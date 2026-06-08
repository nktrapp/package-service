package br.furb.pkg.application.usecase;

import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.domain.exception.PackageNotFoundException;
import br.furb.pkg.domain.model.Package;
import br.furb.pkg.domain.model.PackageStatus;
import br.furb.pkg.domain.port.PackageRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPackageUseCase")
class GetPackageUseCaseTest {

    @Mock
    PackageRepositoryPort packageRepository;

    @Test
    @DisplayName("Given an existing package, should return its response")
    void shouldReturnPackage() {
        GetPackageUseCase useCase = new GetPackageUseCase(packageRepository);
        when(packageRepository.findById("pkg-1")).thenReturn(Optional.of(buildPackage()));

        PackageResponse response = useCase.execute("pkg-1");

        assertThat(response.id()).isEqualTo("pkg-1");
    }

    @Test
    @DisplayName("Given an unknown package, should throw PackageNotFoundException")
    void shouldThrowWhenPackageNotFound() {
        GetPackageUseCase useCase = new GetPackageUseCase(packageRepository);
        when(packageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing"))
                .isInstanceOf(PackageNotFoundException.class);
    }

    private Package buildPackage() {
        return Package.builder()
                .id("pkg-1")
                .senderCep("89000000")
                .recipientCep("89010000")
                .weight(BigDecimal.TEN)
                .status(PackageStatus.CREATED)
                .description("Package")
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }
}
