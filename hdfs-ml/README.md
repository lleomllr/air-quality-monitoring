Commandes à suivre si vous souhaitez exécuter des algorithmes :

docker exec namenode hdfs dfs -chmod -R 777 /historical-data   

docker cp ./hdfs-ml/processing/clean.py spark-master:/opt/spark/work-dir/ 
docker exec spark-master /opt/spark/bin/spark-submit --master spark://spark-master:7077 /opt/spark/work-dir/clean.py

Puis : 

docker cp ./hdfs-ml/ml/jobs/anomaly_detection.py spark-master:/opt/spark/work-dir/ 
docker exec spark-master /opt/spark/bin/spark-submit --master spark://spark-master:7077 --executor-memory 4G /opt/spark/work-dir/anomaly_detection.py