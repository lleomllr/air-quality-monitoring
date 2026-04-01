"""
Clustering des capteurs par profil de pollution 
"""
import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from pyspark.sql import SparkSession
import pyspark.sql.functions as F
from pyspark.ml.feature import StandardScaler, VectorAssembler, PCA
from pyspark.ml.clustering import KMeans, GaussianMixture
from pyspark.ml.evaluation import ClusteringEvaluator
from sklearn.cluster import DBSCAN
from sklearn.metrics import silhouette_score
import warnings
warnings.filterwarnings('ignore')

spark = (
    SparkSession.builder
    .appName("Clustering-Sensors")
    .config("spark.sql.shuffle.partitions", "200")
    .config("spark.memory.fraction", "0.8")
    .config("spark.sql.execution.arrow.pyspark.enabled", "true")
    .getOrCreate()
)
spark.sparkContext.setLogLevel("ERROR")

INPUTS = [
    "hdfs://namenode:9000/historical-data/AirNow_2024_cleaned.parquet",
    "hdfs://namenode:9000/historical-data/AirNow_2025_cleaned.parquet"
]
df = spark.read.parquet(*INPUTS)

df_profil = df.groupBy("SiteID", "Latitude", "Longitude").agg(
    F.round(F.avg(F.when(F.col("Parameter") == "PM2.5", F.col("Value"))), 2).alias("PM25_mean"),
    F.round(F.max(F.when(F.col("Parameter") == "PM2.5", F.col("Value"))), 2).alias("PM25_max"),
    F.round(F.stddev(F.when(F.col("Parameter") == "PM2.5", F.col("Value"))), 2).alias("PM25_std"),
    F.round(F.avg(F.when(F.col("Parameter") == "OZONE", F.col("Value"))), 2).alias("OZONE_mean"),
    F.round(F.max(F.when(F.col("Parameter") == "OZONE", F.col("Value"))), 2).alias("OZONE_max"),
).fillna(0)

df_profil = df_profil.withColumn("PM25_max_log", F.log1p(F.col("PM25_max"))) \
                     .withColumn("PM25_std_log", F.log1p(F.col("PM25_std"))) \
                     .withColumn("OZONE_max_log", F.log1p(F.col("OZONE_max")))

nb_sensors = df_profil.count()
print(f"\nNombre de capteurs profilés : {nb_sensors}")

features_cols = ["PM25_mean", "PM25_max", "PM25_std", "OZONE_mean", "OZONE_max"]

assembler = VectorAssembler(inputCols=features_cols, outputCol="raw_features")
df_vec = assembler.transform(df_profil)
scaler = StandardScaler(inputCol="raw_features", outputCol="scaled_features")
scaler_model = scaler.fit(df_vec)
df_scaled = scaler_model.transform(df_vec)

#pca = PCA(k=2, inputCol="scaled_features", outputCol="pca_features")
#pca_model = pca.fit(df_scaled)
#df_pca = pca_model.transform(df_scaled).cache()

evaluator = ClusteringEvaluator(featuresCol="scaled_features", predictionCol="prediction", metricName="silhouette")

k_val = [2, 3, 4, 5, 6]
ps_res = []

for k in k_val:
    kmeans = KMeans(featuresCol="scaled_features", predictionCol="prediction", k=k, seed=42)
    kmeans_model = kmeans.fit(df_scaled)
    kmeans_silhouette = evaluator.evaluate(kmeans_model.transform(df_scaled))

    gmm = GaussianMixture(featuresCol="scaled_features", predictionCol="prediction", k=k, seed=42)
    gmm_model = gmm.fit(df_scaled)
    gmm_silhouette = evaluator.evaluate(gmm_model.transform(df_scaled))

    ps_res.append({
        "k": k, 
        "KMeans": kmeans_silhouette,
        "GMM": gmm_silhouette
    })

benchmark_df = pd.DataFrame(ps_res).set_index("k").round(3)

pdf = df_scaled.select("SiteID", "Latitude", "Longitude", "scaled_features", *features_cols).toPandas()

pdf['Latitude'] = pdf['Latitude'].astype(float)
pdf['Longitude'] = pdf['Longitude'].astype(float)

X = np.stack(pdf['scaled_features'].apply(lambda x: np.array(x.toArray())).values)

eps_val = [0.5, 1.0, 1.5, 2.0]
#eps_val = [0.25, 0.5, 0.8, 1.0]
dbscan_res = []

for eps in eps_val: 
    db = DBSCAN(eps=eps, min_samples=5).fit(X)
    labels = db.labels_
    n_clusters = len(set(labels)) - (1 if -1 in labels else 0)
    n_noise = list(labels).count(-1)

    if n_clusters >= 1 and len(set(labels)) > 1:
        silhouette = silhouette_score(X, labels)
    else:
        silhouette = -1.0
    
    dbscan_res.append({
        "Epsilon": eps, 
        "Nb de clusters trouvés": n_clusters, 
        "Outliers": n_noise, 
        "Silhouette": round(silhouette, 3)
    })

dbscan_df = pd.DataFrame(dbscan_res).set_index("Epsilon")

print("\nBENCHMARK")
print(f"\nKMeans vs GMM (Silhouette Score) :\n{benchmark_df}")
print(f"\nDBSCAN (Silhouette Score) :\n{dbscan_df}")

best_k = 4
final_kmeans = KMeans(featuresCol="scaled_features", predictionCol="Cluster", k=best_k, seed=42)
pdf['Cluster_KMeans'] = final_kmeans.fit(df_scaled).transform(df_scaled).select("Cluster").toPandas()

best_dbscan = DBSCAN(eps=0.5, min_samples=5).fit(X)
pdf['Cluster_DBSCAN'] = best_dbscan.labels_

#pdf['PC1'] = pdf['pca_features'].apply(lambda x: x[0])
#pdf['PC2'] = pdf['pca_features'].apply(lambda x: x[1])

"""
plt.figure(figsize=(10, 8))
sns.scatterplot(data=pdf, x='PC1', y='PC2', hue='Cluster_KMeans', palette='tab10', s=60, alpha=0.8, edgecolor='black')
plt.title(f"Projection PCA 2D - K-Means (K={best_k})", fontsize=15, fontweight='bold')
plt.xlabel("Composante Principale 1 (PC1)")
plt.ylabel("Composante Principale 2 (PC2)")
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig("PCA_Space_Clusters.png", dpi=300)
print("PCA_Space_Clusters.png sauvegardé")
"""

fig, axes = plt.subplots(1, 2, figsize=(20, 8))

sns.scatterplot(data=pdf, x='Longitude', y='Latitude', hue='Cluster_KMeans', palette='tab10', s=50, alpha=0.8, edgecolor='black', ax=axes[0])
axes[0].set_title(f"Clustering K-Means (K={best_k})", fontsize=14, fontweight='bold')
axes[0].grid(True, alpha=0.3)

dbscan_palette = {label: 'black' if label == -1 else sns.color_palette('tab10')[label % 10] for label in set(pdf['Cluster_DBSCAN'])}
sns.scatterplot(data=pdf, x='Longitude', y='Latitude', hue='Cluster_DBSCAN', palette=dbscan_palette, s=50, alpha=0.8, edgecolor='white', ax=axes[1])
axes[1].set_title("Clustering DBSCAN (Les points noirs = Anomalies/Bruit)", fontsize=14, fontweight='bold')
axes[1].grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig("Benchmark_Maps_Comparison.png", dpi=300)
print("Benchmark_Maps_Comparison.png sauvegardé")

spark.stop()
