package br.furb.pkg.domain.model;

import br.furb.pkg.domain.exception.InvalidPackageStateException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Package {

    private final String id;

    @NotBlank
    @Pattern(regexp = "\\d{8}")
    private final String senderCep;

    @NotBlank
    @Pattern(regexp = "\\d{8}")
    private final String recipientCep;

    @NotNull
    @Positive
    private final BigDecimal weight;

    @NotNull
    private final PackageStatus status;

    @Size(max = 500)
    private final String description;

    @NotNull
    private final Instant createdAt;

    private final Instant updatedAt;

    @Valid
    private final RouteInfo routeInfo;

    public Package withStatus(PackageStatus newStatus) {
        validateStatusTransition(newStatus);

        return this.toBuilder()
                .status(newStatus)
                .updatedAt(Instant.now())
                .build();
    }

    public Package withRouteInfo(RouteInfo routeInfo) {
        Package updatedPackage = withStatus(PackageStatus.ROUTE_CALCULATED);
        return updatedPackage.toBuilder()
                .routeInfo(routeInfo)
                .build();
    }

    public Package withRecipientCep(String newRecipientCep) {
        validateStatusTransition(PackageStatus.ROUTE_PENDING);

        return this.toBuilder()
                .recipientCep(newRecipientCep)
                .status(PackageStatus.ROUTE_PENDING)
                .updatedAt(Instant.now())
                .build();
    }

    private void validateStatusTransition(PackageStatus newStatus) {
        if (status.isValidTransition(newStatus)) {
            return;
        }

        throw new InvalidPackageStateException(
                "Cannot transition from %s to %s".formatted(status, newStatus)
        );
    }
}
