package br.furb.pkg.core.usecase;

import br.furb.pkg.core.dto.PackageResponse;
import br.furb.pkg.core.mapper.PackageMapper;
import br.furb.pkg.domain.exception.PackageNotFoundException;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.port.PackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GetPackageUseCase {

    private final PackageRepository packageRepository;

    public PackageResponse execute(String packageId) {
        log.info("[get-package] Fetching package {}", packageId);

        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException(packageId));

        return PackageMapper.INSTANCE.toResponse(pkg);
    }
}
