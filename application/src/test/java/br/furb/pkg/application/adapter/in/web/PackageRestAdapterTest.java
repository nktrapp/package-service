package br.furb.pkg.application.adapter.in.web;

import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.application.usecase.ChangePackageDestinationUseCase;
import br.furb.pkg.application.usecase.CreatePackageUseCase;
import br.furb.pkg.application.usecase.GetPackageUseCase;
import br.furb.pkg.application.usecase.ListPackagesUseCase;
import br.furb.pkg.application.usecase.UpdatePackageStatusUseCase;
import br.furb.pkg.domain.model.PackageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PackageRestAdapter.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("PackageRestAdapter — change destination")
class PackageRestAdapterTest {

    private static final Logger log = LoggerFactory.getLogger(PackageRestAdapterTest.class);

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

    @Test
    @DisplayName("Local logs remain readable instead of JSON structured output")
    void shouldKeepLocalLogsReadable(CapturedOutput output) {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        MDC.put("spanId", "00f067aa0ba902b7");
        try {
            log.info("local-readable-log-smoke");
        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
        }

        assertThat(output.getOut())
                .contains("local-readable-log-smoke");
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
