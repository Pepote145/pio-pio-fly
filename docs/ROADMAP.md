# PioPio Fly — Hoja de ruta

> Última revisión: 28-sep-2026. Documento vivo: se actualiza al cerrar cada fase.

## 1. Visión

**PioPio Fly** es la web-app del aficionado de la UD Las Palmas que viaja con el equipo. Para cada partido fuera de casa, en una sola pantalla:

- cuándo y dónde se juega (y si la fecha es provisional o ya está confirmada);
- el **vuelo de ida y vuelta más barato** desde Gran Canaria, con el **precio estimado de residente canario (75%)**;
- cómo ha evolucionado ese precio y si es buen momento para comprar;
- el enlace directo a la **entrada oficial** de desplazamientos;
- el **coste total estimado** del viaje (vuelo + entrada).

Y, más adelante, que te **avise** cuando baje el vuelo.

### Lo que nos diferencia

1. **Precio de residente**: los comparadores no lo muestran bien; nosotros sí (siempre marcado como estimación).
2. **Pensado alrededor del partido**, no de la ruta: la ventana de viaje se calcula desde la fecha del partido, y se adapta cuando LaLiga confirma el horario.
3. **Histórico de precios por partido**: saber si ahora está caro o barato.

## 2. Decisiones tomadas

| Tema | Decisión | Motivo |
|---|---|---|
| Naturaleza | Proyecto personal, ya no entrega de DACD | Libertad para diseñar pensando en producto |
| Arquitectura | **Se retiran ActiveMQ, event store y scraper AENA** | Complejidad sin beneficio en un único servicio; AENA no da precios |
| Versión del curso | Se conserva en el tag `v1.0-dacd` | Queda en el historial sin estorbar |
| Backend | **Spring Boot 4.x + Java 25 (LTS)** | Java ya lo dominas; estándar de industria; trae scheduler, JPA y REST de serie |
| Base de datos | **PostgreSQL** + migraciones **Flyway** | Lista para desplegar (hay hostings gratuitos); en local vía Homebrew (no tienes Docker) |
| Frontend | **Web-app: React + Vite + TypeScript, PWA** | Instalable en el móvil sin tiendas; fácil de compartir con la peña |
| Precios de vuelos | **Travelpayouts (Aviasales Data API)** principal; **SerpApi Google Flights** como verificación | Gratis y pensado para "el más barato"; comisión de afiliado. Amadeus Self-Service cerró el 17-jul-2026 y Kiwi Tequila solo admite partners |
| Paquete Java | `com.piopiofly` (antes `org.ulpgc.dacd`) | Ya no es un proyecto de la universidad |
| Tests con base de datos | PostgreSQL embebido (zonky) | PostgreSQL real sin necesidad de Docker |

## 3. Arquitectura objetivo

```text
┌────────────────────────┐        ┌──────────────────────────────────────────┐
│  frontend/ (React PWA) │  REST  │  backend/ (Spring Boot)                  │
│  - Próximos partidos   │ ─────► │                                          │
│  - Detalle del viaje   │        │  matches   ← FixtureProvider (LaLiga)    │
│  - Gráfica de precios  │        │  airports  ← aeropuertos por rival       │
└────────────────────────┘        │  flights   ← FlightPriceProvider         │
                                  │               (Travelpayouts, SerpApi)   │
                                  │  tickets   ← enlace Onebox por partido   │
                                  │  trips     → junta todo en un "viaje"    │
                                  │  jobs      → @Scheduled: partidos/precios│
                                  └───────────────────┬──────────────────────┘
                                                      │ JPA + Flyway
                                                      ▼
                                              ┌───────────────┐
                                              │  PostgreSQL   │
                                              └───────────────┘
```

Principios:

- **Organización por funcionalidad** (`matches`, `flights`…), no por capas técnicas.
- **Cada fuente externa detrás de una interfaz** (`FixtureProvider`, `FlightPriceProvider`): si una API cae o cambia, se sustituye sin tocar el resto.
- **Nada de datos de demo en el código**: ni URLs fijas ni fechas ni aeropuertos concretos en la lógica.
- **Configuración y claves en variables de entorno** (`.env`, nunca en git).
- **Tests desde el día uno**: scrapers probados contra HTML guardado; lógica de viaje con tests unitarios.

### Estructura del repositorio

```text
pio-pio-fly/
├── backend/            Spring Boot (Maven wrapper)
│   └── src/main/java/com/piopiofly/{matches,airports,flights,tickets,trips,config}
├── frontend/           React + Vite + TypeScript
├── docs/               ROADMAP.md, decisiones, notas de APIs
├── .github/workflows/  CI: build + tests de backend y frontend
├── CLAUDE.md           Convenciones para trabajar con Claude Code
└── README.md
```

## 4. Modelo de datos

