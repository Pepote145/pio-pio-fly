# PioPio Fly ✈️🐥

**Vuelo y entrada para seguir a la UD Las Palmas fuera de casa.**

PioPio Fly reúne en una sola web-app lo que necesita el aficionado amarillo para viajar con el equipo: el calendario de partidos fuera de casa, el vuelo más barato desde Gran Canaria (con el precio estimado de residente canario) y la entrada oficial de desplazamientos.

> Estado: **fase 0** (base del proyecto). El plan completo está en [docs/ROADMAP.md](docs/ROADMAP.md).
> La versión original, entregada como proyecto de la asignatura DACD (ULPGC), está en el tag [`v1.0-dacd`](../../tree/v1.0-dacd).

## Qué hace ya

- Sincroniza el calendario de la UD desde laliga.com al arrancar y cada 6 horas, sin duplicar partidos.
- Distingue los partidos con **fecha provisional** (solo se conoce la jornada) de los que tienen **horario confirmado**.
- Asocia cada rival con sus aeropuertos de destino, del recomendado a las alternativas.
- API REST `GET /api/matches` y una primera pantalla con los próximos partidos fuera.

## Estructura

```text
backend/    Spring Boot 4 (Java 25) + PostgreSQL + Flyway
frontend/   React + Vite + TypeScript
docs/       Hoja de ruta y material de la versión del curso (docs/legacy)
```

## Arrancar en local

Requisitos: Java 25, Node 22 y PostgreSQL 17.

```bash
# 1. PostgreSQL (una sola vez)
brew install postgresql@17
brew services start postgresql@17
createuser piopiofly --pwprompt   # contraseña: piopiofly (o la que pongas en backend/.env)
createdb piopiofly --owner piopiofly

# 2. Backend → http://localhost:8080
cd backend
cp .env.example .env               # solo si quieres cambiar los valores por defecto
./mvnw spring-boot:run

# 3. Frontend → http://localhost:5173
cd frontend
npm install
npm run dev
```

## Tests

```bash
cd backend && ./mvnw verify               # usa PostgreSQL embebido: no necesita la base de datos local
cd frontend && npm run lint && npm run build
```

## API

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/matches` | Próximos partidos fuera de casa con sus aeropuertos de destino |
| GET | `/actuator/health` | Estado del servicio |
