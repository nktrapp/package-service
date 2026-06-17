package br.furb.pkg.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Comando de troca de destino de um pacote")
public record ChangeDestinationCommand(
        @Schema(description = "Novo CEP de destino (8 dígitos, sem hífen)", example = "20040002")
        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP de destino inválido") String newCep
) {}
