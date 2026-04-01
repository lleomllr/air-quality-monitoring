"""
Isolation Forest avec lags et deltas
"""
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, upper, hour, first, sin, cos, pi, pandas_udf
from pyspark.sql import types as T
from pyspark.sql.window import Window
import pyspark.sql.functions as F
from sklearn.ensemble import IsolationForest as SklearnIF
import warnings
warnings.filterwarnings('ignore')

spark = (
    SparkSession.builder
    .appName("Anomaly-Detection-With-Lags")
    .config("spark.sql.shuffle.partitions", "20")  
    .config("spark.memory.fraction", "0.8")
    .getOrCreate()
)
spark.sparkContext.setLogLevel("ERROR")

INPUTS = [
        "hdfs://namenode:9000/historical-data/AirNow_2024_cleaned.parquet",
        "hdfs://namenode:9000/historical-data/AirNow_2025_cleaned.parquet"
]
POLLUTANTS = ["PM2.5", "PM10", "OZONE"]

df = (spark.read.parquet(*INPUTS)
      .withColumn("Param", F.regexp_replace(upper(col("Parameter")), "\.", "_"))
      .filter(col("Param").isin([p.replace(".", "_") for p in POLLUTANTS]))
      .select(
          col("timestamp").cast("timestamp").alias("ts"),
          "SiteID", "Value", "Param"
      )
      .na.drop()
)

pivot = (df.groupBy("ts", "SiteID")
         .pivot("Param")
         .agg(first("Value"))
         .sample(False, 0.2, 42)) 

target_cols = [p.replace(".", "_") for p in POLLUTANTS]

all_medians = pivot.approxQuantile(target_cols, [0.5], 0.01)
med_dict = {col_name: med[0] for col_name, med in zip(target_cols, all_medians)}
pivot = pivot.fillna(med_dict)

#Création des lags
#On définit une fenêtre : pour chaque Site, on trie par date/heure
windowSpec = Window.partitionBy("SiteID").orderBy("ts")

#nouvelle colonne pour chaque polluant : la valeur de l'heure précédente
lag_cols = []
for p in target_cols:
    lag_col_name = f"{p}_lag1"
    pivot = pivot.withColumn(lag_col_name, F.lag(col(p), 1).over(windowSpec))
    lag_cols.append(lag_col_name)

#Le lag crée des Nulls pour la toute première heure de chaque site. On supprime ces lignes.
pivot = pivot.na.drop(subset=lag_cols)

#Création des deltas (Différence entre t et t-1)
delta_cols = []
for p in target_cols:
    delta_col_name = f"{p}_delta"
    pivot = pivot.withColumn(delta_col_name, col(p) - col(f"{p}_lag1"))
    delta_cols.append(delta_col_name)

pivot = pivot.withColumn("hour", hour(col("ts")).cast("double"))
pivot = pivot.withColumn("hour_sin_weighted", sin(2 * pi() * col("hour") / 24) * 0.2)
pivot = pivot.withColumn("hour_cos_weighted", cos(2 * pi() * col("hour") / 24) * 0.2)

if_cols = target_cols + delta_cols + ["hour_sin_weighted", "hour_cos_weighted"]

print("Extraction de l'échantillon d'entraînement")
train_pdf = pivot.select(if_cols).sample(False, 0.02, 42).toPandas()

print("Entraînement du modèle Scikit-Learn")
sklearn_iso = SklearnIF(contamination="auto", n_estimators=200, random_state=42, n_jobs=-1)
sklearn_iso.fit(train_pdf)

broadcast_iso = spark.sparkContext.broadcast(sklearn_iso)

@pandas_udf(T.DoubleType())
def predict_anomaly_score(*cols):
    pdf = pd.concat(cols, axis=1)
    pdf.columns = if_cols
    model = broadcast_iso.value
    #decision_function renvoie un score (négatif = anomalie, positif = normal dans sklearn)
    #plus c'est grand, plus c'est anormal
    scores = -model.decision_function(pdf)
    return pd.Series(scores)

print("Prédiction distribuée sur l'ensemble du dataset")
pred_iso_final = pivot.withColumn(
    "iso_score",
    predict_anomaly_score(*[F.col(c) for c in if_cols])
)

quantiles = pred_iso_final.approxQuantile("iso_score", [0.25, 0.75], 0.01)
q1, q3 = quantiles[0], quantiles[1]
iqr = q3 - q1
threshold = q3 + 2.0 * iqr
print(f"\nStatistiques des scores d'anomalie : Q1={q1:.3f}, Q3={q3:.3f}, IQR={iqr:.3f}")
print(f"\nSeuil d'anomalie : {threshold:.3f}")

pred_iso_final = pred_iso_final.withColumn("is_anomaly", F.when(col("iso_score") > threshold, 1).otherwise(0))

nb_anomalies = pred_iso_final.filter(col("is_anomaly") == 1).count()
total_count = pred_iso_final.count()
print(f"\nNombre d'anomalies détectées : {nb_anomalies} sur {total_count} points ({(nb_anomalies/total_count)*100:.2f}%)")

scores_pd = pred_iso_final.select("iso_score").sample(False, 0.1, 42).toPandas()
plt.figure(figsize=(10, 6))
plt.hist(scores_pd['iso_score'], bins=50, color='skyblue', edgecolor='black')
plt.axvline(x=threshold, color='red', linestyle='dashed', linewidth=2, label=f'Seuil IQR: {threshold:.2f}')
plt.title("Distribution des Scores d'Anomalie (Isolation Forest)", fontsize=14, fontweight='bold')
plt.xlabel("Score d'Anomalie")
plt.ylabel("Fréquence")
plt.legend()
plt.grid(True, alpha=0.3)
plt.savefig("IF_score_distribution.png", dpi=300)
print("Sauvegardé : IF_score_distribution.png")

print("\nTOP 10 DES PIRES ANOMALIES (Plus gros sauts inexpliqués)")
cols_to_show = ["ts", "SiteID"] + target_cols + delta_cols
pred_iso_final.filter(col("is_anomaly") == 1).orderBy(F.col("iso_score").desc()).select(cols_to_show + ["iso_score"]).show(10, truncate=False)

pred_iso_final.unpersist()
spark.stop()
