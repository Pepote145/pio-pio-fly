package com.piopiofly.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;

/** PostgreSQL real arrancado en el propio proceso de tests; se comparte entre todos los contextos. */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresTestConfig {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo arrancar PostgreSQL embebido", e);
        }
    }

    @Bean
    DataSource dataSource() {
        return POSTGRES.getPostgresDatabase();
    }
}
