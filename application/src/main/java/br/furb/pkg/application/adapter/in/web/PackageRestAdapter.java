package br.furb.pkg.application.adapter.in.web;

import br.furb.pkg.application.dto.ChangeDestinationCommand;
import br.furb.pkg.application.dto.CreatePackageCommand;
import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.dto.UpdateStatusCommand;
import br.furb.pkg.application.usecase.ChangePackageDestinationUseCase;
import br.furb.pkg.application.usecase.CreatePackageUseCase;
import br.furb.pkg.application.usecase.GetPackageUseCase;
import br.furb.pkg.application.usecase.ListPackagesUseCase;
import br.furb.pkg.application.usecase.UpdatePackageStatusUseCase;
import br.furb.pkg.domain.model.PackageStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/packages")
@RequiredArgsConstructor
@Tag(name = "Packages", description = "Ciclo de vida de pacotes: criação, consulta, status e destino")
public class PackageRestAdapter {

    private final CreatePackageUseCase createPackageUseCase;
    private final GetPackageUseCase getPackageUseCase;
    private final ListPackagesUseCase listPackagesUseCase;
    private final UpdatePackageStatusUseCase updatePackageStatusUseCase;
    private final ChangePackageDestinationUseCase changePackageDestinationUseCase;

    @Operation(summary = "Cria um pacote",
            description = "Registra um novo pacote a partir dos CEPs de origem e destino, peso e descrição. "
                    + "O pacote nasce aguardando cálculo de rota.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pacote criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Conflito de escrita concorrente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<PackageResponse> create(@Valid @RequestBody CreatePackageCommand command) {
        PackageResponse response = createPackageUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Busca um pacote por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pacote encontrado"),
            @ApiResponse(responseCode = "404", description = "Pacote não encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<PackageResponse> getById(
            @Parameter(description = "Identificador do pacote", example = "665f1b2c9a3e4d0012ab34cd")
            @PathVariable String id) {
        PackageResponse response = getPackageUseCase.execute(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lista pacotes",
            description = "Lista todos os pacotes, opcionalmente filtrando por status.")
    @ApiResponse(responseCode = "200", description = "Lista de pacotes")
    @GetMapping
    public ResponseEntity<List<PackageResponse>> list(
            @Parameter(description = "Filtra pelos pacotes neste status (opcional)")
            @RequestParam(required = false) PackageStatus status) {
        List<PackageResponse> response = listPackagesUseCase.execute(status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Atualiza o status de um pacote",
            description = "Aplica uma transição na máquina de estados do pacote. Transições inválidas são rejeitadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Pacote não encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Transição de status inválida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Conflito de escrita concorrente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<PackageResponse> updateStatus(
            @Parameter(description = "Identificador do pacote", example = "665f1b2c9a3e4d0012ab34cd")
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusCommand command) {
        UpdateStatusCommand enriched = new UpdateStatusCommand(id, command.newStatus());
        PackageResponse response = updatePackageStatusUseCase.execute(enriched);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Troca o destino de um pacote",
            description = "Altera o CEP de destino do pacote, recolocando-o como aguardando recálculo de rota.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Destino alterado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Pacote não encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Estado do pacote não permite troca de destino",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Conflito de escrita concorrente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/destination")
    public ResponseEntity<PackageResponse> changeDestination(
            @Parameter(description = "Identificador do pacote", example = "665f1b2c9a3e4d0012ab34cd")
            @PathVariable String id,
            @Valid @RequestBody ChangeDestinationCommand command) {
        PackageResponse response = changePackageDestinationUseCase.execute(id, command);
        return ResponseEntity.ok(response);
    }
}
