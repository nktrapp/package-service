package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.ChangeDestinationCommand;
import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.domain.event.DomainEvent;
import br.furb.pkg.domain.event.PackageDestinationChangedEvent;
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
@DisplayName("ChangePackageDestinationUseCase")
class ChangePackageDestinationUseCaseTest {

    @Mock
    PackageRepositoryPort packageRepository;

    @Mock
    OutboxRepositoryPort outboxRepository;

    @Test
    @DisplayName("Given an existing package, should change the recipient CEP, reopen routing and emit package.destination.changed")
    void shouldChangeDestinationAndEmitEvent() {
        ChangePackageDestinationUseCase useCase = new ChangePackageDestinationUseCase(packageRepository, outboxRepository);
        Package existing = buildPackage("pkg-1", "89010000", PackageStatus.ROUTE_CALCULATED);
        when(packageRepository.findById("pkg-1")).thenReturn(Optional.of(existing));
        when(packageRepository.save(any(Package.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PackageResponse response = useCase.execute("pkg-1", new ChangeDestinationCommand("89200000"));

        ArgumentCaptor<Package> packageCaptor = ArgumentCaptor.forClass(Package.class);
        verify(packageRepository).save(packageCaptor.capture());
        Package persisted = packageCaptor.getValue();
        assertThat(persisted.getRecipientCep()).isEqualTo("89200000");
        assertThat(persisted.getStatus()).isEqualTo(PackageStatus.ROUTE_PENDING);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PackageDestinationChangedEvent.class);
        PackageDestinationChangedEvent event = (PackageDestinationChangedEvent) eventCaptor.getValue();
        assertThat(event.getPayload().getPreviousCep()).isEqualTo("89010000");
        assertThat(event.getPayload().getNewCep()).isEqualTo("89200000");
        assertThat(event.getPayload().getPackageId()).isEqualTo("pkg-1");

        assertThat(response.recipientCep()).isEqualTo("89200000");
        assertThat(response.status()).isEqualTo(PackageStatus.ROUTE_PENDING);
    }

    @Test
    @DisplayName("Given an unknown package, should throw PackageNotFoundException and not emit any event")
    void shouldThrowWhenPackageNotFound() {
        ChangePackageDestinationUseCase useCase = new ChangePackageDestinationUseCase(packageRepository, outboxRepository);
        when(packageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing", new ChangeDestinationCommand("89200000")))
                .isInstanceOf(PackageNotFoundException.class);

        verify(packageRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    private Package buildPackage(String id, String recipientCep, PackageStatus status) {
        return Package.builder()
                .id(id)
                .senderCep("89000000")
                .recipientCep(recipientCep)
                .weight(BigDecimal.TEN)
                .status(status)
                .description("Package")
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }
}
