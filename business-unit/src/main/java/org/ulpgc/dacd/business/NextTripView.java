package org.ulpgc.dacd.business;

import java.util.List;

public record NextTripView(
        AwayMatchView match,
        List<FlightInfoView> outboundFlights,
        List<FlightInfoView> returnFlights,
        String outboundWindowStart,
        String outboundWindowEnd,
        String returnDate,
        boolean invalidMatchDate
) {
}