| Tabla | Campos clave | Notas |
|---|---|---|
| `away_match` ✅ | fuente, id externo, competición, temporada, jornada, rival (slug + nombre), `match_date`, `kickoff_at` (nullable → fecha provisional), estadio, ciudad, lat/lon | Único por (fuente, id externo): **se acabaron los duplicados**. Estadio, ciudad y coordenadas vienen de LaLiga |
| `team_airport` ✅ | slug del rival en laliga.com, aeropuerto IATA, prioridad, nota | Sustituye a `AirportMapping`. Varios aeropuertos por destino; datos de temporada en migración Flyway |
| `price_snapshot` | partido, sentido (IDA/VUELTA), origen, destino, fecha de vuelo, precio €, aerolínea, escalas, proveedor, enlace, `captured_at` | Histórico → gráfica y alertas. Es la evolución natural del antiguo event store |
| `ticket_info` *(fase 3)* | partido, URL, estado, precio desde, `checked_at` | |
| `alert_subscription` *(fase 4)* | contacto, partido o "todos", umbral € | |

### Lógica de la ventana de viaje

- **Fecha confirmada**: ida D-2, D-1 o el mismo día por la mañana si el partido es por la tarde/noche; vuelta D+0 por la noche o D+1.
- **Fecha provisional**: LaLiga publica primero el fin de semana de la jornada y fija el día y la hora unas semanas antes. Mientras tanto la ventana cubre de viernes a lunes y la web lo indica claramente.
- Parámetros configurables, nunca números mágicos repartidos por el código.

### Precio de residente

- El descuento (75%) se aplica sobre la tarifa y no sobre todas las tasas, así que siempre se muestra como **"≈ X € residente (estimado)"**.
- La fórmula va en un único sitio (`ResidentDiscount`), con tests.

## 5. API REST (primer borrador)

| Método | Ruta | Devuelve |
|---|---|---|
| GET | `/api/matches?upcoming=true` | Próximos partidos fuera, con "precio desde" |
| GET | `/api/matches/{id}` | Viaje completo: partido, destino, mejores vuelos ida/vuelta, precio residente, entrada, coste total |
| GET | `/api/matches/{id}/prices` | Histórico de precios para la gráfica |
| GET | `/actuator/health` | Estado del servicio |
| POST | `/api/alerts` *(fase 4)* | Alta de alerta |

## 6. Pantallas

1. **Inicio**: tarjeta grande del *próximo desplazamiento* y, debajo, la lista de partidos fuera. Cada tarjeta muestra rival, fecha (con su estado), "vuelo desde X €" y "≈ Y € residente".
2. **Detalle del viaje**: partido y estadio, aeropuerto(s), mejores vuelos de ida y vuelta con enlace de compra, gráfica de evolución, botón **Comprar entrada** y coste total.
3. **Ajustes** *(fase 4)*: aeropuerto de origen (LPA, TFN, ACE, FUE…) y si eres residente o no.

Se mantiene la identidad actual: amarillo y azul UD, el logo y el "¡Arriba d'ellos!". Diseño pensado primero para el móvil.

## 7. Fases

### Fase 0 — Repo sano y base limpia ✅ (28-sep-2026)

**Objetivo:** cimientos sólidos sobre los que construir rápido.

**Resultado:** git recuperado; tag `v1.0-dacd`; backend Spring Boot 4.1 + Java 25 con 14 tests (PostgreSQL embebido); frontend Vite mínimo; CI. Contra laliga.com real: 17 partidos fuera de la temporada 2026/27, todos con aeropuerto y sin duplicados. El scraper lee ahora el JSON `__NEXT_DATA__` de la página, en lugar de la tabla HTML.

**Pendiente conocido:** LaLiga devuelve "Andalusia" como ciudad del Córdoba CF; habrá que corregir la ciudad en origen o con una tabla de excepciones.

1. **Recuperar git**
   - Guardar el `.git` roto como `.git-roto-backup` y poner el de GitHub (los 29 commits están intactos allí).
   - Los 2 ficheros modificados en local son apaños de la demo (el enlace fijo al VY8991 y quitar los vuelos de vuelta). Se descartan, porque la versión de GitHub es mejor y además ese código se va a sustituir.
   - Etiquetar `0bfabde` como `v1.0-dacd` para conservar la entrega del curso.
2. **Limpiar el repo**: arreglar el `.gitignore` (la última línea está rota), quitar `.playwright-mcp/`, `aena_lcg.html`, las `.db` y el `.m2/` local, y decidir qué hacer con `docs/explicacion-clases-*` (material del curso: moverlo a `docs/legacy/` o no subirlo).
3. **Esqueleto nuevo**
   - `backend/` con Spring Boot y Maven wrapper, `frontend/` con Vite.
   - PostgreSQL local vía Homebrew, Flyway y `.env.example`.
   - Tests: JUnit 5 + AssertJ; para la base de datos, PostgreSQL embebido (sin Docker).
