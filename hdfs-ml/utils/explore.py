from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, countDistinct, min, max, avg

def main():
    spark = SparkSession.builder \
        .appName("EDA-AirNow") \
        .config("spark.hadoop.fs.defaultFS", "hdfs://namenode:9000") \
        .getOrCreate()
    
    print("="*60)
    print("Exploration des données historiques sur l'année 2025")
    print("="*60)

    df = spark.read.parquet("hdfs://namenode:9000/historical-data/AirNow_2025_spark.parquet")
    print("\n Schéma")
    df.printSchema()

    print(f"\n Nb de lignes: {df.count()}")

    print(f"\n Colonnes ({df.columns})")
    for c in df.columns:
        print(f" - {c}")
    
    print("\n Quelques lignes")
    df.show(10, truncate=False)

    print("\n Paramètres mesurés")
    df.groupBy("parameter").count().orderBy("count", ascending=False).show()

    print("\n Unités")
    df.groupBy("unit").count().show()

    print("\n Stats")
    df_sample = df.sample(0.01)
    df_sample.select(
        col("value").cast("double").alias("Value")
    ).filter(col("Value").isNotNull()).describe().show()

    print("\n Plages de dates")
    df.select(min("timestamp"), max("timestamp")).show()

    print("\n=== NOMBRE DE SITES ===")
    print(f"Sites uniques: {df.select('SiteID').distinct().count()}")
    
    print("\n=== ÉTATS ===")
    df.groupBy("StateCode").count().orderBy("count", ascending=False).show(10)
    
    df.unpersist()
    spark.stop()
    print("\n Exploration terminée")
    
if __name__ == "__main__": 
    main()