package org.ulpgc.dacd.domain;

import java.util.HashMap;
import java.util.Map;

public class AirportMapping {
    private final Map<String, String> airportByLocation;

    public AirportMapping() {
        this.airportByLocation = new HashMap<>();
        this.airportByLocation.put("Andorra", "BCN");
        this.airportByLocation.put("Barcelona", "BCN");
        this.airportByLocation.put("Madrid", "MAD");
        this.airportByLocation.put("Sevilla", "SVQ");
        this.airportByLocation.put("Valencia", "VLC");
        this.airportByLocation.put("Zaragoza", "ZAZ");
        this.airportByLocation.put("Malaga", "AGP");
        this.airportByLocation.put("Bilbao", "BIO");
        this.airportByLocation.put("Santander", "SDR");
        this.airportByLocation.put("Oviedo", "OVD");
    }

    public AirportMapping(Map<String, String> airportByLocation) {
        this.airportByLocation = new HashMap<>(airportByLocation);
    }

    public Map<String, String> getAirportByLocation() {
        return new HashMap<>(airportByLocation);
    }

    public String getAirportCode(String locationOrTeam) {
        return airportByLocation.get(locationOrTeam);
    }

    @Override
    public String toString() {
        return "AirportMapping{" +
                "airportByLocation=" + airportByLocation +
                '}';
    }
}
