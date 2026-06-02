package br.furb.pkg.core.dto;

import br.furb.pkg.domain.model.PackageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusCommand(
        @NotBlank String packageId,
        @NotNull PackageStatus newStatus
) {}
