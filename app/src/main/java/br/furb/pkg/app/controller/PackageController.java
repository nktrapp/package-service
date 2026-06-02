package br.furb.pkg.app.controller;

import br.furb.pkg.core.dto.CreatePackageCommand;
import br.furb.pkg.core.dto.PackageResponse;
import br.furb.pkg.core.dto.UpdateStatusCommand;
import br.furb.pkg.core.usecase.CreatePackageUseCase;
import br.furb.pkg.core.usecase.GetPackageUseCase;
import br.furb.pkg.core.usecase.ListPackagesUseCase;
import br.furb.pkg.core.usecase.UpdatePackageStatusUseCase;
import br.furb.pkg.domain.model.PackageStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class PackageController {

    private final CreatePackageUseCase createPackageUseCase;
    private final GetPackageUseCase getPackageUseCase;
    private final ListPackagesUseCase listPackagesUseCase;
    private final UpdatePackageStatusUseCase updatePackageStatusUseCase;

    @PostMapping
    public ResponseEntity<PackageResponse> create(@Valid @RequestBody CreatePackageCommand command) {
        PackageResponse response = createPackageUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackageResponse> getById(@PathVariable String id) {
        PackageResponse response = getPackageUseCase.execute(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<PackageResponse>> list(@RequestParam(required = false) PackageStatus status) {
        List<PackageResponse> response = listPackagesUseCase.execute(status);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PackageResponse> updateStatus(@PathVariable String id,
                                                        @Valid @RequestBody UpdateStatusCommand command) {
        UpdateStatusCommand enriched = new UpdateStatusCommand(id, command.newStatus());
        PackageResponse response = updatePackageStatusUseCase.execute(enriched);
        return ResponseEntity.ok(response);
    }
}
