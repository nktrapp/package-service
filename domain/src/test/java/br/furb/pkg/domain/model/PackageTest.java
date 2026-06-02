package br.furb.pkg.domain.model;

import br.furb.pkg.domain.exception.InvalidPackageStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Package")
class PackageTest {

    @Nested
    @DisplayName("Status transition")
    class StatusTransition {

        @Test
        @DisplayName("Given an invalid target status, should throw exception when updating package status")
        void shouldThrowExceptionWhenUpdatingToInvalidStatus() {
            // GIVEN
            Package pkg = buildPackage(PackageStatus.CREATED);

            // WHEN / THEN
            assertThatThrownBy(() -> pkg.withStatus(PackageStatus.DELIVERED))
                    .isInstanceOf(InvalidPackageStateException.class)
                    .hasMessage("Cannot transition from CREATED to DELIVERED");
        }

        @Test
        @DisplayName("Given a created package, should mark route as calculated when route info is assigned")
        void shouldMarkRouteAsCalculatedWhenRouteInfoIsAssigned() {
            // GIVEN
            Package pkg = buildPackage(PackageStatus.CREATED);
            RouteInfo routeInfo = RouteInfo.builder()
                    .hubs(List.of("Hub A", "Hub B"))
                    .distanceKm(42.0)
                    .estimatedDelivery(Instant.parse("2026-05-31T15:00:00Z"))
                    .build();

            // WHEN
            Package updatedPackage = pkg.withRouteInfo(routeInfo);

            // THEN
            assertThat(updatedPackage.getStatus()).isEqualTo(PackageStatus.ROUTE_CALCULATED);
            assertThat(updatedPackage.getRouteInfo()).isEqualTo(routeInfo);
            assertThat(updatedPackage.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Destination change")
    class DestinationChange {

        @Test
        @DisplayName("Given a calculated route, should change recipient CEP and reopen routing")
        void shouldChangeRecipientCepAndReopenRouting() {
            // GIVEN
            Package pkg = buildPackage(PackageStatus.ROUTE_CALCULATED);

            // WHEN
            Package updated = pkg.withRecipientCep("89030000");

            // THEN
            assertThat(updated.getRecipientCep()).isEqualTo("89030000");
            assertThat(updated.getStatus()).isEqualTo(PackageStatus.ROUTE_PENDING);
            assertThat(updated.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Given a package already in transit, should reject a destination change")
        void shouldRejectDestinationChangeWhenInTransit() {
            // GIVEN
            Package pkg = buildPackage(PackageStatus.IN_TRANSIT);

            // WHEN / THEN
            assertThatThrownBy(() -> pkg.withRecipientCep("89030000"))
                    .isInstanceOf(InvalidPackageStateException.class)
                    .hasMessage("Cannot transition from IN_TRANSIT to ROUTE_PENDING");
        }
    }

    private Package buildPackage(PackageStatus status) {
        return Package.builder()
                .id("pkg-1")
                .senderCep("89000000")
                .recipientCep("89010000")
                .weight(BigDecimal.TEN)
                .status(status)
                .description("Package")
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }
}
