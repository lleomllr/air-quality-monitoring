"""
Détection d'anomalies (KMeans, GMM, Isolation Forest)
"""
import numpy as np
import pandas as pd
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, upper, hour, first, when, sin, cos, pi, pandas_udf
from pyspark.sql import types as T
from pyspark.ml.feature import VectorAssembler, StandardScaler
from pyspark.ml.clustering import KMeans, GaussianMixture
from pyspark.ml.evaluation import ClusteringEvaluator 
import pyspark.sql.functions as F
from sklearn.ensemble import IsolationForest as SklearnIF
from pyspark.sql.types import DoubleType

spark = (
    SparkSession.builder
    .appName("Anomaly-Detection")
    .config("spark.sql.shuffle.partitions", "20")  
    .config("spark.memory.fraction", "0.8")
    .getOrCreate()
)
spark.sparkContext.setLogLevel("ERROR")

INPUT = "hdfs://namenode:9000/historical-data/AirNow_2025_cleaned.parquet"
POLLUTANTS = ["PM2.5", "PM10", "OZONE"]

df = (spark.read.parquet(INPUT)
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
         .sample(False, 0.1, 42))

target_cols = [p.replace(".", "_") for p in POLLUTANTS]
all_medians = pivot.approxQuantile(target_cols, [0.5], 0.01)
medians_dict = {col_name: med[0] for col_name, med in zip(target_cols, all_medians)}

pivot = pivot.fillna(medians_dict)

for c in pivot.columns:
    if "." in c: 
        pivot = pivot.withColumnRenamed(c, c.replace(".", "_"))

pivot = pivot.withColumn("hour", hour(col("ts")).cast("double"))
pivot = pivot.withColumn("hour_sin", sin(2 * pi() * col("hour") / 24))
pivot = pivot.withColumn("hour_cos", cos(2 * pi() * col("hour") / 24))
feature_cols = target_cols + ["hour_sin", "hour_cos"]

"""
assembler = VectorAssembler(inputCols=feature_cols, outputCol="rawFeatures")
vec = assembler.transform(pivot)
assembler_pollutants = VectorAssembler(inputCols=target_cols, outputCol="raw_pollutants")
vec_pollutants = assembler_pollutants.transform(pivot)
scaler = StandardScaler(inputCol="rawFeatures", outputCol="features", withMean=True, withStd=True)
scaler_model = scaler.fit(vec)
scaled_data = scaler_model.transform(vec)
scaled_data.cache()
"""

assembler_pollutants = VectorAssembler(inputCols=target_cols, outputCol="raw_pollutants")
vec_pollutants = assembler_pollutants.transform(pivot)

scaler = StandardScaler(inputCol="raw_pollutants", outputCol="scaled_pollutants", withMean=True, withStd=True)
scaler_model = scaler.fit(vec_pollutants)
data_partiel = scaler_model.transform(vec_pollutants)

#On réduit l'importance de l'heure (0.2)
#Comme le sinus/cosinus est déjà entre -1 et 1, pas besoin de scaler 
data_partiel = data_partiel.withColumn("hour_sin_weighted", sin(2 * pi() * col("hour") / 24) * 0.2)
data_partiel = data_partiel.withColumn("hour_cos_weighted", cos(2 * pi() * col("hour") / 24) * 0.2)

assembler_final = VectorAssembler(
    inputCols=["scaled_pollutants", "hour_sin_weighted", "hour_cos_weighted"], 
    outputCol="features"
)
scaled_data = assembler_final.transform(data_partiel)
scaled_data.cache()

res = {}
evaluator = ClusteringEvaluator(featuresCol="features", metricName="silhouette")
"""
#KMeans
kmeans = KMeans(featuresCol="features", predictionCol="kmeans_pred", k=3, seed=42)
model_kmeans = kmeans.fit(scaled_data)
res["KMeans"] = evaluator.setPredictionCol("kmeans_pred").evaluate(model_kmeans.transform(scaled_data))

#Gaussian Mixture (GMM)
gmm = GaussianMixture(featuresCol="features", predictionCol="gp", k=3, seed=42)
model_gmm = gmm.fit(scaled_data)
res["GMM"] = evaluator.setPredictionCol("gp").evaluate(model_gmm.transform(scaled_data))
try:
    res["GMM BIC"] = model_gmm.summary.bic
except AttributeError:
    res["GMM LogLikelihood"] = model_gmm.summary.logLikelihood

#Isolation Forest (si dispo)
try:
    from pyspark.ml.feature import IsolationForest
    iso = IsolationForest(featuresCol="features", predictionCol="iso_pred", anomalyScoreCol="iso_score", contamination=0.01, maxSamples=256, seed=42)
    model_iso = iso.fit(scaled_data)
    pred_iso = model_iso.transform(scaled_data)
    anomaly_rate = pred_iso.filter(F.col("iso_pred") == 1.0).count() / pred_iso.count()
    res["IsolationForest Anomaly Rate"] = anomaly_rate
except ImportError:
    res["IsolationForest"] = "Not available in this Spark version"
"""

