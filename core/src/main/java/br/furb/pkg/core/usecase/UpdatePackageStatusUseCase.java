package br.furb.pkg.core.usecase;

import br.furb.pkg.core.dto.PackageResponse;
import br.furb.pkg.core.dto.UpdateStatusCommand;
import br.furb.pkg.core.mapper.PackageMapper;
import br.furb.pkg.domain.event.PackageStatusUpdatedEvent;
import br.furb.pkg.domain.exception.PackageNotFoundException;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.port.OutboxRepository;
import br.furb.pkg.domain.port.PackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class UpdatePackageStatusUseCase {

    private final PackageRepository packageRepository;
    private final OutboxRepository outboxRepository;

    @Transactional
    public PackageResponse execute(UpdateStatusCommand command) {
        log.info("[update-status] Updating package {} to status {}", command.packageId(), command.newStatus());

        Package pkg = packageRepository.findById(command.packageId())
                .orElseThrow(() -> new PackageNotFoundException(command.packageId()));

        Package updated = pkg.withStatus(command.newStatus());
        Package saved = packageRepository.save(updated);

        PackageStatusUpdatedEvent event = PackageStatusUpdatedEvent.builder()
                .payload(PackageStatusUpdatedEvent.Payload.builder()
                        .packageId(saved.getId())
                        .previousStatus(pkg.getStatus())
                        .newStatus(saved.getStatus())
                        .build())
                .build();

        outboxRepository.save(event);

        log.info("[update-status] Package {} status updated to {}", saved.getId(), saved.getStatus());

        return PackageMapper.INSTANCE.toResponse(saved);
    }
}
