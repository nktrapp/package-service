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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessRouteFailedUseCase")
class ProcessRouteFailedUseCaseTest {

    @Mock
    PackageRepositoryPort packageRepository;

    @Mock
    InboxRepositoryPort inboxRepository;

    @Test
    @DisplayName("Given a new event and a routable package, should mark the package as FAILED")
    void shouldMarkPackageAsFailed() {
        ProcessRouteFailedUseCase useCase = new ProcessRouteFailedUseCase(packageRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "route.failed")).thenReturn(true);
        when(packageRepository.findById("pkg-1")).thenReturn(Optional.of(buildPackage(PackageStatus.ROUTE_CALCULATED)));

        useCase.execute("event-1", "pkg-1", "no reachable hub");

        ArgumentCaptor<Package> packageCaptor = ArgumentCaptor.forClass(Package.class);
        verify(packageRepository).save(packageCaptor.capture());
        assertThat(packageCaptor.getValue().getStatus()).isEqualTo(PackageStatus.FAILED);
    }

    @Test
    @DisplayName("Given an already processed event, should skip without touching the package")
    void shouldSkipWhenEventAlreadyProcessed() {
        ProcessRouteFailedUseCase useCase = new ProcessRouteFailedUseCase(packageRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "route.failed")).thenReturn(false);

        useCase.execute("event-1", "pkg-1", "reason");

        verify(packageRepository, never()).findById(any());
        verify(packageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given a package already FAILED, should be idempotent and not persist again")
    void shouldSkipWhenAlreadyFailed() {
        ProcessRouteFailedUseCase useCase = new ProcessRouteFailedUseCase(packageRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "route.failed")).thenReturn(true);
        when(packageRepository.findById("pkg-1")).thenReturn(Optional.of(buildPackage(PackageStatus.FAILED)));

        useCase.execute("event-1", "pkg-1", "reason");

        verify(packageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given a package in a terminal non-failed status, should not force an invalid transition to FAILED")
    void shouldSkipWhenTransitionToFailedIsInvalid() {
        ProcessRouteFailedUseCase useCase = new ProcessRouteFailedUseCase(packageRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "route.failed")).thenReturn(true);
        when(packageRepository.findById("pkg-1")).thenReturn(Optional.of(buildPackage(PackageStatus.DELIVERED)));

        useCase.execute("event-1", "pkg-1", "reason");

        verify(packageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given an unknown package, should throw PackageNotFoundException")
    void shouldThrowWhenPackageNotFound() {
        ProcessRouteFailedUseCase useCase = new ProcessRouteFailedUseCase(packageRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "route.failed")).thenReturn(true);
        when(packageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("event-1", "missing", "reason"))
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
