package br.furb.pkg.application.dto;

import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.model.RouteInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Representação de um pacote")
public record PackageResponse(
        @Schema(description = "Identificador do pacote", example = "665f1b2c9a3e4d0012ab34cd")
        String id,
        @Schema(description = "CEP de origem", example = "89010000")
        String senderCep,
        @Schema(description = "CEP de destino", example = "01310930")
        String recipientCep,
        @Schema(description = "Peso em quilogramas", example = "2.5")
        BigDecimal weight,
        @Schema(description = "Status atual na máquina de estados do pacote")
        PackageStatus status,
        @Schema(description = "Descrição do conteúdo", example = "Livros")
        String description,
        @Schema(description = "Rota associada ao pacote, quando já calculada")
        RouteInfo routeInfo,
        @Schema(description = "Data/hora de criação")
        Instant createdAt,
        @Schema(description = "Data/hora da última atualização")
        Instant updatedAt
) {}
