package org.ulpgc.dacd.business;

public record TravelRecommendation(
        AwayMatchView match,
        String destinationAirport,
        int availableFlights,
        FlightInfoView firstFlight,
        String recommendationText
) {
}
