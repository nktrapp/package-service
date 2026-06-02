package br.furb.pkg.app.controller;

import br.furb.pkg.core.dto.PackageResponse;
import br.furb.pkg.core.usecase.ChangePackageDestinationUseCase;
import br.furb.pkg.core.usecase.CreatePackageUseCase;
import br.furb.pkg.core.usecase.GetPackageUseCase;
import br.furb.pkg.core.usecase.ListPackagesUseCase;
import br.furb.pkg.core.usecase.UpdatePackageStatusUseCase;
import br.furb.pkg.domain.model.PackageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PackageController.class)
@DisplayName("PackageController — change destination")
class PackageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CreatePackageUseCase createPackageUseCase;
    @MockitoBean
    GetPackageUseCase getPackageUseCase;
    @MockitoBean
    ListPackagesUseCase listPackagesUseCase;
    @MockitoBean
    UpdatePackageStatusUseCase updatePackageStatusUseCase;
    @MockitoBean
    ChangePackageDestinationUseCase changePackageDestinationUseCase;

    @Test
    @DisplayName("PATCH /{id}/destination with a valid CEP returns 200 and the updated package")
    void shouldChangeDestinationWhenCepIsValid() throws Exception {
        when(changePackageDestinationUseCase.execute(eq("pkg-1"), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/packages/{id}/destination", "pkg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCep\":\"89030000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pkg-1"))
                .andExpect(jsonPath("$.status").value("ROUTE_PENDING"));
    }

    @Test
    @DisplayName("PATCH /{id}/destination with an invalid CEP returns 400")
    void shouldRejectInvalidCep() throws Exception {
        mockMvc.perform(patch("/api/v1/packages/{id}/destination", "pkg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCep\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    private PackageResponse sampleResponse() {
        return new PackageResponse(
                "pkg-1",
                "89000000",
                "89030000",
                BigDecimal.TEN,
                PackageStatus.ROUTE_PENDING,
                "sample",
                null,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T10:05:00Z")
        );
    }
}
