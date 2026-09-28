# PioPio Fly — Hoja de ruta

> Última revisión: 28-sep-2026 (ajustes previos a la fase 1). Documento vivo: se actualiza al cerrar cada fase.

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
| Precios de vuelos | **Travelpayouts (Aviasales Data API)**, *sujeto a la prueba de cobertura de la fase 1*; alternativas: **SerpApi Google Flights** y **Duffel** | Gratis y pensado para "el más barato"; comisión de afiliado. Amadeus Self-Service cerró el 17-jul-2026 y Kiwi Tequila solo admite partners |
| Precio mostrado | Siempre **orientativo**, con la fecha en que se obtuvo | Los precios de Travelpayouts son cacheados; el precio real se confirma en la web de la aerolínea |
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
- **Configuración y claves en variables de entorno** (`backend/.env`, nunca en git). Verificado: `.gitignore` excluye `.env` y `.env.*` y solo deja subir `.env.example`.
- **Una fuente que falla nunca borra datos**: se conserva lo último bueno y se avisa.
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
| `team_airport` ✅ | slug del rival en laliga.com, aeropuerto IATA, prioridad, nota | Sustituye a `AirportMapping`. Varios aeropuertos por rival; datos de temporada en migración Flyway |
| `airport` *(fase 1)* | IATA, nombre, ciudad, lat/lon | Permite calcular la **distancia de cada aeropuerto al estadio** con las coordenadas del estadio que da LaLiga |
| `price_snapshot` | partido, sentido (IDA/VUELTA), origen, destino, fecha de vuelo, precio €, aerolínea, escalas, proveedor, enlace, `found_at` (cuándo lo obtuvo el proveedor), `captured_at` (cuándo lo guardamos) | Histórico → gráfica y alertas. `found_at` es la fecha que se enseña junto al precio orientativo |
| `fixture_sync_run` *(fase 1)* | fecha, fuente, partidos recibidos, partidos fuera próximos, resultado (OK / AVISO / ERROR), mensaje | Registro de cada sincronización con LaLiga, para detectar respuestas anómalas |
| `ticket_info` *(fase 3)* | partido, URL, estado, precio desde, `checked_at` | |
| `alert_subscription` *(fase 4)* | contacto, partido o "todos", umbral € | |

### Aeropuertos por rival

- Cada rival puede tener **varios aeropuertos** (ya soportado en `team_airport`), por ejemplo Córdoba → Sevilla (SVQ) y Málaga (AGP), o Albacete → MAD, VLC y ALC.
- Con las coordenadas del estadio (LaLiga) y las del aeropuerto (tabla `airport`) se calcula la **distancia en línea recta**, que se muestra junto a cada opción. Más adelante se podría sustituir por el tiempo real de traslado.
- La búsqueda de precios se hace en **todos** los aeropuertos del rival, no solo en el principal. Para cada partido se muestra la mejor opción por aeropuerto, y la recomendación combina precio y distancia.

### Lógica de la ventana de viaje

Primero se calculan los **días posibles del partido** y, a partir de ellos, la ventana de búsqueda de vuelos.

- **Fecha confirmada**: el día posible es el del partido. Ida entre D-2 y D (el mismo día solo si el partido empieza por la tarde o noche); vuelta entre D (si hay vuelo después del partido) y D+1.
- **Fecha provisional**: LaLiga solo publica la jornada y fija el día y la hora unas semanas antes. Los días posibles se calculan a partir de la fecha de la jornada:
  - **jornada de fin de semana** (fecha de referencia en sábado o domingo): de viernes a lunes;
  - **jornada entre semana** (fecha de referencia de martes a jueves): de martes a jueves.

  La ventana de ida va desde el día anterior al primer día posible hasta el último día posible; la de vuelta, desde el primer día posible hasta el día siguiente al último. La web lo indica claramente ("fecha por confirmar: buscamos vuelos del X al Y").
- **A verificar en la fase 1**: en el calendario 2026/27 actual todas las fechas de referencia caen en domingo. Hay que confirmar cómo publica LaLiga las jornadas entre semana (qué día trae `gameweek.date`) y ajustar la regla con datos reales, cubierta con tests.
- Parámetros configurables, nunca números mágicos repartidos por el código.

