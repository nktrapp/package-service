package br.furb.pkg.core.dto;

import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.model.RouteInfo;

import java.math.BigDecimal;
import java.time.Instant;

public record PackageResponse(
        String id,
        String senderCep,
        String recipientCep,
        BigDecimal weight,
        PackageStatus status,
        String description,
        RouteInfo routeInfo,
        Instant createdAt,
        Instant updatedAt
) {}
