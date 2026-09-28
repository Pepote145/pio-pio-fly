-- Aeropuertos a los que volar para jugar en el campo de cada equipo.
-- team_slug es el identificador del equipo en laliga.com. priority 1 = aeropuerto recomendado.
create table team_airport (
    id           bigserial primary key,
    team_slug    varchar(80)  not null,
    airport_iata varchar(3)   not null,
    priority     integer      not null,
    note         varchar(200),
    constraint uq_team_airport unique (team_slug, airport_iata)
);

-- Rivales de LALIGA HYPERMOTION 2026/27
insert into team_airport (team_slug, airport_iata, priority, note) values
    ('ad-ceuta-fc',     'AGP', 1, 'Después, barco de Algeciras a Ceuta'),
    ('albacete-bp',     'MAD', 1, 'Después, tren o carretera hasta Albacete'),
    ('albacete-bp',     'VLC', 2, 'Después, carretera hasta Albacete'),
    ('albacete-bp',     'ALC', 3, 'Después, carretera hasta Albacete'),
    ('burgos',          'BIO', 1, 'Después, carretera hasta Burgos'),
    ('burgos',          'MAD', 2, 'Después, tren o carretera hasta Burgos'),
    ('cadiz-cf',        'XRY', 1, null),
    ('cadiz-cf',        'SVQ', 2, 'Después, tren hasta Cádiz'),
    ('cd-castellon',    'VLC', 1, 'Después, tren o carretera hasta Castellón'),
    ('cd-castellon',    'CDT', 2, null),
    ('cd-leganes',      'MAD', 1, null),
    ('cd-tenerife',     'TFN', 1, 'También hay barco desde Gran Canaria'),
    ('cd-tenerife',     'TFS', 2, null),
    ('ce-sabadell',     'BCN', 1, null),
    ('cordoba-cf',      'SVQ', 1, 'Después, tren hasta Córdoba'),
    ('cordoba-cf',      'AGP', 2, 'Después, tren hasta Córdoba'),
    ('cordoba-cf',      'MAD', 3, 'Después, AVE hasta Córdoba'),
    ('eldense',         'ALC', 1, 'Después, carretera hasta Elda'),
    ('fc-andorra',      'BCN', 1, 'Después, carretera hasta Encamp'),
    ('girona-fc',       'GRO', 1, null),
    ('girona-fc',       'BCN', 2, 'Después, tren hasta Girona'),
    ('granada-cf',      'GRX', 1, null),
    ('granada-cf',      'AGP', 2, 'Después, carretera hasta Granada'),
    ('r-sociedad-b',    'EAS', 1, 'El filial juega en Zubieta'),
    ('r-sociedad-b',    'BIO', 2, 'Después, carretera hasta Zubieta'),
    ('r-valladolid-cf', 'VLL', 1, null),
    ('r-valladolid-cf', 'MAD', 2, 'Después, tren hasta Valladolid'),
    ('rc-celta-b',      'VGO', 1, null),
    ('rc-celta-b',      'SCQ', 2, 'Después, tren hasta Vigo'),
    ('rcd-mallorca',    'PMI', 1, null),
    ('real-oviedo',     'OVD', 1, null),
    ('real-sporting',   'OVD', 1, 'Después, carretera hasta Gijón'),
    ('sd-eibar',        'BIO', 1, 'Después, carretera hasta Eibar'),
    ('ud-almeria',      'LEI', 1, null);
