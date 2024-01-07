source getTournamentId.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
curl -i -X POST "$ca_base/tournaments/$tournamentId/rounds/squads" \
    -H "Authorization: Bearer ${token}"