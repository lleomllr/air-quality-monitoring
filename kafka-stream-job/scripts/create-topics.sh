#!/bin/bash
cub kafka-ready -b kafka:9092 1 20

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic rss-raw \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic pre-processed-ozone \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic pre-processed-pm25 \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic pre-processed-pm10 \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic alerts \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic pre-processed-all \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic connect-configs \
  --replication-factor 1 \
  --partitions 1 \
  --config cleanup.policy=compact \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic connect-offsets \
  --replication-factor 1 \
  --partitions 1 \
  --config cleanup.policy=compact \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic connect-status \
  --replication-factor 1 \
  --partitions 1 \
  --config cleanup.policy=compact \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic avg-state-hour \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic avg-city-hour \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic avg-state-8-hour \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic avg-city-8-hour \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic avg-state-day \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic avg-city-day \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic max-state-hour \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic max-city-hour \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic global-top10-aqi-events-v2 \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic aqi-anomaly-alerts-emazscore-v3 \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists

kafka-topics \
  --bootstrap-server kafka:9092 \
  --topic aqi-forecast-alerts \
  --replication-factor 1 \
  --partitions 1 \
  --create --if-not-exists