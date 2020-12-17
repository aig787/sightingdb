#!/bin/bash
rm -rf keys/
mkdir -p keys/
keytool -genkeypair -keystore keys/keystore.p12 -storetype PKCS12 -storepass changeit -alias sightingdb -keyalg RSA -keysize 2048 -validity 99999 -dname "CN=SightingDB, OU=Devo, O=Devo, L=Seattle, ST=WA, C=US" -ext san=dns:localhost,ip:127.0.0.1
keytool -exportcert -keystore keys/keystore.p12 -storepass changeit -alias sightingdb -rfc -file keys/public-certificate.pem
openssl pkcs12 -in keys/keystore.p12 -password pass:changeit -nodes -nocerts -out keys/private-key.key
openssl rsa -in keys/private-key.key -pubout > keys/public-key.pub
