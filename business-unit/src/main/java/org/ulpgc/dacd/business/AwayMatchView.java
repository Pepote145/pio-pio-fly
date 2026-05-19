package org.ulpgc.dacd.business;

public record AwayMatchView(
        String externalId,
        String competition,
        String homeTeam,
        String awayTeam,
        String matchDate,
        String city,
        String stadium,
        String destinationAirport,
        String source
) {
}
