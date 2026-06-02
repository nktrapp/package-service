package br.furb.pkg.infrastructure.adapter.out.persistence.mapper;

import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.model.RouteInfo;
import br.furb.pkg.infrastructure.adapter.out.persistence.document.PackageDocument;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class PackageDocumentMapper {

    private PackageDocumentMapper() {}

    public static PackageDocument toDocument(Package pkg) {
        PackageDocument.PackageDocumentBuilder builder = PackageDocument.builder()
                .id(pkg.getId())
                .senderCep(pkg.getSenderCep())
                .recipientCep(pkg.getRecipientCep())
                .weight(pkg.getWeight())
                .status(pkg.getStatus().name())
                .description(pkg.getDescription())
                .createdAt(pkg.getCreatedAt())
                .updatedAt(pkg.getUpdatedAt());

        if (nonNull(pkg.getRouteInfo())) {
            builder.routeInfo(PackageDocument.RouteInfoEmbedded.builder()
                    .hubs(pkg.getRouteInfo().getHubs())
                    .estimatedDelivery(pkg.getRouteInfo().getEstimatedDelivery())
                    .distanceKm(pkg.getRouteInfo().getDistanceKm())
                    .build());
        }

        return builder.build();
    }

    public static Package toDomain(PackageDocument doc) {
        Package.PackageBuilder builder = Package.builder()
                .id(doc.getId())
                .senderCep(doc.getSenderCep())
                .recipientCep(doc.getRecipientCep())
                .weight(doc.getWeight())
                .status(PackageStatus.valueOf(doc.getStatus()))
                .description(doc.getDescription())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt());

        if (nonNull(doc.getRouteInfo())) {
            builder.routeInfo(RouteInfo.builder()
                    .hubs(doc.getRouteInfo().getHubs())
                    .estimatedDelivery(doc.getRouteInfo().getEstimatedDelivery())
                    .distanceKm(doc.getRouteInfo().getDistanceKm())
                    .build());
        }

        return builder.build();
    }
}
