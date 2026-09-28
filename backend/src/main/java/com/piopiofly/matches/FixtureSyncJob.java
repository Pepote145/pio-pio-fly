package com.piopiofly.matches;

import com.piopiofly.config.PioPioFlyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mantiene el calendario al día: al arrancar y periódicamente. */
@Component
class FixtureSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FixtureSyncJob.class);

    private final FixtureSyncService fixtureSyncService;
    private final boolean syncOnStartup;

    FixtureSyncJob(FixtureSyncService fixtureSyncService, PioPioFlyProperties properties) {
        this.fixtureSyncService = fixtureSyncService;
        this.syncOnStartup = properties.fixtures().syncOnStartup();
    }

    @EventListener(ApplicationReadyEvent.class)
    void syncOnStartup() {
        if (syncOnStartup) {
            runSync();
        }
    }

    @Scheduled(cron = "${piopiofly.fixtures.sync-cron}")
    void syncPeriodically() {
        runSync();
    }

    private void runSync() {
        try {
            fixtureSyncService.sync();
        } catch (FixtureProviderException e) {
            // Si la fuente falla, se conservan los partidos ya guardados y se reintenta en la siguiente ejecución.
            log.warn("No se pudo sincronizar el calendario: {}", e.getMessage(), e);
        }
    }
}
