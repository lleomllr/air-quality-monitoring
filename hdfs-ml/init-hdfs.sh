#!/bin/bash
set -e

NN_DIR=/hadoop/dfs/name

if [ ! -f "$NN_DIR/current/VERSION" ]; then
    echo "Formatage de HDFS NameNode..."
    hdfs namenode -format -force -nonInteractive
fi

hdfs namenode &

echo "Démarrage de HDFS NameNode..."
until hdfs dfs -ls / >/dev/null 2>&1; do
    sleep 3
done

echo "HDFS prêt, initialisation de l'utilisateur et des répertoires..."

groupadd -f supergroup
useradd -m -g supergroup appuser
hdfs dfs -mkdir -p /topics/pre-processed-all
hdfs dfs -chown -R appuser:supergroup /topics/pre-processed-all
hdfs dfs -chmod -R 775 /topics/pre-processed-all
hdfs dfs -mkdir -p /historical-data
hdfs dfs -chown -R appuser:supergroup /historical-data
hdfs dfs -chmod -R 775 /historical-data

echo "En attente que les DataNodes soient prêts..."

until hdfs dfsadmin -report 2>/dev/null | grep -q "Live datanodes ("; do
  sleep 5
done

echo "Insertion des données historiques dans HDFS..."

hdfs dfs -put /data/AirNow_2024_spark.parquet /historical-data/
hdfs dfs -put /data/AirNow_2025_spark.parquet /historical-data/

echo "initialisation de HDFS réussie."

hdfs --daemon stop namenode
exec hdfs namenode