#!/usr/bin/env bash

docker stop test-dokgen; docker rm test-dokgen
mvn -B -Dfile.encoding=UTF-8 -DinstallAtEnd=true -DdeployAtEnd=true  -DskipTests clean install
docker build -t navikt/dokgen .
docker run -p 7281:8080 -d --name test-dokgen navikt/dokgen