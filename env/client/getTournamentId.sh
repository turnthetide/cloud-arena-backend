source token.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
tournaments=$(curl "$ca_base/tournaments" \
    -H "Authorization: Bearer ${token}")

tournamentId=$(echo $tournaments | jq -r '.[0].id')

echo ""
echo "Tournament: $tournamentId"
echo ""