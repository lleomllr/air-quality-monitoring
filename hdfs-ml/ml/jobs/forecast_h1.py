import numpy as np 
import matplotlib.pyplot as plt
import pandas as pd 
import numpy as np
from sklearn.linear_model import LinearRegression 
from sklearn.model_selection import RandomizedSearchCV, TimeSeriesSplit
import xgboost as xgb 
from pyspark.sql import SparkSession 
import pyspark.sql.functions as F
from pyspark.sql.types import StructType, StructField, StringType, TimestampType, DoubleType
import warnings
warnings.filterwarnings('ignore')

spark = (
    SparkSession.builder
    .appName("Hourly-Forecasting")
    .config("spark.sql.shuffle.partitions", "200")
    .config("spark.sql.execution.arrow.pyspark.enabled", "true")
    .getOrCreate()
)
spark.sparkContext.setLogLevel("ERROR")

INPUTS = [
    "hdfs://namenode:9000/historical-data/AirNow_2024_cleaned.parquet",
    "hdfs://namenode:9000/historical-data/AirNow_2025_cleaned.parquet"
]
df = spark.read.parquet(*INPUTS)

df_hourly = (df.filter(F.col("Parameter") == "PM2.5")
             .select("SiteID", "timestamp", F.col("Value").alias("PM25"))
             .dropna()
)

schema = StructType([
    StructField("SiteID", StringType(), True), 
    StructField("timestamp", TimestampType(), True), 
    StructField("Model_Name", StringType(), True),
    StructField("Actual_PM25", DoubleType(), True), 
    StructField("Predicted_PM25", DoubleType(), True),
    StructField("Forecast_Type", StringType(), True)
])

def forecast_hourly(pdf: pd.DataFrame) -> pd.DataFrame:
    def create_empty_return():
        empty = pd.DataFrame(columns=['SiteID', 'timestamp', 'Model_Name', 'Actual_PM25', 'Predicted_PM25', 'Forecast_Type'])
        for col in ['SiteID', 'Model_Name', 'Forecast_Type']:
            empty[col] = empty[col].astype('str')
        for col in ['Actual_PM25', 'Predicted_PM25']:
            empty[col] = empty[col].astype('float64')
        empty['timestamp'] = pd.to_datetime(empty['timestamp']) 
        return empty

    if pdf is None or pdf.empty or len(pdf) < 200: 
        return create_empty_return()
    
    try:
        pdf['timestamp'] = pd.to_datetime(pdf['timestamp'])
        pdf = pdf.sort_values('timestamp').reset_index(drop=True)
        site_id = str(pdf['SiteID'].iloc[0])
        
        pdf['lag_1'] = pdf['PM25'].shift(1)
        pdf['lag_2'] = pdf['PM25'].shift(2)
        pdf['lag_3'] = pdf['PM25'].shift(3)
        pdf['lag_24'] = pdf['PM25'].shift(24) 
        
        pdf['hour_sin'] = np.sin(2 * np.pi * pdf['timestamp'].dt.hour / 24)
        pdf['hour_cos'] = np.cos(2 * np.pi * pdf['timestamp'].dt.hour / 24)
        pdf['dow_sin'] = np.sin(2 * np.pi * pdf['timestamp'].dt.dayofweek / 7)
        pdf['dow_cos'] = np.cos(2 * np.pi * pdf['timestamp'].dt.dayofweek / 7)
        
        pdf_clean = pdf.dropna(subset=['lag_1', 'lag_2', 'lag_3', 'lag_24', 'PM25'])

        cutoff_date = pd.to_datetime('2025-10-01')
        train_df = pdf_clean[pdf_clean['timestamp'] < cutoff_date]
        test_df = pdf_clean[pdf_clean['timestamp'] >= cutoff_date]
        
        if len(train_df) < 150 or len(test_df) == 0:
            return create_empty_return()
            
        features = ['lag_1', 'lag_2', 'lag_3', 'lag_24', 'hour_sin', 'hour_cos', 'dow_sin', 'dow_cos']
        X_train, y_train = train_df[features], train_df['PM25']
        X_test = test_df[features]   

        tscv = TimeSeriesSplit(n_splits=3)

        param_grid = {
            'n_estimators': [50, 100, 150],
            'max_depth': [3, 5, 7],
            'learning_rate': [0.01, 0.05, 0.1]
        }

        xgb_base = xgb.XGBRegressor(random_state=42)

        search = RandomizedSearchCV(
            estimator=xgb_base,
            param_distributions=param_grid,
            n_iter=3,
            cv=tscv,
            scoring='neg_mean_squared_error',
            random_state=42,
            n_jobs=1
        )

        search.fit(X_train, y_train)
        best_xgb = search.best_estimator_

        models = {
            "LinearRegression": LinearRegression(),
            "XGBoost": best_xgb
        }

        all_res = []

        for model_name, model in models.items():
            if model_name == "LinearRegression":
                model.fit(X_train, y_train)
            
            temp_df = pdf_clean.copy()
            temp_df['Model_Name'] = model_name
            temp_df['Predicted_PM25'] = np.nan

            temp_df.loc[train_df.index, 'Predicted_PM25'] = model.predict(X_train)
            temp_df.loc[test_df.index, 'Predicted_PM25'] = model.predict(X_test)
            temp_df.loc[train_df.index, 'Forecast_Type'] = 'Train (Pre-Oct 2025)'
            temp_df.loc[test_df.index, 'Forecast_Type'] = 'Test (Post-Oct 2025)'

            last_row = pdf_clean.iloc[-1]
            future_date = last_row['timestamp'] + pd.Timedelta(hours=1)
        
            future_features = pd.DataFrame([{
                'lag_1': last_row['PM25'],
                'lag_2': last_row['lag_1'],
                'lag_3': last_row['lag_2'],
                'lag_24': pdf_clean.iloc[-24]['PM25'] if len(pdf_clean) >= 24 else last_row['PM25'],
                'hour_sin': np.sin(2 * np.pi * future_date.hour / 24),
                'hour_cos': np.cos(2 * np.pi * future_date.hour / 24),
                'dow_sin': np.sin(2 * np.pi * future_date.dayofweek / 7),
                'dow_cos': np.cos(2 * np.pi * future_date.dayofweek / 7)
            }])
        
            future_pred = float(model.predict(future_features)[0])
            future_df = pd.DataFrame([{
                'SiteID': site_id,
                'timestamp': future_date,
                'Actual_PM25': np.nan, 
                'Predicted_PM25': future_pred,
                'Forecast_Type': 'Futur (T+1 Heure)'
            }])
            
            temp_df = pd.concat([temp_df, future_df], ignore_index=True)
            all_res.append(temp_df)
        
        final_df = pd.concat(all_res, ignore_index=True)
        final_df = final_df.dropna(subset=['Forecast_Type'])
        hist_out = final_df[['SiteID', 'timestamp', 'Model_Name', 'PM25', 'Predicted_PM25', 'Forecast_Type']].rename(columns={'PM25': 'Actual_PM25'})
        hist_out['timestamp'] = pd.to_datetime(hist_out['timestamp'])

        return hist_out
    
    except Exception as e:
        return create_empty_return()

