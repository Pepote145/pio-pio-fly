CREATE TABLE IF NOT EXISTS away_matches_datamart (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT,
    competition TEXT,
    home_team TEXT,
    away_team TEXT,
    match_date TEXT,
    city TEXT,
    stadium TEXT,
    destination_airport TEXT,
    source TEXT,
    captured_at TEXT
);

CREATE TABLE IF NOT EXISTS flight_infos_datamart (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    flight_number TEXT,
    airline TEXT,
    origin_airport TEXT,
    destination_airport TEXT,
    scheduled_datetime TEXT,
    status TEXT,
    terminal TEXT,
    source TEXT,
    captured_at TEXT
);

INSERT INTO away_matches_datamart (
    external_id,
    competition,
    home_team,
    away_team,
    match_date,
    city,
    stadium,
    destination_airport,
    source,
    captured_at
) VALUES (
    'laliga-rcd-udlp-20260531',
    'LALIGA HYPERMOTION',
    'RC Deportivo',
    'UD Las Palmas',
    '2026-05-31T00:00',
    'A Coruña',
    'ABANCA-RIAZOR',
    'LCG',
    'laliga.com',
    '2026-05-20T01:50:00'
);

INSERT INTO flight_infos_datamart (
    flight_number,
    airline,
    origin_airport,
    destination_airport,
    scheduled_datetime,
    status,
    terminal,
    source,
    captured_at
) VALUES (
    'VY8991',
    'Vueling',
    'LPA',
    'LCG',
    '2026-05-30T06:45',
    NULL,
    NULL,
    'aena.es',
    '2026-05-20T02:51:43.918858'
);
