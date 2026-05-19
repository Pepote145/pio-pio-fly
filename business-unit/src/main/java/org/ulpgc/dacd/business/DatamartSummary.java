package org.ulpgc.dacd.business;

public record DatamartSummary(
        int awayMatches,
        int flights,
        int destinations,
        int sources,
        String latestAwayMatchCapturedAt,
        String latestFlightCapturedAt
) {
}