### Precio de residente

- **Es siempre una estimación** y así se muestra: **"≈ X € residente (estimado)"**.
- **Sobre qué se aplica**: el 75% se descuenta solo de la **tarifa bonificable** (el precio base del billete). **No** se descuentan las **tasas aeroportuarias**, los **cargos de gestión o emisión** ni los **servicios adicionales** (equipaje facturado, selección de asiento…). Aplica a vuelos directos o con escala en España, entre Canarias y el resto del país y entre islas; no aplica si el itinerario tiene tramos internacionales.
- **Consecuencia para el cálculo**: los proveedores dan el precio total, con las tasas incluidas. El `ResidentDiscount` actual aplica el 75% sobre el total y **sobreestima el ahorro**. En la fase 1 se cambia a `residente ≈ 0,25 × (total − tasas estimadas) + tasas estimadas`, con las tasas estimadas por aeropuerto o, si no se conocen, con un valor por defecto configurable. Si el proveedor da el desglose, se usa el desglose.
- La fórmula va en un único sitio (`ResidentDiscount`), con tests, y la nota de la web explica qué incluye.

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
   - Guardar el `.git` roto como copia de seguridad fuera del repo y poner el de GitHub (los 29 commits están intactos allí).
   - Los 2 ficheros con apaños de la demo se guardaron en un commit y después se retiraron junto con los módulos del curso.
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

1. **Prueba de cobertura de Travelpayouts, con criterio de sí o no (lo primero)**
   - Tú creas la cuenta y pones el token en `backend/.env`.
   - **Qué se mide.** Los 17 partidos fuera de la temporada: 14 rutas distintas con el aeropuerto principal, más las alternativas. Ida desde LPA y vuelta a LPA, dentro de la ventana de cada partido. Por cada partido y sentido se registra:
     - si hay precio (sí o no) y en qué aeropuerto;
     - a cuántas semanas vista está el partido;
     - la antigüedad del precio (ahora − `found_at`).
   - **Cómo.** Un script de medición, fuera de la app, que se lanza **3 días distintos** en una semana para no juzgar por un día suelto. Los resultados van a `docs/prueba-travelpayouts.md`, con una tabla por partido y un resumen.
   - **Criterio de SÍ** (se tienen que cumplir las tres):
     1. **Cobertura**: ≥ 70% de los partidos a 8 semanas vista o menos tienen precio de ida **y** de vuelta en al menos un aeropuerto.
     2. **Horizonte**: hay precios al menos para partidos a 6 semanas vista.
     3. **Frescura**: antigüedad mediana ≤ 72 h y percentil 90 ≤ 7 días.
   - **Si NO se cumple** (o se cumple a medias), **antes de empezar la fase 2** se evalúan las alternativas con la misma medición:
     - **SerpApi (Google Flights)**: precios en tiempo real, con 250 consultas/mes gratis. Hay que calcular si alcanza para los partidos próximos × aeropuertos × días de ventana, o si compensa el plan de pago.
     - **Duffel**: ofertas en tiempo real de más de 300 aerolíneas. El modo de pruebas usa datos ficticios; en producción cobra por búsqueda si se supera la proporción de 1.500 búsquedas por reserva.
     - **Mixto**: Travelpayouts donde tenga cobertura y la alternativa para los huecos.

     La decisión queda escrita en este documento.
2. **Sincronización de LaLiga vigilada**
   - Registrar cada sincronización en `fixture_sync_run`.
   - **Avisar** si LaLiga devuelve **0 partidos**, o **bastantes menos de los esperados**. Esperados = los partidos fuera próximos de la sincronización anterior, menos los que ya se han jugado desde entonces. El umbral es configurable (por ejemplo, menos del 70%).
   - **Nunca se borran ni se modifican partidos** porque falten en una respuesta: se conserva lo último bueno. El aviso va al log (WARN) y a `/actuator/health`; en la fase 4, también a Telegram.
