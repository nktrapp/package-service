package br.furb.pkg.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePackageCommand(
        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP de origem inválido")
        String senderCep,

        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP de destino inválido")
        String recipientCep,

        @NotNull @Positive(message = "Peso deve ser positivo")
        BigDecimal weight,

        @Size(max = 500, message = "Descrição excede 500 caracteres")
        String description
) {}
