package br.furb.pkg.infrastructure.persistence.repository;

import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.PackageRepository;
import br.furb.pkg.infrastructure.persistence.document.PackageDocument;
import br.furb.pkg.infrastructure.persistence.mapper.PackageDocumentMapper;
import br.furb.pkg.infrastructure.persistence.repository.spring.SpringDataPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoPackageRepository implements PackageRepository {

    private final SpringDataPackageRepository springDataRepository;

    @Override
    public Package save(Package pkg) {
        PackageDocument document = PackageDocumentMapper.toDocument(pkg);
        PackageDocument saved = springDataRepository.save(document);
        return PackageDocumentMapper.toDomain(saved);
    }

    @Override
    public Optional<Package> findById(String id) {
        return springDataRepository.findById(id)
                .map(PackageDocumentMapper::toDomain);
    }

    @Override
    public List<Package> findAll() {
        return springDataRepository.findAll().stream()
                .map(PackageDocumentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Package> findByStatus(PackageStatus status) {
        return springDataRepository.findByStatus(status.name()).stream()
                .map(PackageDocumentMapper::toDomain)
                .toList();
    }
}
