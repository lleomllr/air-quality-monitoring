import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from sklearn.preprocessing import MinMaxScaler
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout
from tensorflow.keras.callbacks import EarlyStopping
import warnings
warnings.filterwarnings('ignore')

print("Chargement et Préparation des Données")
df = pd.read_csv('../../data/LSTM_dataset_California_Top20.csv')
df['timestamp'] = pd.to_datetime(df['timestamp'])

df_pivot = df.pivot_table(index='timestamp', columns='SiteID', values='PM25')
df_pivot = df_pivot.fillna(method='ffill').fillna(method='bfill')
print(f"Dimensions du dataset : {df_pivot.shape[0]} heures, {df_pivot.shape[1]} capteurs.")

print("\nScaling")
cutoff = pd.to_datetime('2025-10-01')
train_mask = df_pivot.index < cutoff
scaler = MinMaxScaler()
scaler.fit(df_pivot[train_mask])
scaled_data = scaler.transform(df_pivot)

print("\nCréation des Séquences (Tenseurs 3D)")
SEQ_LENGTH = 24 

def create_sequences(data, seq_length):
    X, y = [], []
    for i in range(len(data) - seq_length):
        X.append(data[i:(i + seq_length)]) 
        y.append(data[i + seq_length])     
    return np.array(X), np.array(y)

X, y = create_sequences(scaled_data, SEQ_LENGTH)
dates = df_pivot.index[SEQ_LENGTH:] 

print(f"Forme de X (Entrées) : {X.shape} -> (Échantillons, Heures, Capteurs)")
print(f"Forme de y (Cibles)  : {y.shape} -> (Échantillons, Capteurs)")

print("\nTrain / Test Split (Cut : 1er Octobre 2025)")
train_idx = dates < cutoff
test_idx = dates >= cutoff

X_train, y_train = X[train_idx], y[train_idx]
X_test, y_test = X[test_idx], y[test_idx]
dates_test = dates[test_idx]

print(f"X_train shape : {X_train.shape}")
print(f"X_test shape : {X_test.shape}")

print("\nRéseau de Neurones LSTM")
model = Sequential([
    LSTM(64, return_sequences=True, input_shape=(X_train.shape[1], X_train.shape[2])),
    Dropout(0.2), 
    LSTM(32),
    Dropout(0.2),
    Dense(y_train.shape[1]) 
])

model.compile(optimizer='adam', loss='mse')
model.summary()

print("\nEntraînement du Modèle")
earlystop = EarlyStopping(
    monitor='val_loss', 
    patience=5, 
    restore_best_weights=True
)

history = model.fit(
    X_train, y_train, 
    epochs=100, 
    batch_size=32, 
    validation_split=0.1, 
    callbacks=[earlystop],
    verbose=1
)

print("\nÉvaluation")
y_pred_scaled = model.predict(X_test)
y_pred = scaler.inverse_transform(y_pred_scaled)
y_test_real = scaler.inverse_transform(y_test)

mae_global = mean_absolute_error(y_test_real, y_pred)
rmse_global = np.sqrt(mean_squared_error(y_test_real, y_pred))
mse_global = mean_squared_error(y_test_real, y_pred)
r2_global = r2_score(y_test_real, y_pred)
print(f"\nMAE GLOBAL LSTM (Test Post-Oct 2025) : {mae_global:.2f} µg/m³")
print(f"\nRMSE GLOBAL LSTM (Test Post-Oct 2025) : {rmse_global:.2f} µg/m³")
print(f"\nMSE GLOBAL LSTM (Test Post-Oct 2025) : {mse_global:.2f} µg/m³")
print(f"\nR-SQUARED GLOBAL LSTM (Test Post-Oct 2025) : {r2_global:.4f}")

sensor_to_plot = 0 
sensor_id = df_pivot.columns[sensor_to_plot]

mae_sensor = mean_absolute_error(y_test_real[:, sensor_to_plot], y_pred[:, sensor_to_plot])
mse_sensor = mean_squared_error(y_test_real[:, sensor_to_plot], y_pred[:, sensor_to_plot])
rmse_sensor = np.sqrt(mean_squared_error(y_test_real[:, sensor_to_plot], y_pred[:, sensor_to_plot]))
r2_sensor = r2_score(y_test_real[:, sensor_to_plot], y_pred[:, sensor_to_plot])

mask = (dates_test >= '2025-11-10') & (dates_test <= '2025-11-21')

plt.figure(figsize=(16, 7))
plt.plot(dates_test[mask], y_test_real[mask, sensor_to_plot], label='Réalité', color='black', linewidth=2)
label_pred = f"LSTM (MAE: {mae_sensor:.2f} | RMSE: {rmse_sensor:.2f} | R²: {r2_sensor:.4f})"
plt.plot(dates_test[mask], y_pred[mask, sensor_to_plot], label=label_pred, color='purple', linestyle='--', linewidth=2)

plt.title(f"LSTM - Capteur Californie {sensor_id}", fontsize=16, fontweight='bold')
plt.ylabel("PM2.5 (µg/m³)")
plt.legend(loc='upper right')
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig(f"LSTM_prediction_{sensor_id}.png", dpi=300)
print(f"\nGraphique de prédiction sauvegardé sous LSTM_prediction_{sensor_id}.png")

plt.figure(figsize=(10, 6))
plt.plot(history.history['loss'], label='Train Loss', color='blue', linewidth=2)
plt.plot(history.history['val_loss'], label='Val Loss', color='orange', linewidth=2)
plt.title('Loss Curve (Vérification de l\'Overfitting)', fontsize=14, fontweight='bold')
plt.ylabel('Loss (MSE)')
plt.xlabel('Epochs')
plt.legend()
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig("LSTM_learning_curve.png", dpi=300)
print("Graphique de la courbe d'apprentissage sauvegardé")