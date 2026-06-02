package br.furb.pkg.infrastructure.messaging.sqs;

import br.furb.pkg.core.usecase.ProcessRouteCalculatedUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsPackageEventListener {

    private final ProcessRouteCalculatedUseCase processRouteCalculatedUseCase;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.inbound-queue:logistics-events-queue.fifo}")
    private String inboundQueue;

    @SqsListener("${app.messaging.inbound-queue:logistics-events-queue.fifo}")
    public void onMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = requireText(root, "eventId");
            String eventType = requireText(root, "eventType");

            if ("route.calculated".equals(eventType) || "route.recalculated".equals(eventType)) {
                JsonNode payload = requireNode(root, "payload");
                String packageId = requireText(payload, "packageId");

                MDC.put("eventId", eventId);
                MDC.put("packageId", packageId);
                try {
                    log.info("[sqs-listener] Received {} from {}", eventType, inboundQueue);

                    double totalDistanceKm = payload.get("totalDistanceKm").asDouble();
                    int estimatedTransitHours = payload.get("estimatedTransitHours").asInt();

                    JsonNode hopsNode = payload.get("hops");
                    List<String> hubs = new ArrayList<>();
                    if (hopsNode != null) {
                        for (JsonNode hop : hopsNode) {
                            hubs.add(hop.get("name").asText());
                        }
                    }

                    processRouteCalculatedUseCase.execute(eventId, packageId, hubs, totalDistanceKm, estimatedTransitHours);
                } finally {
                    MDC.remove("eventId");
                    MDC.remove("packageId");
                }
            } else {
                log.warn("[sqs-listener] Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("[sqs-listener] Error processing message from {}", inboundQueue, e);
            throw new RuntimeException("Failed to process SQS message", e);
        }
    }

    private static String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing required event field: " + field);
        }
        return node.asText();
    }

    private static JsonNode requireNode(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing required event field: " + field);
        }
        return node;
    }
}
