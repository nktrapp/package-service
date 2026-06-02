package br.furb.pkg.domain.model;

import java.util.Set;

public enum PackageStatus {
    CREATED,
    ROUTE_PENDING,
    ROUTE_CALCULATED,
    IN_TRANSIT,
    DELIVERED,
    FAILED;

    public boolean isValidTransition(PackageStatus target) {
        return getAllowedTransitions().contains(target);
    }

    public Set<PackageStatus> getAllowedTransitions() {
        return switch (this) {
            case CREATED -> Set.of(ROUTE_PENDING, ROUTE_CALCULATED, FAILED);
            // ROUTE_PENDING allows a repeated destination change (self-loop) while re-routing is in flight.
            case ROUTE_PENDING -> Set.of(ROUTE_PENDING, ROUTE_CALCULATED, FAILED);
            // A destination change after a route was calculated reopens routing (-> ROUTE_PENDING).
            case ROUTE_CALCULATED -> Set.of(ROUTE_PENDING, IN_TRANSIT, FAILED);
            case IN_TRANSIT -> Set.of(DELIVERED, FAILED);
            case DELIVERED, FAILED -> Set.of();
        };
    }
}
