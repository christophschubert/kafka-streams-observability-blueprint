#!/usr/bin/env bash

docker compose up -d

echo "Waiting for broker to start up"
sleep 20 # to prevent console producer throwing too many errors while Kafka is starting up

kafka-console-producer --topic input --bootstrap-server localhost:9093 < sample-data

docker compose restart word-count-app
docker compose ps

echo "Waiting for streams app to start"
sleep 3

echo "Kafka streams health: $(curl -s http://localhost:7171/health)"
echo "Kafka streams topology:" && curl -s http://localhost:7171/topology
echo "Kafka streams metrics (sample):" && curl -s http://localhost:7070/metrics | head -25

echo "Kafka topics" && kafka-topics --list --bootstrap-server localhost:9093
