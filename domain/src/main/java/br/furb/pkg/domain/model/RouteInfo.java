package br.furb.pkg.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class RouteInfo {

    private final List<String> hubs;
    private final Instant estimatedDelivery;
    private final double distanceKm;
}
