package br.furb.pkg.infrastructure.config;

import br.furb.pkg.domain.event.PackageCreatedEvent;
import br.furb.pkg.domain.event.PackageDestinationChangedEvent;
import br.furb.pkg.domain.event.PackageStatusUpdatedEvent;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(NativeHintsConfig.JacksonBindingHints.class)
public class NativeHintsConfig {

    static class JacksonBindingHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            new BindingReflectionHintsRegistrar().registerReflectionHints(
                    hints.reflection(),
                    PackageCreatedEvent.Payload.class,
                    PackageStatusUpdatedEvent.Payload.class,
                    PackageDestinationChangedEvent.Payload.class);
        }
    }
}
