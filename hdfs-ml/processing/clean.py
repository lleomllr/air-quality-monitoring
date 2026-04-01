from pyspark.sql import SparkSession 
from pyspark.sql import functions as F

def main():
    spark = SparkSession.builder \
        .appName("Data cleaning") \
        .config("spark.hadoop.fs.defaultFS", "hdfs://namenode:9000") \
        .config("spark.sql.shuffle.partitions", "200") \
        .config("spark.executor.memory", "3g") \
        .config("spark.driver.memory", "3g") \
        .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")
    
    print("="*60)
    print("Nettoyage des données historiques")
    print("="*60)

    df_raw = spark.read.parquet("hdfs://namenode:9000/historical-data/AirNow_2024_spark.parquet")
    init_count = df_raw.count()
    print(f"\n Nb de lignes initiales : {init_count:,}")

    #Filtrer les Etats-Unis
    us_states_codes = [
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
        "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
        "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
        "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
        "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
        "DC", "PR", "VI", "GU", "AS", "MP"
    ]

    df_us = df_raw.filter(F.col("StateCode").isin(us_states_codes))

    #Filtrer les polluants
    valid_p = ["OZONE", "PM2.5", "PM10"]
    df_filt = df_us.filter(F.col("Parameter").isin(valid_p))

    print("\n" + "-"*70)
    print("Recherche et suppression des doublons")
    print("-"*70)
    
    #supprime les lignes ayant le même SiteID, au même instant, pour le même polluant
    df_dedup = df_filt.dropDuplicates(["SiteID", "timestamp", "Parameter"])

    #df_dedup.cache() 

    #Analyse des valeurs aberrantes par polluant
    print("\n" + "-"*70)
    print("Analyse des valeurs par polluant")
    print("-"*70)

    df_num = df_dedup.withColumn("value_num", F.col("Value").cast("double"))
    
    stats_df = df_num.groupBy("Parameter").agg(
        F.count("value_num").alias("count"),
        F.count(F.when(F.isnull("value_num") | F.isnan("value_num"), True)).alias("nulls"),
        F.round(F.min("value_num"), 2).alias("min"),
        F.round(F.max("value_num"), 2).alias("max"),
        F.round(F.avg("value_num"), 2).alias("mean"),
        F.round(F.stddev("value_num"), 2).alias("stddev")
    )
    stats_df.show(truncate=False)

    #Seuils
    thresholds = {
        "OZONE": {"min": 0, "max": 500},   
        "PM2.5": {"min": 0, "max": 500}, 
        "PM10": {"min": 0, "max": 650}   
    }

    df_clean = df_num.filter(F.col("value_num").isNotNull() & ~F.isnan(F.col("value_num")))
    df_clean = df_clean.filter(F.col("value_num") >= 0)
    df_clean = df_clean.filter(
        ((F.col("Parameter") == "OZONE") & (F.col("value_num") <= thresholds["OZONE"]["max"])) |
        ((F.col("Parameter") == "PM2.5") & (F.col("value_num") <= thresholds["PM2.5"]["max"])) |
        ((F.col("Parameter") == "PM10") & (F.col("value_num") <= thresholds["PM10"]["max"]))
    )

    df_clean = df_clean.filter(F.col("timestamp").isNotNull())

    print("\n" + "-"*70)
    print("Résumé du cleaning")
    print("-"*70)

    df_final = df_clean.select(
        F.col("timestamp"), F.col("Date"), F.col("Time"), 
        F.col("SiteID"), F.col("SiteName"), F.col("StateCode"),
        F.col("CountyName"), F.col("ReportingAreaName"),
        F.col("Latitude"), F.col("Longitude"), F.col("Parameter"),
        F.col("value_num").alias("Value"), F.col("Unit"), F.col("AgencyName")
    )

    output = "hdfs://namenode:9000/historical-data/AirNow_2024_cleaned.parquet"

    df_final.write.mode("overwrite").parquet(output)
    print(f"\n Données nettoyées sauvegardées : {output}")

    spark.stop()

if __name__ == "__main__":
    main()