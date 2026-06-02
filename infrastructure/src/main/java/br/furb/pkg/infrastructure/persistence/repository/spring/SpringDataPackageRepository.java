package br.furb.pkg.infrastructure.persistence.repository.spring;

import br.furb.pkg.infrastructure.persistence.document.PackageDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringDataPackageRepository extends MongoRepository<PackageDocument, String> {

    List<PackageDocument> findByStatus(String status);
}
