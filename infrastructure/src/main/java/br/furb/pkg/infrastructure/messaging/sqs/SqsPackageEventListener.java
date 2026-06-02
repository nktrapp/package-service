package br.furb.pkg.infrastructure.messaging.sqs;

import br.furb.pkg.core.usecase.ProcessRouteCalculatedUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsPackageEventListener {

    private final ProcessRouteCalculatedUseCase processRouteCalculatedUseCase;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.inbound-queue:logistics-events-queue}")
    private String inboundQueue;

    @SqsListener("${app.messaging.inbound-queue:logistics-events-queue}")
    public void onMessage(String message) {
        try {
            log.info("[sqs-listener] Received message from {}", inboundQueue);

            JsonNode root = objectMapper.readTree(message);
            String eventId = root.get("eventId").asText();
            String eventType = root.get("eventType").asText();

            if ("route.calculated".equals(eventType) || "route.recalculated".equals(eventType)) {
                JsonNode payload = root.get("payload");
                String packageId = payload.get("packageId").asText();
                double totalDistanceKm = payload.get("totalDistanceKm").asDouble();
                int estimatedTransitHours = payload.get("estimatedTransitHours").asInt();

                JsonNode hopsNode = payload.get("hops");
                List<String> hubs = new java.util.ArrayList<>();
                for (JsonNode hop : hopsNode) {
                    hubs.add(hop.get("name").asText());
                }

                processRouteCalculatedUseCase.execute(eventId, packageId, hubs, totalDistanceKm, estimatedTransitHours);
            } else {
                log.warn("[sqs-listener] Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("[sqs-listener] Error processing message from {}", inboundQueue, e);
            throw new RuntimeException("Failed to process SQS message", e);
        }
    }
}