4. **Migrar lo valioso del código actual**
   - `AirportMapping` → tabla `team_airport`, actualizada a la temporada 2026/27.
   - `LaligaMatchScraper` → `LaligaFixtureProvider`, probado con un HTML guardado; ciudad y estadio vienen ya en los datos de LaLiga.
   - `ResidentDiscountCalculator` → `ResidentDiscount`.
   - El logo y los colores.
5. **Retirar** los módulos `app`, `domain`, `matches-source`, `flights-source`, `event-store-builder` y `business-unit`. Quedan en el tag.
6. **Añadir** `CLAUDE.md`, el README nuevo y la CI con GitHub Actions (build + tests).

**Hecho cuando:** los tests pasan en CI y `GET /api/matches` devuelve los partidos reales de la UD de esta temporada, sin duplicados, con destino y aeropuerto.

### Fase 1 — Precios de vuelos

**Objetivo:** el corazón del producto.

1. **Prueba de viabilidad (lo primero)**
   - Tú creas la cuenta de Travelpayouts y me pasas el token vía `.env`.
   - Comprobamos si de verdad hay precios para las rutas y fechas reales de los partidos (LPA→LCG, BIO, SDR, OVD…).
   - Si la cobertura es pobre en rutas pequeñas, entra SerpApi como respaldo (250 consultas/mes gratis, reservadas para los 2-3 próximos partidos).
2. Interfaz `FlightPriceProvider` + `TravelpayoutsProvider` (+ `SerpApiProvider` si hace falta).
3. Job programado: cada N horas, para los partidos de los próximos ~60 días, calcula la ventana, consulta precios y guarda `price_snapshot`.
4. Servicio `trips`: mejor ida, mejor vuelta, precio residente y coste total.

**Hecho cuando:** `GET /api/matches/{id}` devuelve vuelos de ida y vuelta reales con precio y enlace, y se va acumulando histórico.

### Fase 2 — Web-app

1. Pantallas de Inicio y Detalle, con gráfica de histórico.
2. Diseño mobile-first con la identidad UD.
3. PWA instalable (manifest, iconos y funcionamiento básico sin conexión).

**Hecho cuando:** se puede usar cómodamente desde el móvil y añadir a la pantalla de inicio.

### Fase 3 — Entradas

1. Investigar la plataforma Onebox de desplazamientos UDLP: si expone eventos por partido y el estado de la venta. Respetar sus condiciones: si no se puede consultar, nos quedamos en el enlace.
2. Enlace por partido (calculado, no fijo) y, si es viable, estado *a la venta / agotado / próximamente* y precio.
3. Coste total del viaje (vuelo + entrada).

### Fase 4 — Despliegue y alertas

1. Desplegar backend, base de datos y frontend (hostings con plan gratuito; se eligen en ese momento porque las condiciones cambian) y dominio propio.
2. **Alertas de bajada de precio**. Recomendado: **bot de Telegram** (fácil, gratis y muy usado por las peñas). Alternativas: email o notificaciones push de la PWA.
3. Aeropuerto de origen configurable, para aficionados de otras islas.

### Fase 5 — Ideas para después

- Alojamiento (afiliado de hoteles) y traslado aeropuerto → estadio.
- "Viaje de peña": compartir un desplazamiento con otros.
- **Generalizar a otros clubes insulares** (CD Tenerife, RCD Mallorca…): tienen exactamente el mismo problema.

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| Travelpayouts sin precios para rutas o fechas poco buscadas (son precios cacheados) | Prueba de viabilidad antes de construir; SerpApi de respaldo; mostrar "precio orientativo" + enlace de búsqueda |
| El HTML de LaLiga cambia y el scraper se rompe | Test con HTML guardado; aviso si se capturan 0 partidos; `FixtureProvider` intercambiable por una API de fútbol |
| Condiciones de uso de Onebox o LaLiga | Revisarlas; ante la duda, enlazar en lugar de extraer datos |
| Estimación de residente inexacta | Siempre etiquetada como estimación; fórmula en un único sitio |
| Cuotas y límites de los planes gratuitos | Cachear en base de datos, consultar solo partidos cercanos, controlar consumo |

## 9. Lo que tienes que hacer tú

Yo no puedo crear cuentas ni introducir credenciales, así que esto te toca a ti:

- Crear la cuenta de **Travelpayouts** (y, si hace falta, **SerpApi**) y poner los tokens en `.env`.
- Instalar PostgreSQL: `brew install postgresql@17` (te guío cuando llegue el momento).
- Más adelante: cuentas de hosting y dominio.
- Opcional: borrar la copia antigua del proyecto en OneDrive cuando esto esté en marcha.
