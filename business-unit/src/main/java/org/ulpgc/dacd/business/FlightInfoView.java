package org.ulpgc.dacd.business;

public record FlightInfoView(
        String flightNumber,
        String airline,
        String originAirport,
        String destinationAirport,
        String scheduledDateTime,
        String status,
        String terminal,
        String source
) {
}
