package br.furb.pkg.domain.port;

import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;

import java.util.List;
import java.util.Optional;

public interface PackageRepository {

    Package save(Package pkg);

    Optional<Package> findById(String id);

    List<Package> findAll();

    List<Package> findByStatus(PackageStatus status);
}
