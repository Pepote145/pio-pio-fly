# Pruebas manuales Sprint 2

Este documento recoge las pruebas manuales mínimas para verificar la integración Publisher/Subscriber con ActiveMQ y el Event Store Builder.

## Requisitos previos

- Java 21 instalado.
- ActiveMQ levantado en `tcp://localhost:61616`.
- Usar Maven con el repositorio local del proyecto mediante `-Dmaven.repo.local=.m2/repository`.

## Compilación del proyecto

Desde la raíz del proyecto:

```bash
../TrainerControl/.tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository clean install
```

## Ejecución del Event Store Builder

En una terminal, arrancar el consumidor durable:

```bash
../TrainerControl/.tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository -pl event-store-builder exec:java -Dexec.mainClass=org.ulpgc.dacd.eventstore.EventStoreBuilderApp
```

El proceso debe quedar vivo hasta pararlo con `Ctrl+C`.

## Prueba con publisher manual

En otra terminal, publicar eventos de prueba en los topics `AwayMatch` y `FlightInfo`:

```bash
../TrainerControl/.tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository -pl event-store-builder exec:java -Dexec.mainClass=org.ulpgc.dacd.eventstore.EventStoreManualPublisher
```

El Event Store Builder debe mostrar los eventos recibidos por consola y guardarlos en `eventstore/`.

## Prueba con feeders reales

También se puede ejecutar el `Main` del módulo `app` para que los feeders reales publiquen eventos de LaLiga y AENA, siempre que las fuentes externas estén disponibles.

El flujo esperado es:

- El feeder de partidos publica eventos en el topic `AwayMatch`.
- El feeder de vuelos publica eventos en el topic `FlightInfo`.
- El Event Store Builder consume los eventos y los guarda en formato NDJSON.

## Comprobación del eventstore

Listar los ficheros generados:

```bash
find eventstore -type f -name "*.events" -print
```

Ver las últimas líneas de cada fichero:

```bash
find eventstore -type f -name "*.events" -print -exec tail -n 5 {} \;
```

Cada fichero debe contener un JSON por línea.

## Estructura esperada

Para eventos reales de los feeders:

```text
eventstore/AwayMatch/laliga-matches-source/YYYYMMDD.events
eventstore/FlightInfo/aena-flights-source/YYYYMMDD.events
```

Para eventos publicados con el publisher manual:

```text
eventstore/AwayMatch/manual-test-source/YYYYMMDD.events
eventstore/FlightInfo/manual-test-source/YYYYMMDD.events
```

## Prueba de durabilidad

1. Arrancar ActiveMQ.
2. Arrancar una vez el Event Store Builder para crear las suscripciones durables.
3. Parar el Event Store Builder con `Ctrl+C`.
4. Publicar eventos con `EventStoreManualPublisher`.
5. Volver a arrancar el Event Store Builder.
6. Comprobar que consume los mensajes pendientes y los guarda en `eventstore/`.

## Prueba de reconexión

1. Arrancar el Event Store Builder sin ActiveMQ levantado.
2. Comprobar que el proceso no termina y queda reintentando la conexión.
3. Levantar ActiveMQ después.
4. Comprobar que el Event Store Builder conecta automáticamente.
