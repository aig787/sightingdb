SightingDB
========

<div style="text-align:center"><img alt="sighting logo" src="doc/sightingdb-logo3_128.png"/></div>
<br>

SightingDB is a database designed for Sightings, a technique to count items. This is helpful for Threat Intelligence as Sightings allow
to enrich indicators or attributes with Observations, rather than Reputation.

Simply speaking, by pushing data to SightingDB, you will get the first time it was observed, the last time, its count.

However, it will also provide the following features:
* Keep track of how many times something was searched
* Keep track of the hourly statistics per item
* Get the consensus for each item (how many times the same value exists in another namespace)

SightingDB is designed to scale writing and reading.
# Building

1) Make sure JDK 11 or greater is installed
2) Run `./gradlew distTar`

# Running

To run from the source directory run `./gradlew run`. The database can now be accessed at localhost:9990.

# Routes

[V1 Routes](doc/V1.md)


# Authentication

## JWT

[JWT](https://jwt.io/) authentication can be configured by adding the following to `application.conf`

```hocon
ktor {
  jwt {
    issuer = "sightingdb"
    secret = "$SECRET"
    validitySeconds = "3600"
    users = [
      { name = "$USER", password = "$PASSWORD" }
      ...
    ]
  }
}
```

A JWT token can be obtained via the `/login` route 

Request:
```json
{"name": "$USERNAME", "password": ",$PASSWORD"}
```
Response:
```json
{"token": "$TOKEN"}
```

This token can then be used to make authenticated requests via the header
```bash
Authorization: Bearer $TOKEN
``` 

## Basic

Basic authentication can be configured by adding the following to `application.conf`
```hocon
ktor {
  basicAuth.users = [
    { name = "$USER", password = "$PASSWORD" }
  ]
}
```

Authenticated requests can then be made via the header 
```bash
Authorization: Basic $CREDENTIALS
```

# TLS

TLS can be configured by adding the following to `application.conf`

```hocon
ktor{
  deployment {
    sslPort = 9999
    port = null # Optionally disable plaintext listener
  }
  security {
    ssl {
      keyStore = "$KEYSTORE_PATH"
      keyAlias = "$ALIAS"
      keyStorePassword = "$KEYSTORE_PASSWORD"
      privateKeyPassword = "$KEY_PASSWORD"
    }
  }
}
```

Logging
=======

Application logs are written to `/var/log/devo/sightingdb/app.log`. Every sighting is written to `/var/log/devo/sightingdb/commit.log` as a commit log.

Running Tests
=============

Tests can be run including performance tests with `./gradlew test` or without with `NOPERF=1 ./gradlew test`
