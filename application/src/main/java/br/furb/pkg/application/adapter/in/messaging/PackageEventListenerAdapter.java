package br.furb.pkg.application.adapter.in.messaging;

import br.furb.pkg.application.usecase.ProcessRouteCalculatedUseCase;
import br.furb.pkg.application.usecase.ProcessRouteFailedUseCase;
import br.furb.pkg.infrastructure.config.TraceContextSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PackageEventListenerAdapter {

    private final ProcessRouteCalculatedUseCase processRouteCalculatedUseCase;
    private final ProcessRouteFailedUseCase processRouteFailedUseCase;
    private final ObjectMapper objectMapper;
    private final TraceContextSupport traceContextSupport;

    @Value("${app.messaging.inbound-queue:logistics-events-queue.fifo}")
    private String inboundQueue;

    @SqsListener("${app.messaging.inbound-queue:logistics-events-queue.fifo}")
    public void onMessage(Message<String> message) {
        try (TraceContextSupport.ScopedSpan span = traceContextSupport.startConsumerSpan("sqs.receive", message)) {
            span.tag("messaging.system", "sqs");
            span.tag("messaging.operation", "receive");
            span.tag("messaging.destination.name", inboundQueue);
            processMessage(message.getPayload(), span);
        }
    }

    private void processMessage(String messageBody, TraceContextSupport.ScopedSpan span) {
        String eventId = null;
        String eventType = null;
        String packageId = null;
        try {
            JsonNode root = objectMapper.readTree(messageBody);
            eventId = requireText(root, "eventId");
            eventType = requireText(root, "eventType");
            span.tag("event.id", eventId);
            span.tag("event.type", eventType);
            MDC.put("eventId", eventId);
            MDC.put("eventType", eventType);

            if ("route.calculated".equals(eventType) || "route.recalculated".equals(eventType)) {
                JsonNode payload = requireNode(root, "payload");
                packageId = requireText(payload, "packageId");
                span.tag("package.id", packageId);
                MDC.put("packageId", packageId);

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
            } else if ("route.failed".equals(eventType)) {
                JsonNode payload = requireNode(root, "payload");
                packageId = requireText(payload, "packageId");
                span.tag("package.id", packageId);
                MDC.put("packageId", packageId);
                JsonNode reasonNode = payload.get("reason");
                String reason = reasonNode == null || reasonNode.isNull() ? null : reasonNode.asText();

                log.info("[sqs-listener] Received {} from {}", eventType, inboundQueue);
                processRouteFailedUseCase.execute(eventId, packageId, reason);
            } else {
                log.warn("[sqs-listener] Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            span.error(e);
            log.error("[sqs-listener] Error processing message from {}", inboundQueue, e);
            throw new RuntimeException("Failed to process SQS message", e);
        } finally {
            MDC.remove("eventId");
            MDC.remove("eventType");
            MDC.remove("packageId");
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
