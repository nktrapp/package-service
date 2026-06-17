package br.furb.pkg.application.dto;

import br.furb.pkg.domain.model.PackageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Comando de atualização de status de um pacote")
public record UpdateStatusCommand(
        @Schema(description = "Identificador do pacote (preenchido a partir da URL)",
                accessMode = Schema.AccessMode.READ_ONLY)
        @NotBlank String packageId,
        @Schema(description = "Novo status desejado")
        @NotNull PackageStatus newStatus
) {}
