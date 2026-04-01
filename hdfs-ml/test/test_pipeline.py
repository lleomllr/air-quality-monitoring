import pytest
import pandas as pd 
import numpy as np 

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
    
    pdf['timestamp'] = pd.to_datetime(pdf['timestamp'])
    pdf = pdf.sort_values('timestamp').reset_index(drop=True)
    pdf['lag_1'] = pdf['PM25'].shift(1)
    pdf['hour_sin'] = np.sin(2 * np.pi * pdf['timestamp'].dt.hour / 24)

    return pdf

def test_sensor_failure():
    dummy = {
        'SiteID': ['S1']*10,
        'timestamp': pd.date_range(start='2024-01-01', periods=10, freq='H'),
        'PM25': np.random.rand(10) 
    }
    df_panne = pd.DataFrame(dummy)
    res = forecast_hourly(df_panne)
    assert res.empty, "Le DataFrame de retour devrait être vide pour un échantillon insuffisant"
    expected_columns = ['SiteID', 'timestamp', 'Model_Name', 'Actual_PM25', 'Predicted_PM25', 'Forecast_Type']
    assert list(res.columns) == expected_columns, f"Les colonnes du DataFrame de retour devraient être {expected_columns}"

def test_feature_engineering():
    df_valid = pd.DataFrame({
        'SiteID': ['S1']*300,
        'timestamp': pd.date_range(start='2024-01-01', periods=300, freq='H'),
        'PM25': np.arange(1, 301)
    })
    res = forecast_hourly(df_valid)
    assert res.loc[5, 'PM25'] == 6.0
    assert res.loc[5, 'lag_1'] == 5.0, "Le calcul du lag_1 est incorrect"
    assert pd.isna(res.loc[0, 'lag_1']), "La première ligne devrait avoir un lag_1 NaN"

def test_cyclic_features():
    df_time = pd.DataFrame({
        'SiteID': ['S1']*300,
        'timestamp': pd.date_range(start='2024-01-01 00:00:00', periods=300, freq='H'),
        'PM25': np.ones(300)
    })
    res = forecast_hourly(df_time)
    assert np.isclose(res.loc[0, 'hour_sin'], 0.0), "Le calcul de hour_sin pour 00:00 est incorrect"
    assert np.isclose(res.loc[6, 'hour_sin'], 1.0), "Le calcul de hour_sin pour 06:00 est incorrect"
    assert np.isclose(res.loc[12, 'hour_sin'], 0.0), "Le calcul de hour_sin pour 12:00 est incorrect"
    assert np.isclose(res.loc[18, 'hour_sin'], -1.0), "Le calcul de hour_sin pour 18:00 est incorrect"

def test_unordered_data():
    df_ordered = pd.DataFrame({
        'SiteID': ['S1']*300,
        'timestamp': pd.date_range(start='2024-01-01', periods=300, freq='H'),
        'PM25': np.arange(1, 301)
    })
    df_shuffle = df_ordered.sample(frac=1, random_state=42)
    res = forecast_hourly(df_shuffle)
    assert res.loc[0, 'PM25'] == 1.0, "La fonction n'a pas trié les données chronologiquement"
    assert res.loc[1, 'lag_1'] == 1.0, "Le lag a été calculé sur des données non triées"

def test_strict_datatypes():
    df_panne = pd.DataFrame({'SiteID': ['S1']})
    res = forecast_hourly(df_panne)
    assert res.empty
    assert pd.api.types.is_float_dtype(res['Actual_PM25']), "Actual_PM25 devrait être de type float64 pour Spark"
    assert pd.api.types.is_float_dtype(res['Predicted_PM25']), "Predicted_PM25 devrait être de type float64"
    assert pd.api.types.is_datetime64_any_dtype(res['timestamp']), "timestamp doit être un datetime"

def test_missing_value_in_series():
    df_holes = pd.DataFrame({
        'SiteID': ['S1']*300,
        'timestamp': pd.date_range(start='2024-01-01', periods=300, freq='H'),
        'PM25': np.ones(300)
    })
    df_holes.loc[9, 'PM25'] = np.nan
    res = forecast_hourly(df_holes)
    assert pd.isna(res.loc[10, 'lag_1']), "Le lag _1 n'a pas détecté le trou de donnée précédent"


if __name__ == "__main__":
    import pytest
    import sys
    sys.exit(pytest.main(["-v", __file__]))