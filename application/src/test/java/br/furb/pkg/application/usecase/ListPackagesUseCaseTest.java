package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.mapper.PackageMapper;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListPackagesUseCase")
class ListPackagesUseCaseTest {

    @Mock
    PackageRepositoryPort packageRepository;

    private final PackageMapper packageMapper = Mappers.getMapper(PackageMapper.class);

    @Test
    @DisplayName("Given no status filter, should list all packages")
    void shouldListAllWhenNoFilter() {
        ListPackagesUseCase useCase = new ListPackagesUseCase(packageRepository, packageMapper);
        when(packageRepository.findAll()).thenReturn(List.of(buildPackage(PackageStatus.CREATED)));

        List<PackageResponse> responses = useCase.execute(null);

        assertThat(responses).hasSize(1);
        verify(packageRepository, never()).findByStatus(any());
    }

    @Test
    @DisplayName("Given a status filter, should query packages by that status")
    void shouldListByStatusWhenFilterProvided() {
        ListPackagesUseCase useCase = new ListPackagesUseCase(packageRepository, packageMapper);
        when(packageRepository.findByStatus(PackageStatus.IN_TRANSIT))
                .thenReturn(List.of(buildPackage(PackageStatus.IN_TRANSIT), buildPackage(PackageStatus.IN_TRANSIT)));

        List<PackageResponse> responses = useCase.execute(PackageStatus.IN_TRANSIT);

        assertThat(responses).hasSize(2);
        verify(packageRepository, never()).findAll();
    }

    private Package buildPackage(PackageStatus status) {
        return Package.builder()
                .id("pkg-1")
                .senderCep("89000000")
                .recipientCep("89010000")
                .weight(BigDecimal.TEN)
                .status(status)
                .description("Package")
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }
}
