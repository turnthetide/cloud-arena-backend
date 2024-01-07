source getSquadId.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
curl -i -X PUT "$ca_base/tournaments/$tournamentId/squads/${squadId}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{
    \"playerId\": \"4a26c41e-7c38-4b75-a593-fc1b68cf651a\", 
    \"role\": \"member\"
}"