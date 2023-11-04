source getTournamentId.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
curl -i -X PUT "$ca_base/tournaments/$tournamentId/naf" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d '{"official": true}'