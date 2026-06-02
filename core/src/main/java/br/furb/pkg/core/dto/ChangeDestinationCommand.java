package br.furb.pkg.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeDestinationCommand(
        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP de destino inválido") String newCep
) {}
