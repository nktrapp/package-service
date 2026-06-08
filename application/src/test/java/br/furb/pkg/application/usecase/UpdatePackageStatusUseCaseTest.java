package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.dto.UpdateStatusCommand;
import br.furb.pkg.domain.event.DomainEvent;
import br.furb.pkg.domain.event.PackageStatusUpdatedEvent;
import br.furb.pkg.domain.exception.InvalidPackageStateException;
import br.furb.pkg.domain.exception.PackageNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdatePackageStatusUseCase")
class UpdatePackageStatusUseCaseTest {

    @Mock
    PackageRepositoryPort packageRepository;

    @Mock
    OutboxRepositoryPort outboxRepository;

    @Test
    @DisplayName("Given a valid transition, should persist the new status and emit package.status.updated")
    void shouldUpdateStatusAndEmitEvent() {
        UpdatePackageStatusUseCase useCase = new UpdatePackageStatusUseCase(packageRepository, outboxRepository);
        Package existing = buildPackage(PackageStatus.ROUTE_CALCULATED);
        when(packageRepository.findById("pkg-1")).thenReturn(Optional.of(existing));
        when(packageRepository.save(any(Package.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PackageResponse response = useCase.execute(new UpdateStatusCommand("pkg-1", PackageStatus.IN_TRANSIT));

        ArgumentCaptor<Package> packageCaptor = ArgumentCaptor.forClass(Package.class);
        verify(packageRepository).save(packageCaptor.capture());
        assertThat(packageCaptor.getValue().getStatus()).isEqualTo(PackageStatus.IN_TRANSIT);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PackageStatusUpdatedEvent.class);
        PackageStatusUpdatedEvent event = (PackageStatusUpdatedEvent) eventCaptor.getValue();
        assertThat(event.getPayload().getPreviousStatus()).isEqualTo(PackageStatus.ROUTE_CALCULATED);
        assertThat(event.getPayload().getNewStatus()).isEqualTo(PackageStatus.IN_TRANSIT);

        assertThat(response.status()).isEqualTo(PackageStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("Given an unknown package, should throw PackageNotFoundException and not emit any event")
    void shouldThrowWhenPackageNotFound() {
        UpdatePackageStatusUseCase useCase = new UpdatePackageStatusUseCase(packageRepository, outboxRepository);
        when(packageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new UpdateStatusCommand("missing", PackageStatus.IN_TRANSIT)))
                .isInstanceOf(PackageNotFoundException.class);

        verify(packageRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given an invalid transition, should throw InvalidPackageStateException and not persist or emit")
    void shouldThrowWhenTransitionInvalid() {
        UpdatePackageStatusUseCase useCase = new UpdatePackageStatusUseCase(packageRepository, outboxRepository);
        Package existing = buildPackage(PackageStatus.CREATED);
        when(packageRepository.findById("pkg-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(new UpdateStatusCommand("pkg-1", PackageStatus.DELIVERED)))
                .isInstanceOf(InvalidPackageStateException.class);

        verify(packageRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    private Package buildPackage(PackageStatus status) {
        return Package.builder()
                .id("pkg-1")
                .senderCep("89000000")
                .recipientCep("89010000")
                .weight(BigDecimal.TEN)
                .status(status)
                .description("Package")
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }
}
