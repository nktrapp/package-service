package br.furb.pkg.infrastructure.config;

import br.furb.pkg.core.usecase.CreatePackageUseCase;
import br.furb.pkg.core.usecase.GetPackageUseCase;
import br.furb.pkg.core.usecase.ListPackagesUseCase;
import br.furb.pkg.core.usecase.ProcessRouteCalculatedUseCase;
import br.furb.pkg.core.usecase.UpdatePackageStatusUseCase;
import br.furb.pkg.domain.port.InboxRepository;
import br.furb.pkg.domain.port.OutboxRepository;
import br.furb.pkg.domain.port.PackageRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CreatePackageUseCase createPackageUseCase(PackageRepository packageRepository, OutboxRepository outboxRepository) {
        return new CreatePackageUseCase(packageRepository, outboxRepository);
    }

    @Bean
    public GetPackageUseCase getPackageUseCase(PackageRepository packageRepository) {
        return new GetPackageUseCase(packageRepository);
    }

    @Bean
    public ListPackagesUseCase listPackagesUseCase(PackageRepository packageRepository) {
        return new ListPackagesUseCase(packageRepository);
    }

    @Bean
    public UpdatePackageStatusUseCase updatePackageStatusUseCase(PackageRepository packageRepository, OutboxRepository outboxRepository) {
        return new UpdatePackageStatusUseCase(packageRepository, outboxRepository);
    }

    @Bean
    public ProcessRouteCalculatedUseCase processRouteCalculatedUseCase(PackageRepository packageRepository, InboxRepository inboxRepository) {
        return new ProcessRouteCalculatedUseCase(packageRepository, inboxRepository);
    }
}
