source getTournamentId.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
squads=$(curl "$ca_base/tournaments/${tournamentId}/squads" \
    -H "Authorization: Bearer ${token}")

squadId=$(echo $squads | jq -r '.[0].id')

echo ""
echo "Squad: $squadId"
echo ""