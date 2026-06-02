package br.furb.pkg.infrastructure.persistence.repository.spring;

import br.furb.pkg.infrastructure.persistence.document.InboxDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataInboxRepository extends MongoRepository<InboxDocument, String> {

    boolean existsByEventId(String eventId);
}
