package com.piopiofly.matches;

import java.util.List;

/** Fuente del calendario de partidos del club. */
public interface FixtureProvider {

    /** Identificador de la fuente; junto al id externo, identifica cada partido. */
    String name();

    /**
     * @throws FixtureProviderException si la fuente no responde o su formato ha cambiado
     */
    List<Fixture> fetchFixtures();
}
