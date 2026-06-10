package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.ChangeDestinationCommand;
import br.furb.pkg.application.dto.CreatePackageCommand;
import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.mapper.PackageMapper;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.InboxRepositoryPort;
import br.furb.pkg.domain.port.OutboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.MongoInboxRepositoryAdapter;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = StaleRouteConvergenceIntegrationTest.TestConfig.class)
@DisplayName("Stale route convergence on a MongoDB replica set")
class StaleRouteConvergenceIntegrationTest {

    private static final String CEP_A = "01310100";
    private static final String CEP_B = "80010000";

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
    ProcessRouteCalculatedUseCase processRouteCalculatedUseCase;
    @Autowired
    PackageRepositoryPort packageRepository;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.getCollectionNames().forEach(collection -> mongoTemplate.remove(new Query(), collection));
    }

    @Test
    @DisplayName("skips a stale route event, applies the fresh one and deduplicates the stale redelivery")
    void staleRouteEventDoesNotOverrideNewDestination() {
        PackageResponse created = createPackageUseCase.execute(
                new CreatePackageCommand("89010000", CEP_A, BigDecimal.TEN, "integration test"));
        String packageId = created.id();
        assertThat(created.status()).isEqualTo(PackageStatus.CREATED);

        PackageResponse redirected = changePackageDestinationUseCase.execute(
                packageId, new ChangeDestinationCommand(CEP_B));
        assertThat(redirected.status()).isEqualTo(PackageStatus.ROUTE_PENDING);

        assertThatCode(() -> processRouteCalculatedUseCase.execute(
                "evt-stale", packageId, CEP_A, List.of("Hub X"), 100.0, 5))
                .doesNotThrowAnyException();

        Package afterStale = packageRepository.findById(packageId).orElseThrow();
        assertThat(afterStale.getStatus()).isEqualTo(PackageStatus.ROUTE_PENDING);
        assertThat(afterStale.getRouteInfo()).isNull();

        processRouteCalculatedUseCase.execute("evt-fresh", packageId, CEP_B, List.of("Hub Y"), 200.0, 8);

        Package afterFresh = packageRepository.findById(packageId).orElseThrow();
        assertThat(afterFresh.getStatus()).isEqualTo(PackageStatus.ROUTE_CALCULATED);
        assertThat(afterFresh.getRouteInfo()).isNotNull();
        assertThat(afterFresh.getRouteInfo().getHubs()).containsExactly("Hub Y");

        assertThatCode(() -> processRouteCalculatedUseCase.execute(
                "evt-stale", packageId, CEP_A, List.of("Hub X"), 100.0, 5))
                .doesNotThrowAnyException();

        Package afterRedelivery = packageRepository.findById(packageId).orElseThrow();
        assertThat(afterRedelivery.getStatus()).isEqualTo(PackageStatus.ROUTE_CALCULATED);
        assertThat(afterRedelivery.getRouteInfo().getHubs()).containsExactly("Hub Y");
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
    @EnableMongoRepositories(basePackageClasses = PackageMongoRepository.class)
    @EnableTransactionManagement
    @Import({MongoPackageRepositoryAdapter.class, MongoInboxRepositoryAdapter.class, MongoOutboxRepositoryAdapter.class})
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

        @Bean
        ProcessRouteCalculatedUseCase processRouteCalculatedUseCase(PackageRepositoryPort packageRepository,
                                                                    InboxRepositoryPort inboxRepository) {
            return new ProcessRouteCalculatedUseCase(packageRepository, inboxRepository);
        }
    }
}
