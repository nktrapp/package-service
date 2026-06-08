package br.furb.pkg.infrastructure.adapter.out.persistence;

import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.MongoPackageRepositoryAdapter;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.mongo.PackageMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MongoPackageRepositoryAdapterTest.TestConfig.class)
@DisplayName("MongoPackageRepositoryAdapter")
class MongoPackageRepositoryAdapterTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
    }

    @Autowired
    PackageRepositoryPort packageRepository;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.getCollectionNames().forEach(collection -> mongoTemplate.remove(new Query(), collection));
    }

    @Test
    @DisplayName("saves a package assigning an id and reads it back by id")
    void shouldSaveAndFindById() {
        Package saved = packageRepository.save(buildPackage(PackageStatus.CREATED));

        assertThat(saved.getId()).isNotBlank();

        Optional<Package> found = packageRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(PackageStatus.CREATED);
        assertThat(found.get().getRecipientCep()).isEqualTo("89010000");
    }

    @Test
    @DisplayName("findByStatus returns only packages in the requested status")
    void shouldFindByStatus() {
        packageRepository.save(buildPackage(PackageStatus.CREATED));
        packageRepository.save(buildPackage(PackageStatus.IN_TRANSIT));
        packageRepository.save(buildPackage(PackageStatus.IN_TRANSIT));

        List<Package> inTransit = packageRepository.findByStatus(PackageStatus.IN_TRANSIT);

        assertThat(inTransit).hasSize(2);
        assertThat(packageRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("returns empty for an unknown id")
    void shouldReturnEmptyForUnknownId() {
        assertThat(packageRepository.findById("missing")).isEmpty();
    }

    private Package buildPackage(PackageStatus status) {
        return Package.builder()
                .senderCep("89000000")
                .recipientCep("89010000")
                .weight(BigDecimal.TEN)
                .status(status)
                .description("Package")
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
    @EnableMongoRepositories(basePackageClasses = PackageMongoRepository.class)
    @Import(MongoPackageRepositoryAdapter.class)
    static class TestConfig {
    }
}
