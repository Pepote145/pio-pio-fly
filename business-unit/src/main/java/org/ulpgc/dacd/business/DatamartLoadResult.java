package org.ulpgc.dacd.business;

public record DatamartLoadResult(
        int processedEvents,
        int loadedAwayMatches,
        int loadedFlights,
        int skippedEvents,
        int failedEvents
) {
}
