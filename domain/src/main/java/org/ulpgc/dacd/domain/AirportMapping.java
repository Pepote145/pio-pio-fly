package org.ulpgc.dacd.domain;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class AirportMapping {
    private final Map<String, String> airportByTeam;

    public AirportMapping() {
        this.airportByTeam = new LinkedHashMap<>();
        registerDefaultMappings();
    }

    public AirportMapping(Map<String, String> airportByLocation) {
        this.airportByTeam = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : airportByLocation.entrySet()) {
            putMapping(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, String> getAirportByLocation() {
        return new HashMap<>(airportByTeam);
    }

    public String getAirportCode(String locationOrTeam) {
        if (locationOrTeam == null || locationOrTeam.isBlank()) {
            return null;
        }

        String normalizedValue = normalize(locationOrTeam);
        String exactMatch = airportByTeam.get(normalizedValue);
        if (exactMatch != null) {
            return exactMatch;
        }

        for (Map.Entry<String, String> entry : airportByTeam.entrySet()) {
            if (normalizedValue.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private void registerDefaultMappings() {
        putMapping("CD Castellón", "VLC");
        putMapping("Cádiz CF", "XRY");
        putMapping("Córdoba CF", "SVQ");
        putMapping("Albacete BP", "ABC");
        putMapping("R. Sociedad B", "EAS");
        putMapping("CD Mirandés", "BIO");
        putMapping("AD Ceuta FC", "AGP");
        putMapping("Málaga CF", "AGP");
        putMapping("Cultural y Deportiva Leonesa", "LEN");
        putMapping("SD Eibar", "BIO");
        putMapping("R. Racing Club", "SDR");
        putMapping("Real Valladolid CF", "VLL");
        putMapping("UD Almería", "LEI");
        putMapping("UD Las Palmas", "LPA");
        putMapping("Granada CF", "GRX");
        putMapping("Burgos CF", "VLL");
        putMapping("RC Deportivo", "LCG");
        putMapping("FC Andorra", "BCN");
        putMapping("Real Zaragoza", "ZAZ");
        putMapping("Real Sporting", "OVD");
        putMapping("CD Leganés", "MAD");
        putMapping("SD Huesca", "ZAZ");

        putMapping("Deportivo La Coruña", "LCG");
        putMapping("Real Sociedad B", "EAS");
        putMapping("R. Sociedad B", "EAS");
        putMapping("Cultural Leonesa", "LEN");
        putMapping("Deportivo", "LCG");
        putMapping("Almería", "LEI");
        putMapping("Andorra", "BCN");
        putMapping("Zaragoza", "ZAZ");
        putMapping("Sporting", "OVD");
        putMapping("Racing", "SDR");
        putMapping("Valladolid", "VLL");
        putMapping("Granada", "GRX");
        putMapping("Málaga", "AGP");
        putMapping("Malaga", "AGP");
        putMapping("Ceuta", "AGP");
        putMapping("Eibar", "BIO");
        putMapping("Mirandés", "BIO");
        putMapping("Mirandes", "BIO");
        putMapping("Huesca", "ZAZ");
        putMapping("Leganés", "MAD");
        putMapping("Castellón", "VLC");
        putMapping("Castellon", "VLC");
        putMapping("Cádiz", "XRY");
        putMapping("Cadiz", "XRY");
        putMapping("Córdoba", "SVQ");
        putMapping("Cordoba", "SVQ");
        putMapping("Albacete", "ABC");
        putMapping("Burgos", "VLL");
    }

    private void putMapping(String teamOrAlias, String airportCode) {
        airportByTeam.put(normalize(teamOrAlias), airportCode);
    }

    private String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ");
    }

    @Override
    public String toString() {
        return "AirportMapping{" +
                "airportByTeam=" + airportByTeam +
                '}';
    }
}
