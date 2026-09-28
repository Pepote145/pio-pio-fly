package com.piopiofly.matches;

import com.piopiofly.config.PioPioFlyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trae el calendario de la fuente y guarda los partidos fuera de casa del club, sin duplicarlos. */
@Service
public class FixtureSyncService {

    private static final Logger log = LoggerFactory.getLogger(FixtureSyncService.class);

    private final FixtureProvider fixtureProvider;
    private final AwayMatchRepository awayMatchRepository;
    private final String clubSlug;

    public FixtureSyncService(FixtureProvider fixtureProvider, AwayMatchRepository awayMatchRepository,
                              PioPioFlyProperties properties) {
        this.fixtureProvider = fixtureProvider;
        this.awayMatchRepository = awayMatchRepository;
        this.clubSlug = properties.clubSlug();
    }

    @Transactional
    public SyncResult sync() {
        int created = 0;
        int updated = 0;

        for (Fixture fixture : fixtureProvider.fetchFixtures()) {
            if (!fixture.isAwayMatchFor(clubSlug)) {
                continue;
            }

            AwayMatch match = awayMatchRepository
                    .findByProviderAndExternalId(fixtureProvider.name(), fixture.externalId())
                    .orElse(null);
            if (match == null) {
                match = new AwayMatch(fixtureProvider.name(), fixture.externalId());
                created++;
            } else {
                updated++;
            }
            match.updateFrom(fixture);
            awayMatchRepository.save(match);
        }

        SyncResult result = new SyncResult(created, updated);
        log.info("Calendario sincronizado desde {}: {} partidos nuevos, {} actualizados",
                fixtureProvider.name(), result.created(), result.updated());
        return result;
    }

    public record SyncResult(int created, int updated) {
    }
}
