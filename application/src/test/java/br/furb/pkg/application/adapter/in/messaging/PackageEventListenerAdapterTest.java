package br.furb.pkg.application.adapter.in.messaging;

import br.furb.pkg.application.usecase.ProcessRouteCalculatedUseCase;
import br.furb.pkg.application.usecase.ProcessRouteFailedUseCase;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PackageEventListenerAdapter")
class PackageEventListenerAdapterTest {

    @Mock
    ProcessRouteCalculatedUseCase processRouteCalculatedUseCase;

    @Mock
    ProcessRouteFailedUseCase processRouteFailedUseCase;

    private PackageEventListenerAdapter buildAdapter() {
        return new PackageEventListenerAdapter(processRouteCalculatedUseCase, processRouteFailedUseCase, new ObjectMapper());
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

        adapter.onMessage(message);

        verify(processRouteCalculatedUseCase).execute("event-1", "pkg-1", List.of("Hub A", "Hub B"), 42.5, 6);
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

        adapter.onMessage(message);

        verify(processRouteCalculatedUseCase).execute("event-2", "pkg-1", List.of("Hub A"), 10.0, 2);
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

        adapter.onMessage(message);

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

        adapter.onMessage(message);

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

        adapter.onMessage(message);

        verifyNoInteractions(processRouteCalculatedUseCase, processRouteFailedUseCase);
    }

    @Test
    @DisplayName("Given a message missing a required field, should wrap the failure so the message is retried")
    void shouldThrowWhenRequiredFieldMissing() {
        PackageEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventType":"route.calculated","payload":{"packageId":"pkg-1"}}
                """;

        assertThatThrownBy(() -> adapter.onMessage(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message");

        verifyNoInteractions(processRouteCalculatedUseCase, processRouteFailedUseCase);
    }
}
