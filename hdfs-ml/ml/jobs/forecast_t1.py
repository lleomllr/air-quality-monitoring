"""
Prévision à t+1 avec Régression Linéaire
"""
import pandas as pd 
import numpy as np
from sklearn.linear_model import LinearRegression 
import xgboost as xgb 
from pyspark.sql import SparkSession 
import pyspark.sql.functions as F
from pyspark.sql.types import StructType, StructField, StringType, TimestampType, DoubleType, DateType
from pyspark.ml.evaluation import RegressionEvaluator

spark = (
    SparkSession.builder
    .appName("LR-XG-Forecasting-t1")
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

df_daily = (df.filter(F.col("Parameter") == "PM2.5")
            .groupBy(F.to_date("timestamp").alias("Date"), "SiteID")
            .agg(F.avg("Value").alias("PM25"))
            .dropna()
)

schema = StructType([
    StructField("SiteID", StringType(), True), 
    StructField("Date", DateType(), True), 
    StructField("Model_Name", StringType(), True),
    StructField("Actual_PM25", DoubleType(), True), 
    StructField("Predicted_PM25", DoubleType(), True),
    StructField("Forecast_Type", StringType(), True)
])


def forecast(pdf: pd.DataFrame) -> pd.DataFrame:
    def create_empty_return():
        empty = pd.DataFrame(columns=['SiteID', 'Date', 'Model_Name', 'Actual_PM25', 'Predicted_PM25', 'Forecast_Type'])
        empty['SiteID'] = empty['SiteID'].astype('str')
        empty['Model_Name'] = empty['Model_Name'].astype('str')
        empty['Actual_PM25'] = empty['Actual_PM25'].astype('float64')
        empty['Predicted_PM25'] = empty['Predicted_PM25'].astype('float64')
        empty['Forecast_Type'] = empty['Forecast_Type'].astype('str')
        empty['Date'] = pd.to_datetime(empty['Date']).dt.date 
        return empty

    if pdf is None or pdf.empty or len(pdf) < 50:
        return create_empty_return()
    
    try:
        pdf['Date'] = pd.to_datetime(pdf['Date'])
        pdf = pdf.sort_values('Date').reset_index(drop=True)
        site_id = str(pdf['SiteID'].iloc[0])
        
        pdf['lag_1'] = pdf['PM25'].shift(1)
        pdf['lag_2'] = pdf['PM25'].shift(2)
        pdf['lag_3'] = pdf['PM25'].shift(3)
        pdf['lag_7'] = pdf['PM25'].shift(7)
        
        pdf['dow_sin'] = np.sin(2 * np.pi * pdf['Date'].dt.dayofweek / 7)
        pdf['dow_cos'] = np.cos(2 * np.pi * pdf['Date'].dt.dayofweek / 7)
        
        pdf_clean = pdf.dropna(subset=['lag_1', 'lag_2', 'lag_3', 'lag_7', 'PM25'])

        cutoff_date = pd.to_datetime('2025-10-01')
        train_df = pdf_clean[pdf_clean['Date'] < cutoff_date]
        test_df = pdf_clean[pdf_clean['Date'] >= cutoff_date]
        
        if len(train_df) < 30 or len(test_df) == 0:
            return create_empty_return()
            
        features = ['lag_1', 'lag_2', 'lag_3', 'lag_7', 'dow_sin', 'dow_cos']
        X_train, y_train = train_df[features], train_df['PM25']
        X_test = test_df[features]   

        models = {
            "LinearRegression": LinearRegression(),
            "XGBoost": xgb.XGBRegressor(n_estimators=80, max_depth=4, learning_rate=0.05, random_state=42)
        }

        all_res = []

        for model_name, model in models.items():
            model.fit(X_train, y_train)
            
            temp_df = pdf_clean.copy()
            temp_df['Model_Name'] = model_name
            temp_df['Predicted_PM25'] = np.nan

            temp_df.loc[train_df.index, 'Predicted_PM25'] = model.predict(X_train)
            temp_df.loc[test_df.index, 'Predicted_PM25'] = model.predict(X_test)
            temp_df.loc[train_df.index, 'Forecast_Type'] = 'Train (Pre-Oct 2025)'
            temp_df.loc[test_df.index, 'Forecast_Type'] = 'Test (Post-Oct 2025)'

            last_row = pdf_clean.iloc[-1]
            future_date = last_row['Date'] + pd.Timedelta(days=1)
        
            future_features = pd.DataFrame([{
                'lag_1': last_row['PM25'],
                'lag_2': last_row['lag_1'],
                'lag_3': last_row['lag_2'],
                'lag_7': pdf.iloc[-7]['PM25'] if len(pdf) >= 7 else last_row['PM25'],
                'dow_sin': np.sin(2 * np.pi * future_date.dayofweek / 7),
                'dow_cos': np.cos(2 * np.pi * future_date.dayofweek / 7)
            }]).fillna(method='ffill', axis=1).fillna(0)
        
            future_pred = float(model.predict(future_features)[0])
            future_df = pd.DataFrame([{
                'SiteID': site_id,
                'Date': future_date,
                'Actual_PM25': np.nan, 
                'Predicted_PM25': future_pred,
                'Forecast_Type': 'Futur (T+1)'
            }])
            temp_df = pd.concat([temp_df, future_df], ignore_index=True)
            all_res.append(temp_df)
        
        final_df = pd.concat(all_res, ignore_index=True)
        final_df = final_df.dropna(subset=['Forecast_Type'])
        hist_out = final_df[['SiteID', 'Date', 'Model_Name', 'PM25', 'Predicted_PM25', 'Forecast_Type']].rename(columns={'PM25': 'Actual_PM25'})
        hist_out['Date'] = pd.to_datetime(hist_out['Date']).dt.date

        return hist_out
    
    except Exception as e:
        return create_empty_return()

print("\nDéploiement")
predictions_df = (
    df_daily
    .groupBy("SiteID")
    .applyInPandas(forecast, schema=schema)
)
predictions_df.cache()

print("\nPERFORMANCES :")
metrics_df = predictions_df.filter(F.col("Forecast_Type").isin(["Train (Pre-Oct 2025)", "Test (Post-Oct 2025)"])) \
    .dropna(subset=["Actual_PM25", "Predicted_PM25"]) \
    .groupBy("Model_Name", "Forecast_Type") \
    .agg(
        F.round(F.avg(F.abs(F.col("Actual_PM25") - F.col("Predicted_PM25"))), 2).alias("MAE"),
        F.round(F.sqrt(F.avg((F.col("Actual_PM25") - F.col("Predicted_PM25"))**2)), 2).alias("RMSE")
    ).orderBy("Forecast_Type", "MAE")

metrics_df.show(truncate=False)

print("\nSauvegarde des prédictions")
predictions_df.write.mode("overwrite").parquet("hdfs://namenode:9000/historical-data/lr_xgboost_forecasting_t1.parquet")

spark.stop()



