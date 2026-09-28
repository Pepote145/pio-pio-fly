# PioPio Fly

Web-app para aficionados de la UD Las Palmas: partidos fuera de casa + vuelo más barato desde Gran Canaria (con precio estimado de residente) + entrada oficial. Proyecto personal; el plan por fases está en `docs/ROADMAP.md` y hay que mantenerlo al día al cerrar cada fase.

## Estructura

- `backend/`: Spring Boot 4.1, Java 25, PostgreSQL + Flyway, JPA. Paquetes por funcionalidad bajo `com.piopiofly` (`matches`, `airports`, `flights`, `config`…).
- `frontend/`: React 19 + Vite + TypeScript. En desarrollo, `/api` se redirige al backend en `:8080`.
- `docs/legacy/`: material de la versión del curso (tag `v1.0-dacd`). No se toca.

## Comandos

- Tests del backend: `cd backend && ./mvnw verify`. Usan PostgreSQL embebido (zonky), sin Docker ni base de datos local.
- Frontend: `cd frontend && npm run lint && npm run build`.
- Arrancar: ver README.

## Convenciones

- Código, comentarios, mensajes de log y commits en **español**.
- Cada fuente externa va detrás de una interfaz (`FixtureProvider`, y en la fase 1 `FlightPriceProvider`). Los errores de una fuente se lanzan como excepción propia y **nunca** se sustituyen por datos inventados.
- Nada de datos de demo en el código: ni URLs fijas, ni fechas, ni aeropuertos concretos en la lógica. Los datos de referencia van en migraciones Flyway.
- El esquema solo cambia con migraciones nuevas (`V<n>__descripcion.sql`); nunca se editan las ya aplicadas. Hibernate está en `ddl-auto=validate`.
- Configuración en `application.properties` con `PioPioFlyProperties`; los secretos en `backend/.env` (plantilla en `.env.example`), nunca en git.
- "Hoy" se calcula con el `Clock` inyectado (zona `Atlantic/Canary`) para que los tests puedan fijar la fecha.
- Tests: unitarios para parseo y lógica; `@IntegrationTest` (app completa + PostgreSQL embebido) para repositorios y API. Los scrapers se prueban contra HTML guardado en `src/test/resources`, nunca contra la web real.
- El precio de residente siempre se muestra como estimación (`ResidentDiscount`).

## Datos de LaLiga

`LaligaFixtureProvider` lee el JSON `__NEXT_DATA__` de la página de próximos partidos. `time == null` significa que la fecha es provisional (solo se conoce la jornada). Los rivales se identifican por su slug de laliga.com, que es la clave de `team_airport`.