3. **Aeropuertos con distancia**: tabla `airport` con coordenadas, distancia de cada aeropuerto al estadio y búsqueda en todos los aeropuertos del rival.
4. **Ventana de viaje**: días posibles del partido (confirmado, jornada de fin de semana o entre semana) → ventana de ida y de vuelta, con tests.
5. **Precio de residente**: corregir `ResidentDiscount` para que excluya las tasas estimadas (ver sección 4).
6. Interfaz `FlightPriceProvider` + la implementación que salga de la prueba.
7. Job programado: cada N horas, para los partidos de los próximos ~60 días, calcula la ventana, consulta precios en todos los aeropuertos y guarda `price_snapshot`.
8. Servicio `trips`: mejor ida y mejor vuelta por aeropuerto, precio de residente estimado y coste total.

**Hecho cuando:** `GET /api/matches/{id}` devuelve vuelos de ida y vuelta reales, por aeropuerto, con precio orientativo, fecha de obtención, enlace y estimación de residente; se va acumulando histórico, y la decisión sobre el proveedor de precios está documentada.

### Fase 2 — Web-app

1. Pantallas de Inicio y Detalle, con gráfica de histórico.
2. Diseño mobile-first con la identidad UD.
3. PWA instalable (manifest, iconos y funcionamiento básico sin conexión).

**Hecho cuando:** se puede usar cómodamente desde el móvil y añadir a la pantalla de inicio.

### Fase 3 — Entradas

1. **Verificar primero por qué canal compra la entrada un aficionado visitante.** Hay dos vías posibles, y no tienen por qué estar ambas disponibles en todos los partidos:
   - el **cupo de visitante de la UD** (plataforma de desplazamientos UDLP en Onebox): quién puede comprar (socios, peñas, público general), cuándo se abre la venta y cuántas entradas hay;
   - la **venta del club local** en su propia ticketera: si admite visitantes, en qué zonas y con qué restricciones.

   Se documenta por partido (o por rival, si el patrón se repite) antes de construir nada. El canal recomendado en la app sale de aquí.
2. Revisar las condiciones de uso de cada plataforma: si no se pueden consultar datos, nos quedamos en el enlace.
3. Enlace por partido al canal que corresponda (calculado, nunca fijo) y, si es viable, estado *a la venta / agotado / próximamente* y precio.
4. Coste total del viaje (vuelo + entrada).

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
| Travelpayouts sin precios para rutas o fechas poco buscadas (son precios cacheados) | Prueba con criterio de sí o no antes de construir; alternativas SerpApi y Duffel evaluadas antes de la fase 2; precio siempre orientativo con su fecha de obtención y enlace de búsqueda |
| El formato de LaLiga cambia y el scraper se rompe | Test con HTML guardado; aviso si llegan 0 partidos o bastantes menos de lo esperado; nunca se borran datos; `FixtureProvider` intercambiable por una API de fútbol |
| Jornadas entre semana mal interpretadas | Regla de ventana cubierta con tests y verificada con datos reales de LaLiga en cuanto aparezca una |
| Condiciones de uso de Onebox, de las ticketeras o de LaLiga | Revisarlas; ante la duda, enlazar en lugar de extraer datos |
| Estimación de residente inexacta (el descuento no se aplica a las tasas) | Siempre etiquetada como estimación; se descuentan las tasas estimadas; fórmula en un único sitio con tests |
| Entrada no disponible por el canal enlazado | Verificar el canal de compra del visitante antes de construir la fase 3 |
| Cuotas y límites de los planes gratuitos | Cachear en base de datos, consultar solo partidos cercanos, controlar consumo |

## 9. Lo que tienes que hacer tú

Yo no puedo crear cuentas ni introducir credenciales, así que esto te toca a ti:

- Crear la cuenta de **Travelpayouts** (y, si hace falta, **SerpApi**) y poner los tokens en `.env`.
- Instalar PostgreSQL: `brew install postgresql@17` (te guío cuando llegue el momento).
- Más adelante: cuentas de hosting y dominio.
- Opcional: borrar la copia antigua del proyecto en OneDrive cuando esto esté en marcha.
