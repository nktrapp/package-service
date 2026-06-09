package br.furb.pkg.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@Document("packages")
public class PackageDocument {

    @Id
    private String id;
    private String senderCep;
    private String recipientCep;
    private BigDecimal weight;
    private String status;
    private String description;
    private RouteInfoEmbedded routeInfo;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    public static class RouteInfoEmbedded {
        private List<String> hubs;
        private Instant estimatedDelivery;
        private double distanceKm;
    }
}
