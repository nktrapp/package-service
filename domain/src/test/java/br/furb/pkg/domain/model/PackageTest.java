package br.furb.pkg.domain.model;

import br.furb.pkg.domain.exception.InvalidPackageStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackageTest {

    @Test
    @DisplayName("Given a created package, should update status to route calculated when route info is added")
    void shouldUpdateStatusToRouteCalculatedWhenRouteInfoIsAdded() {
        // GIVEN
        Package pkg = buildPackage(PackageStatus.CREATED);
        RouteInfo routeInfo = RouteInfo.builder()
                .hubs(List.of("Hub A", "Hub B"))
                .distanceKm(12.5)
                .estimatedDelivery(Instant.parse("2026-05-31T10:15:30Z"))
                .build();

        // WHEN
        Package updatedPackage = pkg.withRouteInfo(routeInfo);

        // THEN
        assertThat(updatedPackage.getStatus()).isEqualTo(PackageStatus.ROUTE_CALCULATED);
        assertThat(updatedPackage.getRouteInfo()).isEqualTo(routeInfo);
        assertThat(updatedPackage.getUpdatedAt()).isNotNull();
        assertThat(updatedPackage.getCreatedAt()).isEqualTo(pkg.getCreatedAt());
    }

    @Test
    @DisplayName("Given a delivered package, should throw exception when transitioning to in transit")
    void shouldThrowExceptionWhenTransitionIsInvalid() {
        // GIVEN
        Package pkg = buildPackage(PackageStatus.DELIVERED);

        // WHEN / THEN
        assertThatThrownBy(() -> pkg.withStatus(PackageStatus.IN_TRANSIT))
                .isInstanceOf(InvalidPackageStateException.class)
                .hasMessageContaining("Cannot transition from DELIVERED to IN_TRANSIT");
    }

    private Package buildPackage(PackageStatus status) {
        return Package.builder()
                .id("pkg-1")
                .senderCep("89000000")
                .recipientCep("89010000")
                .weight(BigDecimal.TEN)
                .status(status)
                .description("Package test")
                .createdAt(Instant.parse("2026-05-31T09:00:00Z"))
                .build();
    }
}package br.furb.pkg.domain.model;

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