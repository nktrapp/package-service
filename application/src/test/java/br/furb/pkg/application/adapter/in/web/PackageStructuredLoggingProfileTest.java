package br.furb.pkg.application.adapter.in.web;

import br.furb.pkg.application.usecase.ChangePackageDestinationUseCase;
import br.furb.pkg.application.usecase.CreatePackageUseCase;
import br.furb.pkg.application.usecase.GetPackageUseCase;
import br.furb.pkg.application.usecase.ListPackagesUseCase;
import br.furb.pkg.application.usecase.UpdatePackageStatusUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(PackageRestAdapter.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "MONGODB_URI=mongodb://localhost:27017/package_test",
        "APP_ENVIRONMENT=prod",
        "APP_VERSION=test-version"
})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Package structured logging profile")
class PackageStructuredLoggingProfileTest {

    private static final Logger log = LoggerFactory.getLogger(PackageStructuredLoggingProfileTest.class);

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
    @DisplayName("Prod logs expose trace and span ids as top-level JSON fields")
    void shouldExposeTraceAndSpanIdsInProdStructuredLogs(CapturedOutput output) {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        MDC.put("spanId", "00f067aa0ba902b7");
        try {
            log.info("structured-log-correlation-smoke");
        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
        }

        assertThat(output.getOut())
                .contains("\"traceId\":\"4bf92f3577b34da6a3ce929d0e0e4736\"")
                .contains("\"spanId\":\"00f067aa0ba902b7\"")
                .contains("\"serviceName\":\"package-service\"")
                .contains("\"environment\":\"prod\"")
                .contains("\"version\":\"test-version\"");
    }
}
