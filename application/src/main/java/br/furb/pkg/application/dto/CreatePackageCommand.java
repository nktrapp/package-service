package br.furb.pkg.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Dados para criação de um pacote")
public record CreatePackageCommand(
        @Schema(description = "CEP de origem (8 dígitos, sem hífen)", example = "89010000")
        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP de origem inválido")
        String senderCep,

        @Schema(description = "CEP de destino (8 dígitos, sem hífen)", example = "01310930")
        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP de destino inválido")
        String recipientCep,

        @Schema(description = "Peso do pacote em quilogramas", example = "2.5")
        @NotNull @Positive(message = "Peso deve ser positivo")
        BigDecimal weight,

        @Schema(description = "Descrição livre do conteúdo (máx. 500 caracteres)", example = "Livros")
        @Size(max = 500, message = "Descrição excede 500 caracteres")
        String description
) {}