print("\nEntraînement sur données horaires")
predictions_df = (
    df_hourly
    .groupBy("SiteID")
    .applyInPandas(forecast_hourly, schema=schema)
)
predictions_df.cache()

print("\nPERFORMANCES :")
metrics_df = predictions_df.filter(F.col("Forecast_Type").isin(["Train (Pre-Oct 2025)", "Test (Post-Oct 2025)"])) \
    .dropna(subset=["Actual_PM25", "Predicted_PM25"]) \
    .withColumn("Denom", F.when(F.col("Actual_PM25") == 0, 0.1).otherwise(F.col("Actual_PM25"))) \
    .groupBy("Model_Name", "Forecast_Type") \
    .agg(
        F.round(F.avg(F.abs(F.col("Actual_PM25") - F.col("Predicted_PM25"))), 2).alias("MAE"),
        F.round(F.sqrt(F.avg((F.col("Actual_PM25") - F.col("Predicted_PM25"))**2)), 2).alias("RMSE"),
        F.round(F.corr("Actual_PM25", "Predicted_PM25")**2, 4).alias("R2_Approximatif")
    ).orderBy("Forecast_Type", "MAE")

metrics_df.show(truncate=False)

top_site_row = predictions_df.groupBy("SiteID").count().orderBy(F.desc("count")).first()
    
if top_site_row:
    top_site = top_site_row["SiteID"]
        
    plot_df = predictions_df.filter(
        (F.col("SiteID") == top_site) & 
        (F.col("Forecast_Type") == "Test (Post-Oct 2025)")
    ).select("timestamp", "Model_Name", "Actual_PM25", "Predicted_PM25").toPandas()
        
    if not plot_df.empty:
        plot_df = plot_df.sort_values("timestamp")
            
        plt.figure(figsize=(16, 7))
            
        actuals = plot_df[plot_df['Model_Name'] == 'XGBoost']
        plt.plot(actuals['timestamp'], actuals['Actual_PM25'], color='black', label='Réalité (PM2.5)', linewidth=2)
            
        xgb_preds = plot_df[plot_df['Model_Name'] == 'XGBoost']
        plt.plot(xgb_preds['timestamp'], xgb_preds['Predicted_PM25'], color='orange', linestyle='--', label='XGBoost Optimisé', alpha=0.8)
            
        lr_preds = plot_df[plot_df['Model_Name'] == 'LinearRegression']
        plt.plot(lr_preds['timestamp'], lr_preds['Predicted_PM25'], color='blue', linestyle=':', label='Régression Linéaire', alpha=0.6)
            
        plt.title(f"Forecasting PM2.5 (Test OOT) - Comparaison des Modèles - Capteur {top_site}", fontsize=15, fontweight='bold')
        plt.ylabel("Concentration PM2.5 (µg/m³)")
        plt.xlabel("Date")
        plt.legend(loc='upper right')
        plt.grid(True, alpha=0.3)
        plt.xlim(actuals['timestamp'].min(), actuals['timestamp'].min() + pd.Timedelta(days=15))
        plt.tight_layout()
        plt.savefig(f"Models_Comparison_Site_{top_site}.png", dpi=300)
        print(f"Models_Comparison_Site_{top_site}.png")

print("\nSauvegarde des prédictions horaires")
predictions_df.write.mode("overwrite").parquet("hdfs://namenode:9000/historical-data/hourly_forecasting.parquet")

spark.stop()