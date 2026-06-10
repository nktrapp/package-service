package br.furb.pkg.application.adapter.in.messaging;

import br.furb.pkg.application.usecase.ProcessRouteCalculatedUseCase;
import br.furb.pkg.application.usecase.ProcessRouteFailedUseCase;
import br.furb.pkg.infrastructure.config.TraceContextSupport;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PackageEventListenerAdapter")
class PackageEventListenerAdapterTest {

    @Mock
    ProcessRouteCalculatedUseCase processRouteCalculatedUseCase;

    @Mock
    ProcessRouteFailedUseCase processRouteFailedUseCase;
    @Mock
    TraceContextSupport traceContextSupport;

    private PackageEventListenerAdapter buildAdapter() {
        lenient().when(traceContextSupport.startConsumerSpan(eq("sqs.receive"), any())).thenReturn(noopSpan());
        return new PackageEventListenerAdapter(
                processRouteCalculatedUseCase,
                processRouteFailedUseCase,
                new ObjectMapper(),
                traceContextSupport
        );
    }

    @Test
    @DisplayName("Given a route.calculated message, should extract hop names and distances and process the route")
    void shouldProcessRouteCalculated() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-1","eventType":"route.calculated",
                 "payload":{"packageId":"pkg-1","totalDistanceKm":42.5,"estimatedTransitHours":6,
                            "hops":[{"name":"Hub A"},{"name":"Hub B"}]}}
                """;

        adapter.onMessage(toMessage(message));

        verify(processRouteCalculatedUseCase).execute("event-1", "pkg-1", null, List.of("Hub A", "Hub B"), 42.5, 6);
        verifyNoInteractions(processRouteFailedUseCase);
    }

    @Test
    @DisplayName("Given a route.recalculated message, should also process it through the calculated route flow")
    void shouldProcessRouteRecalculated() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-2","eventType":"route.recalculated",
                 "payload":{"packageId":"pkg-1","totalDistanceKm":10.0,"estimatedTransitHours":2,
                            "hops":[{"name":"Hub A"}]}}
                """;

        adapter.onMessage(toMessage(message));

        verify(processRouteCalculatedUseCase).execute("event-2", "pkg-1", null, List.of("Hub A"), 10.0, 2);
        verifyNoInteractions(processRouteFailedUseCase);
    }

    @Test
    @DisplayName("Given a route.failed message, should process the failure with its reason")
    void shouldProcessRouteFailed() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-3","eventType":"route.failed",
                 "payload":{"packageId":"pkg-1","reason":"no reachable hub"}}
                """;

        adapter.onMessage(toMessage(message));

        verify(processRouteFailedUseCase).execute("event-3", "pkg-1", "no reachable hub");
        verifyNoInteractions(processRouteCalculatedUseCase);
    }

    @Test
    @DisplayName("Given a route.failed message without a reason, should process the failure with a null reason")
    void shouldProcessRouteFailedWithoutReason() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-4","eventType":"route.failed","payload":{"packageId":"pkg-1"}}
                """;

        adapter.onMessage(toMessage(message));

        verify(processRouteFailedUseCase).execute(eq("event-4"), eq("pkg-1"), eq(null));
        verifyNoInteractions(processRouteCalculatedUseCase);
    }

    @Test
    @DisplayName("Given an unknown event type, should ignore it without invoking any use case")
    void shouldIgnoreUnknownEventType() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-5","eventType":"route.archived","payload":{"packageId":"pkg-1"}}
                """;

        adapter.onMessage(toMessage(message));

        verifyNoInteractions(processRouteCalculatedUseCase, processRouteFailedUseCase);
    }

    @Test
    @DisplayName("Given a message missing a required field, should wrap the failure so the message is retried")
    void shouldThrowWhenRequiredFieldMissing() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventType":"route.calculated","payload":{"packageId":"pkg-1"}}
                """;

        assertThatThrownBy(() -> adapter.onMessage(toMessage(message)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message");

        verifyNoInteractions(processRouteCalculatedUseCase, processRouteFailedUseCase);
    }

    @Test
    @DisplayName("Given a route.calculated message carrying destinationCep, should pass it to the use case")
    void shouldExtractDestinationCep() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-6","eventType":"route.calculated",
                 "payload":{"packageId":"pkg-1","destinationCep":"01310100","totalDistanceKm":42.5,"estimatedTransitHours":6,
                            "hops":[{"name":"Hub A"}]}}
                """;

        adapter.onMessage(toMessage(message));

        verify(processRouteCalculatedUseCase).execute("event-6", "pkg-1", "01310100", List.of("Hub A"), 42.5, 6);
        verifyNoInteractions(processRouteFailedUseCase);
    }

    @Test
    @DisplayName("Given a hop without a name, should fail with a missing-field error instead of an NPE")
    void shouldThrowWhenHopNameMissing() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-7","eventType":"route.calculated",
                 "payload":{"packageId":"pkg-1","totalDistanceKm":42.5,"estimatedTransitHours":6,
                            "hops":[{"city":"Blumenau"}]}}
                """;

        assertThatThrownBy(() -> adapter.onMessage(toMessage(message)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message")
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(processRouteCalculatedUseCase, processRouteFailedUseCase);
    }

    @Test
    @DisplayName("Given a payload without totalDistanceKm, should fail with a missing-field error instead of an NPE")
    void shouldThrowWhenTotalDistanceKmMissing() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-8","eventType":"route.calculated",
                 "payload":{"packageId":"pkg-1","estimatedTransitHours":6,"hops":[{"name":"Hub A"}]}}
                """;

        assertThatThrownBy(() -> adapter.onMessage(toMessage(message)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message")
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalDistanceKm");

        verifyNoInteractions(processRouteCalculatedUseCase, processRouteFailedUseCase);
    }

    private Message<String> toMessage(String payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("Sqs_MA_traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .build();
    }

    private TraceContextSupport.ScopedSpan noopSpan() {
        return new TraceContextSupport.ScopedSpan(mock(Span.class), mock(Tracer.SpanInScope.class));
    }
}
