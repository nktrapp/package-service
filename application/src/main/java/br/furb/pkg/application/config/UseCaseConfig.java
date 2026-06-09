package br.furb.pkg.application.config;

import br.furb.pkg.application.mapper.PackageMapper;
import br.furb.pkg.application.usecase.ChangePackageDestinationUseCase;
import br.furb.pkg.application.usecase.CreatePackageUseCase;
import br.furb.pkg.application.usecase.GetPackageUseCase;
import br.furb.pkg.application.usecase.ListPackagesUseCase;
import br.furb.pkg.application.usecase.ProcessRouteCalculatedUseCase;
import br.furb.pkg.application.usecase.ProcessRouteFailedUseCase;
import br.furb.pkg.application.usecase.UpdatePackageStatusUseCase;
import br.furb.pkg.domain.port.InboxRepositoryPort;
import br.furb.pkg.domain.port.OutboxRepositoryPort;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CreatePackageUseCase createPackageUseCase(PackageRepositoryPort packageRepository,
                                                     OutboxRepositoryPort outboxRepository,
                                                     PackageMapper packageMapper) {
        return new CreatePackageUseCase(packageRepository, outboxRepository, packageMapper);
    }

    @Bean
    public GetPackageUseCase getPackageUseCase(PackageRepositoryPort packageRepository,
                                               PackageMapper packageMapper) {
        return new GetPackageUseCase(packageRepository, packageMapper);
    }

    @Bean
    public ListPackagesUseCase listPackagesUseCase(PackageRepositoryPort packageRepository,
                                                   PackageMapper packageMapper) {
        return new ListPackagesUseCase(packageRepository, packageMapper);
    }

    @Bean
    public UpdatePackageStatusUseCase updatePackageStatusUseCase(PackageRepositoryPort packageRepository,
                                                                 OutboxRepositoryPort outboxRepository,
                                                                 PackageMapper packageMapper) {
        return new UpdatePackageStatusUseCase(packageRepository, outboxRepository, packageMapper);
    }

    @Bean
    public ChangePackageDestinationUseCase changePackageDestinationUseCase(PackageRepositoryPort packageRepository,
                                                                           OutboxRepositoryPort outboxRepository,
                                                                           PackageMapper packageMapper) {
        return new ChangePackageDestinationUseCase(packageRepository, outboxRepository, packageMapper);
    }

    @Bean
    public ProcessRouteCalculatedUseCase processRouteCalculatedUseCase(PackageRepositoryPort packageRepository,
                                                                       InboxRepositoryPort inboxRepository) {
        return new ProcessRouteCalculatedUseCase(packageRepository, inboxRepository);
    }

    @Bean
    public ProcessRouteFailedUseCase processRouteFailedUseCase(PackageRepositoryPort packageRepository,
                                                               InboxRepositoryPort inboxRepository) {
        return new ProcessRouteFailedUseCase(packageRepository, inboxRepository);
    }
}
