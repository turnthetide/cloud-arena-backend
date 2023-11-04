drop table ca_schema_history ; drop table ca_tournaments; drop table ca_tournaments_inscriptions ;

select *
from pg_indexes
where tablename not like 'pg%';