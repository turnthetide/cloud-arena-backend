source getTournamentId.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
curl -i "$ca_base/tournaments/$tournamentId/players" \
    -H "Authorization: Bearer ${token}"