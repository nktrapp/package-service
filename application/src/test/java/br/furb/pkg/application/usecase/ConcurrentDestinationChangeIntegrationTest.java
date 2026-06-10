package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.ChangeDestinationCommand;
import br.furb.pkg.application.dto.CreatePackageCommand;
import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.mapper.PackageMapper;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.OutboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import br.furb.pkg.infrastructure.adapter.out.persistence.document.OutboxDocument;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.MongoOutboxRepositoryAdapter;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.MongoPackageRepositoryAdapter;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.mongo.PackageMongoRepository;
import br.furb.pkg.infrastructure.config.TraceContextSupport;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ConcurrentDestinationChangeIntegrationTest.TestConfig.class)
@DisplayName("Concurrent destination changes on a MongoDB replica set")
class ConcurrentDestinationChangeIntegrationTest {

    private static final String INITIAL_CEP = "01310100";
    private static final String EVENT_TYPE = "package.destination.changed";
    private static final int ROUNDS = 10;

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
    }

    @Autowired
    CreatePackageUseCase createPackageUseCase;
    @Autowired
    ChangePackageDestinationUseCase changePackageDestinationUseCase;
    @Autowired
    PackageRepositoryPort packageRepository;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clear() {
        mongoTemplate.getCollectionNames().forEach(collection -> mongoTemplate.remove(new Query(), collection));
    }

    record Outcome(String cep, boolean success, Throwable failure) {}

    @Test
    @DisplayName("never loses an update silently and only committed transactions leave outbox events")
    void concurrentDestinationChangesNeverLoseAnUpdateSilently() throws Exception {
        PackageResponse created = createPackageUseCase.execute(
                new CreatePackageCommand("89010000", INITIAL_CEP, BigDecimal.TEN, "concurrency test"));
        String packageId = created.id();
        assertThat(created.status()).isEqualTo(PackageStatus.CREATED);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        int bothSucceededRounds = 0;
        int oneFailedRounds = 0;
        int bothFailedRounds = 0;
        long cumulativeSuccesses = 0;
        String previousFinalCep = INITIAL_CEP;
        Set<String> observedFailureTypes = new TreeSet<>();

        try {
            for (int round = 0; round < ROUNDS; round++) {
                String cepX = String.format("%08d", 10000000 + round * 2);
                String cepY = String.format("%08d", 10000001 + round * 2);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);

                Callable<Outcome> taskX = changeDestinationTask(packageId, cepX, ready, start);
                Callable<Outcome> taskY = changeDestinationTask(packageId, cepY, ready, start);

                Future<Outcome> futureX = executor.submit(taskX);
                Future<Outcome> futureY = executor.submit(taskY);
                assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                List<Outcome> outcomes = List.of(
                        futureX.get(60, TimeUnit.SECONDS),
                        futureY.get(60, TimeUnit.SECONDS));

                Set<String> successCeps = new HashSet<>();
                for (Outcome outcome : outcomes) {
                    if (outcome.success()) {
                        successCeps.add(outcome.cep());
                    } else {
                        String failureMessage = String.valueOf(outcome.failure().getMessage());
                        observedFailureTypes.add(outcome.failure().getClass().getName() + ": "
                                + failureMessage.substring(0, Math.min(200, failureMessage.length())));
                    }
                }

                Package persisted = packageRepository.findById(packageId).orElseThrow();
                String finalCep = persisted.getRecipientCep();

                if (successCeps.isEmpty()) {
                    bothFailedRounds++;
                    assertThat(finalCep)
                            .as("round %d: both failed, persisted CEP must be unchanged", round)
                            .isEqualTo(previousFinalCep);
                } else {
                    if (successCeps.size() == 2) {
                        bothSucceededRounds++;
                    } else {
                        oneFailedRounds++;
                    }
                    assertThat(successCeps)
                            .as("round %d: persisted CEP %s must come from a successful execute", round, finalCep)
                            .contains(finalCep);
                }

                cumulativeSuccesses += successCeps.size();

                List<OutboxDocument> events = mongoTemplate.find(
                        Query.query(Criteria.where("eventType").is(EVENT_TYPE)), OutboxDocument.class);
                assertThat(events)
                        .as("round %d: outbox events must match committed transactions only", round)
                        .hasSize((int) cumulativeSuccesses);

                List<String> eventNewCeps = new ArrayList<>();
                for (OutboxDocument event : events) {
                    Map<?, ?> payload = objectMapper.readValue(event.getPayload(), Map.class);
                    eventNewCeps.add(String.valueOf(payload.get("newCep")));
                }
                if (!finalCep.equals(INITIAL_CEP)) {
                    assertThat(eventNewCeps)
                            .as("round %d: an outbox event for the persisted CEP %s must exist", round, finalCep)
                            .contains(finalCep);
                }

                previousFinalCep = finalCep;
            }
        } finally {
            executor.shutdownNow();
        }

        System.out.printf(
                "[concurrent-destination-change] rounds=%d bothSucceeded=%d oneFailed=%d bothFailed=%d failureTypes=%s%n",
                ROUNDS, bothSucceededRounds, oneFailedRounds, bothFailedRounds, observedFailureTypes);
    }

    private Callable<Outcome> changeDestinationTask(String packageId, String cep,
                                                    CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                changePackageDestinationUseCase.execute(packageId, new ChangeDestinationCommand(cep));
                return new Outcome(cep, true, null);
            } catch (Exception exception) {
                return new Outcome(cep, false, exception);
            }
        };
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
    @EnableMongoRepositories(basePackageClasses = PackageMongoRepository.class)
    @EnableTransactionManagement
    @Import({MongoPackageRepositoryAdapter.class, MongoOutboxRepositoryAdapter.class})
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
        PackageMapper packageMapper() {
            return Mappers.getMapper(PackageMapper.class);
        }

        @Bean
        CreatePackageUseCase createPackageUseCase(PackageRepositoryPort packageRepository,
                                                  OutboxRepositoryPort outboxRepository,
                                                  PackageMapper packageMapper) {
            return new CreatePackageUseCase(packageRepository, outboxRepository, packageMapper);
        }

        @Bean
        ChangePackageDestinationUseCase changePackageDestinationUseCase(PackageRepositoryPort packageRepository,
                                                                        OutboxRepositoryPort outboxRepository,
                                                                        PackageMapper packageMapper) {
            return new ChangePackageDestinationUseCase(packageRepository, outboxRepository, packageMapper);
        }
    }
}
