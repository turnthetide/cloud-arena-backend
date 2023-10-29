source <(cat .env)
token=$(curl -d "grant_type=password&username=teg&password=teg&client_id=arena-backend&client_secret=aI4udjpn5GmSc23GsQSglVL2TpjFOdZM&scope=openid" -sL \
     --url "$kc_base/realms/arena/protocol/openid-connect/token" \
      | jq -r '.access_token')
echo $token
