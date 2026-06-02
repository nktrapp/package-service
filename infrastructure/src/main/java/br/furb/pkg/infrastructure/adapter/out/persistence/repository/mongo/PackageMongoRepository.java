package br.furb.pkg.infrastructure.adapter.out.persistence.repository.mongo;

import br.furb.pkg.infrastructure.adapter.out.persistence.document.PackageDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PackageMongoRepository extends MongoRepository<PackageDocument, String> {

    List<PackageDocument> findByStatus(String status);
}
