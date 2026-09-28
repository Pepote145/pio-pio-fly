package com.piopiofly.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("piopiofly")
public record PioPioFlyProperties(
        @NotBlank String clubSlug,
        @Valid @NotNull Fixtures fixtures
) {

    public record Fixtures(
            @NotNull URI laligaUrl,
            @NotNull Duration timeout,
            @NotBlank String syncCron,
            boolean syncOnStartup
    ) {
    }
}
