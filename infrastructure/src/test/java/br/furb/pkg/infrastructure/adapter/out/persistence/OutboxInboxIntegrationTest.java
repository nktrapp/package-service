package br.furb.pkg.infrastructure.adapter.out.persistence;

import br.furb.pkg.domain.event.PackageCreatedEvent;
import br.furb.pkg.domain.port.InboxRepositoryPort;
import br.furb.pkg.domain.port.OutboxRepositoryPort;
import br.furb.pkg.domain.port.OutboxRepositoryPort.OutboxEntry;
import br.furb.pkg.infrastructure.adapter.out.persistence.document.OutboxDocument;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.MongoInboxRepositoryAdapter;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.MongoOutboxRepositoryAdapter;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.mongo.OutboxMongoRepository;
import br.furb.pkg.infrastructure.config.TraceContextSupport;
import tools.jackson.databind.ObjectMapper;
import org.bson.Document;
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
import org.springframework.data.mongodb.core.query.Criteria;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertThat(entry.traceparent()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(entry.tracestate()).isEqualTo("rojo=00f067aa0ba902b7");

        outboxRepository.markAsPublished(entry.eventId(), Instant.now());
        assertThat(outboxRepository.countFailed()).isZero();

        long deleted = outboxRepository.deletePublishedBefore(Instant.now().plusSeconds(1));
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("schedules retries while attempts remain and marks the event FAILED once exhausted")
    void outboxRetryExhaustion() {
        PackageCreatedEvent event = sampleEvent();
        outboxRepository.save(event);
        String eventId = event.getEventId();
        Instant nextAttempt = Instant.now().plusSeconds(30);

        assertThat(outboxRepository.markForRetry(eventId, "boom", nextAttempt, 3).retryScheduled()).isTrue();
        assertThat(outboxRepository.markForRetry(eventId, "boom", nextAttempt, 3).retryScheduled()).isTrue();
        OutboxRepositoryPort.RetryOutcome exhausted = outboxRepository.markForRetry(eventId, "boom", nextAttempt, 3);

        assertThat(exhausted.retryScheduled()).isFalse();
        assertThat(exhausted.retryCount()).isEqualTo(3);
        assertThat(outboxRepository.countFailed()).isEqualTo(1);
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

    @Test
    @DisplayName("existsEarlierUnpublished is true while an earlier sibling of the group awaits retry")
    void earlierSiblingPendingRetryBlocksGroup() {
        PackageCreatedEvent firstEvent = sampleEvent();
        PackageCreatedEvent secondEvent = sampleEvent();
        outboxRepository.save(firstEvent);
        outboxRepository.save(secondEvent);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));
        assertThat(claimed).hasSize(2);
        OutboxEntry second = entryFor(claimed, secondEvent.getEventId());

        outboxRepository.markForRetry(firstEvent.getEventId(), "boom", Instant.now().plusSeconds(30), 5);

        assertThat(outboxRepository.existsEarlierUnpublished("pkg-1", second.createdAt(), second.id())).isTrue();
    }

    @Test
    @DisplayName("existsEarlierUnpublished is false once the earlier sibling is published")
    void publishedSiblingUnblocksGroup() {
        PackageCreatedEvent firstEvent = sampleEvent();
        PackageCreatedEvent secondEvent = sampleEvent();
        outboxRepository.save(firstEvent);
        outboxRepository.save(secondEvent);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));
        assertThat(claimed).hasSize(2);
        OutboxEntry second = entryFor(claimed, secondEvent.getEventId());

        outboxRepository.markAsPublished(firstEvent.getEventId(), Instant.now());

        assertThat(outboxRepository.existsEarlierUnpublished("pkg-1", second.createdAt(), second.id())).isFalse();
    }

    @Test
    @DisplayName("a FAILED earlier sibling keeps blocking the group until manual replay")
    void failedSiblingBlocksGroup() {
        PackageCreatedEvent firstEvent = sampleEvent();
        PackageCreatedEvent secondEvent = sampleEvent();
        outboxRepository.save(firstEvent);
        outboxRepository.save(secondEvent);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));
        assertThat(claimed).hasSize(2);
        OutboxEntry second = entryFor(claimed, secondEvent.getEventId());

        OutboxRepositoryPort.RetryOutcome outcome;
        do {
            outcome = outboxRepository.markForRetry(firstEvent.getEventId(), "boom", Instant.now().plusSeconds(30), 3);
        } while (outcome.retryScheduled());
        assertThat(outboxRepository.countFailed()).isEqualTo(1);

        assertThat(outboxRepository.existsEarlierUnpublished("pkg-1", second.createdAt(), second.id())).isTrue();
    }

    @Test
    @DisplayName("a pending head of another group does not block this group")
    void otherGroupHeadDoesNotBlock() {
        PackageCreatedEvent firstGroupEvent = sampleEvent();
        PackageCreatedEvent otherGroupEvent = sampleEvent("pkg-2");
        outboxRepository.save(firstGroupEvent);
        outboxRepository.save(otherGroupEvent);

        Document otherDocument = mongoTemplate.findOne(
                Query.query(Criteria.where("eventId").is(otherGroupEvent.getEventId())), Document.class, "outbox");
        assertThat(otherDocument).isNotNull();
        String otherId = otherDocument.getObjectId("_id").toHexString();
        Instant otherCreatedAt = otherDocument.getDate("createdAt").toInstant();

        assertThat(outboxRepository.existsEarlierUnpublished("pkg-2", otherCreatedAt, otherId)).isFalse();
    }

    @Test
    @DisplayName("releaseClaim returns the entry to PENDING without counting a retry")
    void releaseClaimRestoresPendingWithoutRetry() {
        PackageCreatedEvent event = sampleEvent();
        outboxRepository.save(event);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));
        assertThat(claimed).hasSize(1);

        Instant releaseAt = Instant.now().plusSeconds(5);
        outboxRepository.releaseClaim(event.getEventId(), releaseAt);

        Document rawDocument = mongoTemplate.findOne(
                Query.query(Criteria.where("eventId").is(event.getEventId())), Document.class, "outbox");
        assertThat(rawDocument).isNotNull();
        assertThat(rawDocument.getString("status")).isEqualTo("PENDING");
        assertThat(rawDocument.getInteger("retryCount")).isEqualTo(0);
        assertThat(rawDocument.containsKey("processingStartedAt")).isFalse();
        assertThat(rawDocument.getDate("nextAttemptAt").toInstant()).isEqualTo(releaseAt.truncatedTo(ChronoUnit.MILLIS));

        assertThat(outboxRepository.claimPending(10, Instant.now(), Instant.now().minusSeconds(60))).isEmpty();
    }

    @Test
    @DisplayName("releaseClaim is a no-op on a PUBLISHED entry")
    void releaseClaimIgnoresPublishedEntry() {
        PackageCreatedEvent event = sampleEvent();
        outboxRepository.save(event);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));
        assertThat(claimed).hasSize(1);
        outboxRepository.markAsPublished(event.getEventId(), Instant.now());

        outboxRepository.releaseClaim(event.getEventId(), Instant.now().plusSeconds(5));

        Document rawDocument = mongoTemplate.findOne(
                Query.query(Criteria.where("eventId").is(event.getEventId())), Document.class, "outbox");
        assertThat(rawDocument).isNotNull();
        assertThat(rawDocument.getString("status")).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("identical createdAt falls back to the _id tie-break within the group")
    void identicalCreatedAtUsesIdTieBreak() {
        Instant sharedCreatedAt = Instant.parse("2026-06-01T10:00:00Z");
        OutboxDocument firstDocument = outboxDocument("evt-tie-1", "pkg-tie", sharedCreatedAt);
        OutboxDocument secondDocument = outboxDocument("evt-tie-2", "pkg-tie", sharedCreatedAt);
        mongoTemplate.insert(firstDocument);
        mongoTemplate.insert(secondDocument);

        String smallerId = firstDocument.getId().compareTo(secondDocument.getId()) < 0
                ? firstDocument.getId() : secondDocument.getId();
        String largerId = firstDocument.getId().compareTo(secondDocument.getId()) < 0
                ? secondDocument.getId() : firstDocument.getId();

        assertThat(outboxRepository.existsEarlierUnpublished("pkg-tie", sharedCreatedAt, largerId)).isTrue();
        assertThat(outboxRepository.existsEarlierUnpublished("pkg-tie", sharedCreatedAt, smallerId)).isFalse();
    }

    @Test
    @DisplayName("two concurrent relay workers publish a group's events in creation order")
    void concurrentWorkersPreservePerGroupOrder() throws InterruptedException {
        PackageCreatedEvent firstEvent = sampleEvent();
        PackageCreatedEvent secondEvent = sampleEvent();
        outboxRepository.save(firstEvent);
        outboxRepository.save(secondEvent);

        List<String> publishedOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        Instant deadline = Instant.now().plusSeconds(10);

        Runnable worker = () -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            while (publishedOrder.size() < 2 && Instant.now().isBefore(deadline)) {
                Instant claimedAt = Instant.now();
                List<OutboxEntry> claimed = outboxRepository.claimPending(1, claimedAt, claimedAt.minusSeconds(60));
                if (claimed.isEmpty()) {
                    continue;
                }
                OutboxEntry entry = claimed.getFirst();
                if (outboxRepository.existsEarlierUnpublished(entry.groupId(), entry.createdAt(), entry.id())) {
                    outboxRepository.releaseClaim(entry.eventId(), Instant.now());
                } else {
                    publishedOrder.add(entry.eventId());
                    outboxRepository.markAsPublished(entry.eventId(), Instant.now());
                }
            }
        };

        Thread firstWorker = new Thread(worker);
        Thread secondWorker = new Thread(worker);
        firstWorker.start();
        secondWorker.start();
        startLatch.countDown();
        firstWorker.join(15000);
        secondWorker.join(15000);

        assertThat(publishedOrder).containsExactly(firstEvent.getEventId(), secondEvent.getEventId());
    }

    private OutboxEntry entryFor(List<OutboxEntry> entries, String eventId) {
        return entries.stream().filter(entry -> entry.eventId().equals(eventId)).findFirst().orElseThrow();
    }

    private OutboxDocument outboxDocument(String eventId, String groupId, Instant createdAt) {
        return OutboxDocument.builder()
                .eventId(eventId)
                .eventType("package.created")
                .payload("{}")
                .groupId(groupId)
                .status("PENDING")
                .nextAttemptAt(createdAt)
                .retryCount(0)
                .createdAt(createdAt)
                .build();
    }

    private PackageCreatedEvent sampleEvent() {
        return sampleEvent("pkg-1");
    }

    private PackageCreatedEvent sampleEvent(String packageId) {
        return PackageCreatedEvent.builder()
                .payload(PackageCreatedEvent.Payload.builder()
                        .packageId(packageId)
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
        TraceContextSupport traceContextSupport() {
            TraceContextSupport traceContextSupport = mock(TraceContextSupport.class);
            when(traceContextSupport.captureCurrent()).thenReturn(new TraceContextSupport.TraceCarrier(
                    "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                    "rojo=00f067aa0ba902b7"
            ));
            return traceContextSupport;
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
