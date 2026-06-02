package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.mapper.PackageMapper;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static java.util.Objects.isNull;

@Slf4j
@RequiredArgsConstructor
public class ListPackagesUseCase {

    private final PackageRepositoryPort packageRepository;

    public List<PackageResponse> execute(PackageStatus statusFilter) {
        log.info("[list-packages] Listing packages with filter: {}", statusFilter);

        List<Package> packages = isNull(statusFilter)
                ? packageRepository.findAll()
                : packageRepository.findByStatus(statusFilter);

        return packages.stream()
                .map(PackageMapper.INSTANCE::toResponse)
                .toList();
    }
}
