-- Partidos de la UD Las Palmas fuera de casa.
-- match_date es siempre conocida (día de la jornada); kickoff_at solo cuando LaLiga confirma el horario.
create table away_match (
    id             bigserial primary key,
    provider       varchar(40)  not null,
    external_id    varchar(80)  not null,
    competition    varchar(120) not null,
    season         varchar(20),
    gameweek       integer,
    home_team_slug varchar(80)  not null,
    home_team_name varchar(120) not null,
    match_date     date         not null,
    kickoff_at     timestamptz,
    stadium        varchar(160),
    city           varchar(120),
    latitude       double precision,
    longitude      double precision,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    constraint uq_away_match_provider_external unique (provider, external_id)
);

create index idx_away_match_date on away_match (match_date);
