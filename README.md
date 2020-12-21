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

Building
========

1) Make sure JDK 11 or greater is installed
2) Run `./gradlew distTar`

Running
=======

To run from the source directory run `./gradlew run`. The database can now be accessed at localhost:9990.

Client Demo
===========

Writing
-------
```bash
$ curl http://localhost:9990/w/my/namespace/?val=127.0.0.1
{"count":1,"message":"ok"}	

$ curl http://localhost:9990/w/another/namespace/?val=127.0.0.1
{"count":1,"message":"ok"}	

$ curl http://localhost:9990/w/another/namespace/?val=127.0.0.1
{"count":1,"message":"ok"}	

# Bulk writes are JSON objects of namespace, value, and optionally a timestamp as epoch milliseconds
$ curl -X POST -H "Content-Type: application/json" -d '{"items": [{"namespace": "/a/namespace", "value": "127.0.0.1", "timespamp": 0}, {"namespace": "/a/namespace", "value": "127.0.0.2"}]}' http://localhost:9990/wb
{"count":2,"message":"ok"}
```

Reading
-------
```bash
$ curl -k 'http://localhost:9990/r/another/namespace/?val=127.0.0.1'
{
  "value": "127.0.0.1",
  "first_seen": "2020-12-18T11:20:24.677428",
  "last_seen": "2020-12-18T11:20:24.677428",
  "consensus": 4,
  "count": "1",
  "tags": {},
  "ttl": 0
}

$ curl -k 'http://localhost:9990/r/another/namespace/?val=127.0.0.1'
{
  "value": "127.0.0.1",
  "first_seen": "2020-12-18T11:20:24.677428",
  "last_seen": "2020-12-18T11:20:24.677428",
  "consensus": 4,
  "count": "1",
  "tags": {},
  "ttl": 0,
  "stats": {
    "2020-12-18T11:00": 1
  }
}

$ curl -X POST -H "Content-Type: application/json" -d '{"items": [{"namespace": "/a/namespace", "value": "127.0.0.1"}, {"namespace": "/a/namespace", "value": "127.0.0.2"}]}' http://localhost:9990/rb
{
  "items": [
    {
      "value": "127.0.0.1",
      "first_seen": "2020-12-18T11:13:31.723596",
      "last_seen": "2020-12-18T11:14:31.890442",
      "consensus": 3,
      "count": "3",
      "tags": {},
      "ttl": 0
    },
    {
      "value": "127.0.0.2",
      "first_seen": "2020-12-18T11:13:31.736353",
      "last_seen": "2020-12-18T11:14:31.891164",
      "consensus": 3,
      "count": "3",
      "tags": {},
      "ttl": 0
    }
  ]
}

```

Authentication
==============

[JWT](https://jwt.io/) authentication can be configured by adding the following to `application.conf`

```hocon
ktor {
  jwt {
    issuer = "sightingdb"
    secret = "$SECRET"
    validitySeconds = "3600"
    users = [
      { name = "$USER", password: "$PASSWORD" }
      ...
    ]
  }
}
```

A JWT token can be obtained via the `/login` route 
```bash
$ curl -X POST -H "Content-Type: application/json" -d '{"name": "test", "password": "test"}' http://localhost:9990/login
{
  "token": "$TOKEN"
}
```

And used to make authenticated requests 
```bash
$ curl -k -H "Authorization: Bearer $TOKEN" 'http://localhost:9990/w/another/namespace/?val=127.0.0.1'
{
  "count": 1,
  "message": "ok"
}

```

TLS
===

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

REST Endpoints
==============
	/w: write (GET)
	/wb: write in bulk mode (POST)
	/r: read (GET)
	/rs: read with statistics (GET)
	/rb: read in bulk mode (POST)
	/rbs: read with statistics in bulk mode (POST)
	/d: delete (GET)
	/c: configure (GET)
	/i: info (GET)

Logging
=======

Application logs are written to `/var/log/devo/sightingdb/app.log`. Every sighting is written to `/var/log/devo/sightingdb/commit.log` as a commit log.

Running Tests
=============

Tests can be run including performance tests with `./gradlew test` or without with `NOPERF=1 ./gradlew test`
