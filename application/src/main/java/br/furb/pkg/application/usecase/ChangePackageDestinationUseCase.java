package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.ChangeDestinationCommand;
import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.mapper.PackageMapper;
import br.furb.pkg.domain.event.PackageDestinationChangedEvent;
import br.furb.pkg.domain.exception.PackageNotFoundException;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.port.OutboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class ChangePackageDestinationUseCase {

    private final PackageRepositoryPort packageRepository;
    private final OutboxRepositoryPort outboxRepository;

    @Transactional
    public PackageResponse execute(String packageId, ChangeDestinationCommand command) {
        log.info("[change-destination] Changing destination of package {} to {}", packageId, command.newCep());

        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException(packageId));

        String previousCep = pkg.getRecipientCep();
        Package updated = pkg.withRecipientCep(command.newCep());
        Package saved = packageRepository.save(updated);

        PackageDestinationChangedEvent event = PackageDestinationChangedEvent.builder()
                .payload(PackageDestinationChangedEvent.Payload.builder()
                        .packageId(saved.getId())
                        .senderCep(saved.getSenderCep())
                        .previousCep(previousCep)
                        .newCep(saved.getRecipientCep())
                        .build())
                .build();

        outboxRepository.save(event);

        log.info("[change-destination] Package {} destination changed, awaiting recalculation", saved.getId());

        return PackageMapper.INSTANCE.toResponse(saved);
    }
}
