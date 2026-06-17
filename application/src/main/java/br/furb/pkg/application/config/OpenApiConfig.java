package br.furb.pkg.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String FALLBACK_VERSION = "dev";

    @Bean
    public OpenAPI packageOpenAPI(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null ? build.getVersion() : FALLBACK_VERSION;

        Info info = new Info()
                .title("Package Service API")
                .version(version)
                .description("""
                        Serviço de pacotes do TCC FURB. Gerencia o ciclo de vida de pacotes \
                        (criação, consulta, atualização de status e troca de destino), com o \
                        status governado por uma máquina de estados. Publica eventos de pacote \
                        e consome eventos de rota calculada via SQS (outbox/inbox transacional).""")
                .contact(new Contact()
                        .name("FURB - Trabalho de Conclusão de Curso")
                        .url("https://www.furb.br"))
                .license(new License()
                        .name("Uso acadêmico"));

        Server localServer = new Server()
                .url("http://localhost:8081")
                .description("Ambiente local (docker compose)");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}
