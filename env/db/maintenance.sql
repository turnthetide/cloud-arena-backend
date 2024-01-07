drop table ca_schema_history;
drop table ca_tournaments;
drop table ca_tournaments_inscriptions;
drop table ca_squads;
drop table ca_squads_players;

select *
from pg_indexes
where tablename not like 'pg%';

select s.id, s.name, pi.id, pi.teamName, pi.teamRace, pi.nafScore, sp.role