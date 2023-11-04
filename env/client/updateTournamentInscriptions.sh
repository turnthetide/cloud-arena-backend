source getTournamentId.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
curl -i -X PUT "$ca_base/tournaments/$tournamentId/inscriptions" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d '{"open": true}'