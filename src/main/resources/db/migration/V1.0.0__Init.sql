create table ca_tournaments
(
    id        uuid                        not null
        constraint ca_tournaments_pk
            primary key,
    name      text                        not null,
    naf       boolean default false       not null,
    variant   text                        not null,
    location  jsonb   default '{}'::jsonb not null,
    start     date                        not null,
    "end"     date                        not null,
    type      text                        not null,
    style     text                        not null,
    rounds    integer                     not null,
    squads    boolean                     not null,
    note      text    default ''::text    not null,
    organizer uuid                        not null
);
