package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.CreatePackageCommand;
import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.domain.event.DomainEvent;
import br.furb.pkg.domain.event.PackageCreatedEvent;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.OutboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreatePackageUseCase")
class CreatePackageUseCaseTest {

    @Mock
    PackageRepositoryPort packageRepository;

    @Mock
    OutboxRepositoryPort outboxRepository;

    @Test
    @DisplayName("Given a valid command, should persist the package as CREATED and emit package.created to the outbox")
    void shouldPersistPackageAndEmitEvent() {
        CreatePackageUseCase useCase = new CreatePackageUseCase(packageRepository, outboxRepository);
        CreatePackageCommand command = new CreatePackageCommand("89010000", "89200000", new BigDecimal("2.5"), "Box");

        Package saved = Package.builder()
                .id("pkg-1")
                .senderCep("89010000")
                .recipientCep("89200000")
                .weight(new BigDecimal("2.5"))
                .status(PackageStatus.CREATED)
                .description("Box")
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
        when(packageRepository.save(any(Package.class))).thenReturn(saved);

        PackageResponse response = useCase.execute(command);

        ArgumentCaptor<Package> packageCaptor = ArgumentCaptor.forClass(Package.class);
        verify(packageRepository).save(packageCaptor.capture());
        Package persisted = packageCaptor.getValue();
        assertThat(persisted.getStatus()).isEqualTo(PackageStatus.CREATED);
        assertThat(persisted.getSenderCep()).isEqualTo("89010000");
        assertThat(persisted.getRecipientCep()).isEqualTo("89200000");
        assertThat(persisted.getWeight()).isEqualByComparingTo("2.5");
        assertThat(persisted.getCreatedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PackageCreatedEvent.class);
        PackageCreatedEvent event = (PackageCreatedEvent) eventCaptor.getValue();
        assertThat(event.getPayload().getPackageId()).isEqualTo("pkg-1");
        assertThat(event.getPayload().getRecipientCep()).isEqualTo("89200000");
        assertThat(event.getPartitionKey()).isEqualTo("pkg-1");

        assertThat(response.id()).isEqualTo("pkg-1");
        assertThat(response.status()).isEqualTo(PackageStatus.CREATED);
    }
}
