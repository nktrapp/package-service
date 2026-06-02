package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.CreatePackageCommand;
import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.mapper.PackageMapper;
import br.furb.pkg.domain.event.PackageCreatedEvent;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.OutboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class CreatePackageUseCase {

    private final PackageRepositoryPort packageRepository;
    private final OutboxRepositoryPort outboxRepository;

    @Transactional
    public PackageResponse execute(CreatePackageCommand command) {
        log.info("[create-package] Creating package from {} to {}", command.senderCep(), command.recipientCep());

        Package pkg = Package.builder()
                .senderCep(command.senderCep())
                .recipientCep(command.recipientCep())
                .weight(command.weight())
                .description(command.description())
                .status(PackageStatus.CREATED)
                .createdAt(Instant.now())
                .build();

        Package saved = packageRepository.save(pkg);

        PackageCreatedEvent event = PackageCreatedEvent.builder()
                .payload(PackageCreatedEvent.Payload.builder()
                        .packageId(saved.getId())
                        .senderCep(saved.getSenderCep())
                        .recipientCep(saved.getRecipientCep())
                        .weight(saved.getWeight())
                        .description(saved.getDescription())
                        .build())
                .build();

        outboxRepository.save(event);

        log.info("[create-package] Package created with id {}", saved.getId());

        return PackageMapper.INSTANCE.toResponse(saved);
    }
}
