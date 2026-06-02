package br.furb.pkg.infrastructure.adapter.out.persistence.repository.mongo;

import br.furb.pkg.infrastructure.adapter.out.persistence.document.InboxDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InboxMongoRepository extends MongoRepository<InboxDocument, String> {

    boolean existsByEventId(String eventId);
}
