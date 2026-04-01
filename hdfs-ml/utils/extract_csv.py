import pyspark.sql.functions as F
from pyspark.sql import SparkSession

spark = SparkSession.builder.appName("Extract-CA-LSTM").getOrCreate()
spark.sparkContext.setLogLevel("ERROR")

INPUTS = [
    "hdfs://namenode:9000/historical-data/AirNow_2024_cleaned.parquet",
    "hdfs://namenode:9000/historical-data/AirNow_2025_cleaned.parquet"
]
df = spark.read.parquet(*INPUTS)

# 1. Filtre sur toute la Californie (PM2.5)
df_ca = df.filter(
    (F.col("Parameter") == "PM2.5") & 
    (F.col("StateCode") == "CA")
)

# 2. Trouver les 20 capteurs les PLUS FIABLES de Californie
# (Ceux qui ont le plus de lignes, donc le moins de trous horaires)
top_sensors_ca = (
    df_ca.groupBy("SiteID")
    .agg(F.count("Value").alias("nb_mesures"))
    .orderBy(F.col("nb_mesures").desc()) # On trie par fiabilité
    .limit(20) 
    .select("SiteID")
    .rdd.flatMap(lambda x: x).collect()
)

print(f"---> Les 20 capteurs californiens sélectionnés : {top_sensors_ca}")

# 3. Extraction de l'historique horaire uniquement pour ces 20 champions
df_export = (
    df_ca.filter(F.col("SiteID").isin(top_sensors_ca))
    .select("timestamp", "SiteID", F.col("Value").alias("PM25"))
    .orderBy("timestamp", "SiteID")
)

# 4. Sauvegarde en CSV local
output_name = "LSTM_dataset_California_Top20.csv"
df_export.toPandas().to_csv(output_name, index=False)

print(f"\nExtraction terminée ! Fichier sauvegardé sous : {output_name}")
spark.stop()