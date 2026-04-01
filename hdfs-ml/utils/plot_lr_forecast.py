import os
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
from pyspark.sql import SparkSession
import pyspark.sql.functions as F

spark = SparkSession.builder.appName("Plot-Multi-Models").getOrCreate()
spark.sparkContext.setLogLevel("ERROR")

print("Chargement des prédictions...")
df_preds = spark.read.parquet("hdfs://namenode:9000/historical-data/lr_xgboost_forecasting_t1.parquet")

SITE_ID = "132450091" 

# 1. On récupère TOUT l'historique (Train + Test) pour ce capteur
site_data = df_preds.filter(
    (F.col("SiteID") == SITE_ID) & 
    (F.col("Forecast_Type").isin(["Train (Pre-Oct 2025)", "Test (Post-Oct 2025)"]))
).toPandas()

# 2. Pivot : On transforme le format long (1 ligne par modèle) en format large (colonnes)
df_pivot = site_data.pivot_table(
    index=['Date', 'Actual_PM25'], 
    columns='Model_Name', 
    values='Predicted_PM25'
).reset_index()

# 3. Tri temporel
df_pivot['Date'] = pd.to_datetime(df_pivot['Date'])
df_pivot = df_pivot.sort_values('Date').reset_index(drop=True)

# 4. Création de la Baseline Naïve (basée sur la vraie valeur de la veille)
df_pivot['Naive_PM25'] = df_pivot['Actual_PM25'].shift(1)

# =========================================================
# 5. ISOLATION DE LA PÉRIODE DE TEST (Post 1er Octobre)
# =========================================================
test_df = df_pivot[df_pivot['Date'] >= '2025-10-01'].copy()

# On retire les éventuelles lignes incomplètes pour comparer de manière équitable
test_df = test_df.dropna(subset=['Actual_PM25', 'LinearRegression', 'XGBoost', 'Naive_PM25'])

# Calcul des métriques spécifiques à ce capteur sur cette période
mae_lr = np.mean(np.abs(test_df['Actual_PM25'] - test_df['LinearRegression']))
mae_xgb = np.mean(np.abs(test_df['Actual_PM25'] - test_df['XGBoost']))
mae_naive = np.mean(np.abs(test_df['Actual_PM25'] - test_df['Naive_PM25']))

print(f"\nMÉTRIQUES DE TEST POUR LE CAPTEUR {SITE_ID} (Oct/Nov 2025) :")
print(f" - MAE Régression Linéaire : {mae_lr:.2f}")
print(f" - MAE XGBoost             : {mae_xgb:.2f}")
print(f" - MAE Baseline (Naïve)    : {mae_naive:.2f}")

# =========================================================
# 6. CRÉATION DU GRAPHIQUE COMPARATIF
# ==========================================================
plt.figure(figsize=(16, 8))

# Courbe 1 : Valeur Réelle (En noir, plus épaisse)
plt.plot(test_df['Date'], test_df['Actual_PM25'], 
         label='Valeur Réelle', color='black', linewidth=2.5)

# Courbe 2 : Régression Linéaire (En bleu)
plt.plot(test_df['Date'], test_df['LinearRegression'], 
         label=f'Régression Linéaire (MAE: {mae_lr:.2f})', color='blue', linestyle='--', linewidth=2, alpha=0.8)

# Courbe 3 : XGBoost (En orange)
plt.plot(test_df['Date'], test_df['XGBoost'], 
         label=f'XGBoost (MAE: {mae_xgb:.2f})', color='orange', linestyle='-.', linewidth=2, alpha=0.9)

# Courbe 4 : Baseline Naïve (En rouge pointillé)
plt.plot(test_df['Date'], test_df['Naive_PM25'], 
         label=f'Baseline Naïve (MAE: {mae_naive:.2f})', color='red', linestyle=':', linewidth=1.5, alpha=0.5)

plt.title(f"Comparaison LR vs XGBoost - Capteur {SITE_ID}", fontsize=16, fontweight='bold')
plt.ylabel("Concentration PM2.5 (µg/m³)", fontsize=12)
plt.xlabel("Date", fontsize=12)
plt.legend(fontsize=12)
plt.grid(True, alpha=0.3)
plt.tight_layout()

folder = os.getcwd() 
img_name = f"{folder}/comparaison_modeles_test_{SITE_ID}.png"
plt.savefig(img_name, dpi=300, bbox_inches='tight') 
print(f"\nGraphique sauvegardé : {img_name}")
plt.close()

spark.stop()