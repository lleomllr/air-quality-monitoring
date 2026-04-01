#!/bin/bash

# Attend que Kafka Connect soit prêt
until curl -s http://connect:8083/ > /dev/null && nc -z namenode 9000; do
  echo "En attente de Kafka Connect et de Namenode (HDFS)..."
  sleep 5
done

echo "Déploiement des connecteurs..."

# Envois des configurations aux connecteurs
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/hdfs-sink-config.json

curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/timescale-pp-all-sink-config.json

curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/timescale-pp-all-sink-config.json

curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/timescale-sink-config.json

curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-1.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-2.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-3.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-4.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-5.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-6.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-7.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-8.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-9.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-10.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-11.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-12.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-13.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-14.json
sleep 60
curl -X POST http://connect:8083/connectors \
     -H "Content-Type: application/json" \
     -d @/config/rss-source-config-15.json

echo "Connecteurs déployés avec succès."