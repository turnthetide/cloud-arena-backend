create table ca_tournaments
(
    id           uuid                  not null
        constraint ca_tournaments_pk
            primary key,
    name         text                  not null,
    naf          boolean default false not null,
    inscriptions boolean default false not null,
    variant      text                  not null,
    location     jsonb   default '{}'::jsonb not null,
    start        date                  not null,
    "end"        date                  not null,
    type         text                  not null,
    style        text                  not null,
    rounds       integer               not null,
    squads       boolean               not null,
    note         text    default ''::text not null,
    organizer    uuid                  not null,
    created      timestamptz           not null,
    createdBy    uuid                  not null,
    updated      timestamptz           not null,
    updatedBy    uuid                  not null
);

create table ca_tournaments_inscriptions
(
    id           uuid        not null
        constraint ca_tournaments_inscriptions_pk
            primary key,
    tournamentId uuid        not null,
    playerId     uuid        not null,
    teamName     text        not null,
    teamRace     text        not null,
    nafScore     text        not null,
    substitute   boolean     not null,
    created      timestamptz not null,
    createdBy    uuid        not null,
    updated      timestamptz not null,
    updatedBy    uuid        not null,
    unique (tournamentId, playerId)
);

create table ca_squads
(
    id           uuid        not null
        constraint ca_squads_pk
            primary key,
    tournamentId uuid        not null,
    name         text        not null,
    created      timestamptz not null,
    createdBy    uuid        not null,
    updated      timestamptz not null,
    updatedBy    uuid        not null,
    unique (tournamentId, name)
);

create table ca_squads_members
(
    id        uuid        not null
        constraint ca_squads_members_pk
            primary key,
    squadId   uuid        not null,
    playerId  uuid        not null,
    role      text        not null,
    created   timestamptz not null,
    createdBy uuid        not null,
    updated   timestamptz not null,
    updatedBy uuid        not null,
    unique (squadId, playerId)
);

create table ca_rounds
(
    id           uuid        not null
        constraint ca_rounds_pk
            primary key,
    tournamentId uuid        not null,
    round        int         not null,
    created      timestamptz not null,
    createdBy    uuid        not null,
    updated      timestamptz not null,
    updatedBy    uuid        not null,
    unique (tournamentId, number)
);

create table ca_teams_standings
(
    id        uuid        not null
        constraint ca_teams_standings_pk
            primary key,
    roundId   uuid        not null,
    prize     text        not null,
    squadId   uuid        not null,
    points    int         not null,
    tiebreak  int[] not null,
    position  int         not null,
    created   timestamptz not null,
    createdBy uuid        not null,
    updated   timestamptz not null,
    updatedBy uuid        not null,
    unique (roundId, prize, teamId)
);
