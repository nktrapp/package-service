package br.furb.pkg.application.usecase;

import br.furb.pkg.domain.exception.PackageNotFoundException;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.InboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class ProcessRouteFailedUseCase {

    private final PackageRepositoryPort packageRepository;
    private final InboxRepositoryPort inboxRepository;

    @Transactional
    public void execute(String eventId, String packageId, String reason) {
        if (!inboxRepository.saveIfAbsent(eventId, "route.failed")) {
            log.info("[process-route-failed] Event {} already processed, skipping", eventId);
            return;
        }

        log.warn("[process-route-failed] Processing route.failed for package {} (reason: {})", packageId, reason);

        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException(packageId));

        if (pkg.getStatus() == PackageStatus.FAILED) {
            log.info("[process-route-failed] Package {} already FAILED, skipping", packageId);
            return;
        }

        if (!pkg.getStatus().isValidTransition(PackageStatus.FAILED)) {
            log.warn("[process-route-failed] Package {} in status {} cannot transition to FAILED, skipping",
                    packageId, pkg.getStatus());
            return;
        }

        Package failed = pkg.withStatus(PackageStatus.FAILED);
        packageRepository.save(failed);

        log.warn("[process-route-failed] Package {} marked as FAILED", packageId);
    }
}
