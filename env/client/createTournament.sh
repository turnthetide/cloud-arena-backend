source token.sh

#curl -iv "$ca_base/me" -H "Authorization: Bearer ${token}"
curl -i "$ca_base/tournaments" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d '{
  "name": "Tournament 1",
  "settings": {
    "variant": "BloodBowl2020",
    "location": {
      "address1": "Via della Pace 2",
      "address2": "",
      "city": "Carpi",
      "state": "MO",
      "zip": "41012",
      "nation": "Italy"
    },
    "start": "2023-12-30",
    "end": "2023-12-31",
    "type": "Open",
    "style": "Swiss",
    "rounds": 6,
    "squads": true,
    "note": "note"
  }
}'