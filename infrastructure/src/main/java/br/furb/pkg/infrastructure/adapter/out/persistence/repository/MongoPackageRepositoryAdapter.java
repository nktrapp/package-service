package br.furb.pkg.infrastructure.adapter.out.persistence.repository;

import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import br.furb.pkg.infrastructure.adapter.out.persistence.document.PackageDocument;
import br.furb.pkg.infrastructure.adapter.out.persistence.mapper.PackageDocumentMapper;
import br.furb.pkg.infrastructure.adapter.out.persistence.repository.mongo.PackageMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoPackageRepositoryAdapter implements PackageRepositoryPort {

    private final PackageMongoRepository mongoRepository;

    @Override
    public Package save(Package pkg) {
        PackageDocument document = PackageDocumentMapper.toDocument(pkg);
        PackageDocument saved = mongoRepository.save(document);
        return PackageDocumentMapper.toDomain(saved);
    }

    @Override
    public Optional<Package> findById(String id) {
        return mongoRepository.findById(id)
                .map(PackageDocumentMapper::toDomain);
    }

    @Override
    public List<Package> findAll() {
        return mongoRepository.findAll().stream()
                .map(PackageDocumentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Package> findByStatus(PackageStatus status) {
        return mongoRepository.findByStatus(status.name()).stream()
                .map(PackageDocumentMapper::toDomain)
                .toList();
    }
}
