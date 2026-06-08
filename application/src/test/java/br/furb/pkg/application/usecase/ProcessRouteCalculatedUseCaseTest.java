package br.furb.pkg.application.usecase;

import br.furb.pkg.domain.exception.PackageNotFoundException;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.InboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessRouteCalculatedUseCase")
class ProcessRouteCalculatedUseCaseTest {

    @Mock
    PackageRepositoryPort packageRepository;

    @Mock
    InboxRepositoryPort inboxRepository;

    @Test
    @DisplayName("Given a new event, should attach the route info and mark the package as ROUTE_CALCULATED")
    void shouldAttachRouteInfo() {
        ProcessRouteCalculatedUseCase useCase = new ProcessRouteCalculatedUseCase(packageRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "route.calculated")).thenReturn(true);
        when(packageRepository.findById("pkg-1")).thenReturn(Optional.of(buildPackage(PackageStatus.ROUTE_PENDING)));

        useCase.execute("event-1", "pkg-1", List.of("Hub A", "Hub B"), 42.5, 6);

        ArgumentCaptor<Package> packageCaptor = ArgumentCaptor.forClass(Package.class);
        verify(packageRepository).save(packageCaptor.capture());
        Package persisted = packageCaptor.getValue();
        assertThat(persisted.getStatus()).isEqualTo(PackageStatus.ROUTE_CALCULATED);
        assertThat(persisted.getRouteInfo()).isNotNull();
        assertThat(persisted.getRouteInfo().getHubs()).containsExactly("Hub A", "Hub B");
        assertThat(persisted.getRouteInfo().getDistanceKm()).isEqualTo(42.5);
        assertThat(persisted.getRouteInfo().getEstimatedDelivery()).isNotNull();
    }

    @Test
    @DisplayName("Given an already processed event, should skip without touching the package")
    void shouldSkipWhenEventAlreadyProcessed() {
        ProcessRouteCalculatedUseCase useCase = new ProcessRouteCalculatedUseCase(packageRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "route.calculated")).thenReturn(false);

        useCase.execute("event-1", "pkg-1", List.of("Hub A"), 10.0, 1);

        verify(packageRepository, never()).findById(any());
        verify(packageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given an unknown package, should throw PackageNotFoundException")
    void shouldThrowWhenPackageNotFound() {
        ProcessRouteCalculatedUseCase useCase = new ProcessRouteCalculatedUseCase(packageRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "route.calculated")).thenReturn(true);
        when(packageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("event-1", "missing", List.of("Hub A"), 10.0, 1))
                .isInstanceOf(PackageNotFoundException.class);

        verify(packageRepository, never()).save(any());
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
