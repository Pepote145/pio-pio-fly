package org.ulpgc.dacd.domain;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AirportMapping {
    private final Map<String, String> airportByLocation;

    public AirportMapping() {
        this.airportByLocation = new HashMap<>();
        this.airportByLocation.put("Andorra", "BCN");
        this.airportByLocation.put("FC Andorra", "BCN");
        this.airportByLocation.put("Barcelona", "BCN");
        this.airportByLocation.put("Madrid", "MAD");
        this.airportByLocation.put("Sevilla", "SVQ");
        this.airportByLocation.put("Valencia", "VLC");
        this.airportByLocation.put("Zaragoza", "ZAZ");
        this.airportByLocation.put("Almería", "LEI");
        this.airportByLocation.put("A Coruña", "LCG");
        this.airportByLocation.put("Malaga", "AGP");
        this.airportByLocation.put("Bilbao", "BIO");
        this.airportByLocation.put("Deportivo", "LCG");
        this.airportByLocation.put("Santander", "SDR");
        this.airportByLocation.put("Oviedo", "OVD");
        this.airportByLocation.put("FC Barcelona", "BCN");
        this.airportByLocation.put("Real Madrid", "MAD");
        this.airportByLocation.put("Real Madrid CF", "MAD");
        this.airportByLocation.put("Sevilla FC", "SVQ");
        this.airportByLocation.put("Valencia CF", "VLC");
        this.airportByLocation.put("UD Almería", "LEI");
        this.airportByLocation.put("RC Deportivo", "LCG");
        this.airportByLocation.put("Real Zaragoza", "ZAZ");
        this.airportByLocation.put("Athletic Club", "BIO");
        this.airportByLocation.put("Racing de Santander", "SDR");
        this.airportByLocation.put("Real Oviedo", "OVD");
    }

    public AirportMapping(Map<String, String> airportByLocation) {
        this.airportByLocation = new HashMap<>(airportByLocation);
    }

    public Map<String, String> getAirportByLocation() {
        return new HashMap<>(airportByLocation);
    }

    public String getAirportCode(String locationOrTeam) {
        if (locationOrTeam == null || locationOrTeam.isBlank()) {
            return null;
        }

        String exactMatch = airportByLocation.get(locationOrTeam);
        if (exactMatch != null) {
            return exactMatch;
        }

        String normalizedValue = locationOrTeam.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : airportByLocation.entrySet()) {
            if (normalizedValue.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "AirportMapping{" +
                "airportByLocation=" + airportByLocation +
                '}';
    }
}
