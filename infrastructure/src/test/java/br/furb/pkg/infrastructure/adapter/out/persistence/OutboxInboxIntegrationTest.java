package br.furb.pkg.infrastructure.adapter.out.persistence;

import br.furb.pkg.domain.event.PackageCreatedEvent;
import br.furb.pkg.domain.port.InboxRepositoryPort;
import br.furb.pkg.domain.port.OutboxRepositoryPort;
import br.furb.pkg.domain.port.OutboxRepositoryPort.OutboxEntry;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.MongoInboxRepositoryAdapter;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.MongoOutboxRepositoryAdapter;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.mongo.OutboxMongoRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = OutboxInboxIntegrationTest.TestConfig.class)
@DisplayName("Outbox/Inbox on a MongoDB replica set")
class OutboxInboxIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
    }

    @Autowired
    OutboxRepositoryPort outboxRepository;
    @Autowired
    InboxRepositoryPort inboxRepository;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.getCollectionNames().forEach(collection -> mongoTemplate.remove(new Query(), collection));
    }

    @Test
    @DisplayName("persists an event, claims it, publishes it, then purges it")
    void outboxRoundTrip() {
        PackageCreatedEvent event = sampleEvent();
        outboxRepository.save(event);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));

        assertThat(claimed).hasSize(1);
        OutboxEntry entry = claimed.getFirst();
        assertThat(entry.eventId()).isEqualTo(event.getEventId());
        assertThat(entry.eventType()).isEqualTo("package.created");
        assertThat(entry.groupId()).isEqualTo("pkg-1");

        outboxRepository.markAsPublished(entry.eventId(), Instant.now());
        assertThat(outboxRepository.countFailed()).isZero();

        long deleted = outboxRepository.deletePublishedBefore(Instant.now().plusSeconds(1));
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("inbox saveIfAbsent is idempotent on the unique eventId")
    void inboxIdempotency() {
        assertThat(inboxRepository.saveIfAbsent("evt-1", "package.created")).isTrue();
        assertThat(inboxRepository.saveIfAbsent("evt-1", "package.created")).isFalse();
        assertThat(inboxRepository.existsByEventId("evt-1")).isTrue();
    }

    @Test
    @DisplayName("a failed transaction rolls back the inbox claim so the event is reprocessed")
    void rollbackReprocessesEvent() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            inboxRepository.saveIfAbsent("evt-2", "package.created");
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(inboxRepository.existsByEventId("evt-2")).isFalse();

        transactionTemplate.executeWithoutResult(status ->
                assertThat(inboxRepository.saveIfAbsent("evt-2", "package.created")).isTrue());
        assertThat(inboxRepository.existsByEventId("evt-2")).isTrue();
    }

    private PackageCreatedEvent sampleEvent() {
        return PackageCreatedEvent.builder()
                .payload(PackageCreatedEvent.Payload.builder()
                        .packageId("pkg-1")
                        .senderCep("89000000")
                        .recipientCep("89010000")
                        .weight(BigDecimal.TEN)
                        .description("integration test")
                        .build())
                .build();
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
    @EnableMongoRepositories(basePackageClasses = OutboxMongoRepository.class)
    @Import({MongoOutboxRepositoryAdapter.class, MongoInboxRepositoryAdapter.class})
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
            return new MongoTransactionManager(databaseFactory);
        }

        @Bean
        TransactionTemplate transactionTemplate(MongoTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
