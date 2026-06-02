package br.furb.pkg.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class PackageCreatedEvent implements DomainEvent {

    private static final String EVENT_TYPE = "package.created";

    @Builder.Default
    private final String eventId = UUID.randomUUID().toString();

    @Builder.Default
    private final Instant occurredAt = Instant.now();

    private final Payload payload;

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @Override
    public String getPartitionKey() {
        return payload.getPackageId();
    }

    @Getter
    @Builder
    public static class Payload {
        private final String packageId;
        private final String senderCep;
        private final String recipientCep;
        private final BigDecimal weight;
        private final String description;
    }
}
