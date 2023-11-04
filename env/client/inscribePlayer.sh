source getTournamentId.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
curl -i "$ca_base/tournaments/$tournamentId/players" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{
  \"playerId\": \"${playerId}\",
  \"teamName\": \"Full Metal Dwarves\",
  \"teamRace\": \"Dwarves\"
}"