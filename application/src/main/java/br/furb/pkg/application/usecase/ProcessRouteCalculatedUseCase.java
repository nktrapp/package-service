package br.furb.pkg.application.usecase;

import br.furb.pkg.domain.exception.PackageNotFoundException;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.model.RouteInfo;
import br.furb.pkg.domain.port.InboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ProcessRouteCalculatedUseCase {

    private final PackageRepositoryPort packageRepository;
    private final InboxRepositoryPort inboxRepository;

    @Transactional
    public void execute(String eventId, String packageId, String destinationCep, List<String> hubs, double distanceKm, int transitHours) {
        if (!inboxRepository.saveIfAbsent(eventId, "route.calculated")) {
            log.info("[process-route] Event {} already processed, skipping", eventId);
            return;
        }

        log.info("[process-route] Processing route.calculated for package {}", packageId);

        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException(packageId));

        if (destinationCep != null && !destinationCep.equals(pkg.getRecipientCep())) {
            log.warn("[process-route] Stale route for package {}: route destination {} != current recipient {}, skipping",
                    packageId, destinationCep, pkg.getRecipientCep());
            return;
        }

        if (!pkg.getStatus().isValidTransition(PackageStatus.ROUTE_CALCULATED)) {
            log.warn("[process-route] Package {} in status {} cannot accept route, skipping", packageId, pkg.getStatus());
            return;
        }

        RouteInfo routeInfo = RouteInfo.builder()
                .hubs(hubs)
                .distanceKm(distanceKm)
                .estimatedDelivery(Instant.now().plusSeconds((long) transitHours * 3600))
                .build();

        Package updated = pkg.withRouteInfo(routeInfo);
        packageRepository.save(updated);

        log.info("[process-route] Package {} updated with route info", packageId);
    }
}