k_range = range(2, 8)
best_k_kmeans = 2
best_silhouette_km = -1
best_k_gmm = 2
best_silhouette_gmm = -1

results_tuning = []

print("Début de l'optimisation des hyperparamètres")

for k in k_range:
    #KMeans
    km = KMeans(featuresCol="features", predictionCol=f"km_{k}", k=k, seed=42)
    model_km = km.fit(scaled_data)
    pred_km = model_km.transform(scaled_data)
    sil_km = evaluator.setPredictionCol(f"km_{k}").evaluate(pred_km)
    
    #GMM
    gmm = GaussianMixture(featuresCol="features", predictionCol=f"gmm_{k}", k=k, seed=42, maxIter=30)
    model_gmm = gmm.fit(scaled_data)
    pred_gmm = model_gmm.transform(scaled_data)
    sil_gmm = evaluator.setPredictionCol(f"gmm_{k}").evaluate(pred_gmm)
    #bic_gmm = model_gmm.summary._java_obj.bic() # Direct Scala call pour Spark 3.5
    
    results_tuning.append((k, sil_km, sil_gmm))
    print(f"k={k} | Silh. KM: {sil_km:.4f} | Silh. GMM: {sil_gmm:.4f}")

    #Sauvegarde des meilleurs k
    if sil_km > best_silhouette_km:
        best_silhouette_km = sil_km
        best_k_kmeans = k
    if sil_gmm > best_silhouette_gmm:
        best_silhouette_gmm = sil_gmm
        best_k_gmm = k

#Isolation Forest
print("\n🌲 Entraînement Isolation Forest Hybride (Scikit-Learn + Spark)")
if_cols = target_cols 
train_pdf = scaled_data.select(if_cols).sample(False, 0.02, seed=42).toPandas()
sklearn_iso = SklearnIF(contamination=0.01, n_estimators=200, random_state=42)
sklearn_iso.fit(train_pdf)
broadcast_iso = spark.sparkContext.broadcast(sklearn_iso)

@pandas_udf(T.DoubleType())
def predict_iso_score(*cols):
    pdf = pd.concat(cols, axis=1)
    pdf.columns = if_cols
    model = broadcast_iso.value
    # Score : on inverse pour que les valeurs positives soient les anomalies
    scores = -model.decision_function(pdf) 
    return pd.Series(scores)

pred_iso_final = scaled_data.withColumn(
    "iso_score", 
    predict_iso_score(*[F.col(c) for c in if_cols])
)

#Résultats
print("\n" + "="*30)
print(f"MEILLEURS PARAMÈTRES TROUVÉS :")
print(f"KMeans : k={best_k_kmeans} (Silhouette: {best_silhouette_km:.4f})")
print(f"GMM    : k={best_k_gmm} (Silhouette: {best_silhouette_gmm:.4f})")
print("="*30)

print("\n ANALYSE DES CLUSTERS (KMeans k=2)")
final_km = KMeans(featuresCol="features", predictionCol="cluster", k=best_k_kmeans, seed=42)
model_final = final_km.fit(scaled_data)

analysis_df = model_final.transform(scaled_data)

stats = analysis_df.groupBy("cluster").agg(
    F.avg("PM2_5").alias("Moy_PM25"),
    F.avg("PM10").alias("Moy_PM10"),
    F.avg("OZONE").alias("Moy_Ozone"),
    F.avg("hour").alias("Heure_Moyenne"),
    F.count("*").alias("Nb_Points")
).orderBy("cluster")
stats.show()

rows = stats.collect()
if len(rows) >= 2:
    diff_pollution = abs(rows[0]['Moy_PM25'] - rows[1]['Moy_PM25'])
    diff_heure = abs(rows[0]['Heure_Moyenne'] - rows[1]['Heure_Moyenne'])
    
    print(f"\nDifférence de PM2.5 entre clusters : {diff_pollution:.2f}")
    print(f"Différence d'heure entre clusters : {diff_heure:.2f} heures")
    
    if diff_heure > 8 and diff_pollution < 2:
        print("ALERTE ! Le modèle semble privilégier l'heure sur la pollution (Biais temporel).")
    else:
        print("Le modèle semble équilibrer les facteurs temporels et chimiques.")

print("\nTOP 10 DES ANOMALIES LES PLUS SÉVÈRES (Isolation Forest)")
cols_to_show = ["ts", "SiteID"] + target_cols 
top_anomalies = pred_iso_final.orderBy(F.col("iso_score").desc())
top_anomalies.select(cols_to_show + ["iso_score"]).show(10, truncate=False)

scaled_data.unpersist()
spark.stop()