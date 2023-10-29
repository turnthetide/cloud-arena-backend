source token.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
curl -i "$ca_base/players/${playerId}" \
    -H "Authorization: Bearer ${token}"