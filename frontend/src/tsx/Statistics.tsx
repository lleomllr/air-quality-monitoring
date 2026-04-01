import { useCallback, useEffect, useState } from 'react';
import '../css/Statistics.css';
import Navbar from './Navbar';

interface HourlyIndicator {
    city: string;
    state: string;
    pollutant: string;
    aqi: number;
    timeStart: string;
}

interface Anomaly {
    id?: number;
    city?: string;
    state?: string;
    pollutant?: string;
    aqi?: number;
    alertLevel?: string;
    message?: string;
    time?: string;
}

interface AnomalyApiItem {
    id?: number;
    city?: string;
    state?: string;
    pollutant?: string;
    aqi?: number;
    concentration?: number;
    time?: string;
}

interface TopKItem {
    stationId: string;
    aqi: number;
    pollutant: string;
}

const Statistics = () => {
    const [hourlyIndicators, setHourlyIndicators] = useState<HourlyIndicator[]>([]);
    const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
    const [topKGlobal, setTopKGlobal] = useState<TopKItem[]>([]);
    const [loading, setLoading] = useState(true);

    const fetchStatistics = useCallback(async () => {
        try {
            // Plus besoin du token obligatoirement car on récupère les données globales (publiques)

            // MODIFICATION ICI : On appelle /alerts/all au lieu de /alerts
            const [indicatorsRes, anomaliesRes, topKRes] = await Promise.all([
                fetch('/api/aqi/cities/hourly?hours=24'),
                fetch('/api/aqi/alerts/all?hours=24'),
                fetch('/api/aqi/topk/global?limit=10')
            ]);

            if (indicatorsRes.ok) {
                const indicatorsData = await indicatorsRes.json();
                const arr = Array.isArray(indicatorsData) ? indicatorsData : (indicatorsData.content || []);
                setHourlyIndicators(arr.slice(0, 20));
            }

            if (anomaliesRes.ok) {
                const anomaliesData = await anomaliesRes.json();
                const arr = Array.isArray(anomaliesData) ? anomaliesData : (anomaliesData.content || []);

                const formattedAnomalies: Anomaly[] = (arr as AnomalyApiItem[]).map((item, index: number) => {
                    const aqiValue = item.aqi ?? 0;

                    let severity = 'MEDIUM';
                    if (aqiValue > 200) severity = 'CRITICAL';
                    else if (aqiValue > 150) severity = 'HIGH';
                    else if (aqiValue <= 50) severity = 'LOW';

                    return {
                        id: item.id || index,
                        city: item.city,
                        state: item.state,
                        pollutant: item.pollutant,
                        aqi: aqiValue,
                        alertLevel: severity,
                        message: `High concentration of ${item.pollutant} (${item.concentration ? item.concentration.toFixed(1) : 0} ug/m3).`,
                        time: item.time
                    };
                });

                // On peut afficher un peu plus d'anomalies (ex: 50) puisqu'on affiche tout le pays
                setAnomalies(formattedAnomalies.slice(0, 50));
            }

            if (topKRes.ok) {
                const topKData: unknown = await topKRes.json();
                const arr = Array.isArray(topKData) ? (topKData as TopKItem[]) : [];
                setTopKGlobal(arr.slice(0, 10));
            }
        } catch (err) {
            console.error('Erreur lors de la récupération des statistiques:', err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchStatistics();

        const intervalId = window.setInterval(() => {
            fetchStatistics();
        }, 5000);

        return () => {
            window.clearInterval(intervalId);
        };
    }, [fetchStatistics]);

    const getSeverityColor = (severity: string) => {
        switch ((severity || '').toUpperCase()) {
            case 'LOW': return '#00e400';
            case 'MEDIUM': return '#ffff00';
            case 'HIGH': return '#ff7e00';
            case 'CRITICAL': return '#ff0000';
            default: return 'gray';
        }
    };

    if (loading) return (
        <div className="statistics">
            <Navbar />
            <div className="loading">Loading statistics...</div>
        </div>
    );

    return (
        <div className="statistics">
            <Navbar />
            <main className="statistics-content">
                <h1>Statistics and Analysis</h1>

                {/* Section Hourly Indicators */}
                <section className="stats-section">
                    <h2>Hourly Indicators</h2>
                    <div className="stats-summary">
                        <div className="summary-card">
                            <h3>{hourlyIndicators.length}</h3>
                            <p>Available Indicators</p>
                        </div>
                        <div className="summary-card">
                            <h3>{anomalies.length}</h3>
                            <p>Anomalies Detected (National)</p>
                        </div>
                        <div className="summary-card">
                            <h3>{topKGlobal.length}</h3>
                            <p>Top AQI Stations</p>
                        </div>
                    </div>
                    {hourlyIndicators.length > 0 ? (
                        <div className="table-container scrollable-table">
                            <table className="stats-table">
                                <thead>
                                <tr>
                                    <th>Location</th>
                                    <th>State</th>
                                    <th>Pollutant</th>
                                    <th>AQI</th>
                                    <th>Recording Date</th>
                                </tr>
                                </thead>
                                <tbody>
                                {hourlyIndicators.map((indicator, idx) => (
                                    <tr key={idx}>
                                        <td>{indicator.city || 'Unknown'}</td>
                                        <td>{indicator.state || 'Unknown'}</td>
                                        <td><span className="pollutant-badge">{indicator.pollutant || 'N/A'}</span></td>
                                        <td style={{ fontWeight: 'bold' }}>{indicator.aqi !== undefined && indicator.aqi !== null ? indicator.aqi : 'N/A'}</td>
                                        <td>{indicator.timeStart ? new Date(indicator.timeStart).toLocaleString('en-US') : 'Unknown date'}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    ) : (
                        <div className="no-data">No hourly indicators available</div>
                    )}
                </section>
                {/* Section Anomalies */}
                <section className="stats-section">
                    <h2>Detected Anomalies (National Level)</h2>
                    {anomalies.length > 0 ? (
                        <div className="anomalies-compact-list">
                            {anomalies.map((anomaly, idx) => {
                                const severityColor = getSeverityColor(anomaly.alertLevel || '');

                                return (
                                    <div
                                        key={anomaly.id || idx}
                                        className="anomaly-compact-item"
                                        style={{ borderLeftColor: severityColor }}
                                    >
                                        <div className="anomaly-compact-main">
                                            <div className="anomaly-compact-header">
                                                <span className="anomaly-compact-city">
                                                    {anomaly.city ? `${anomaly.city}, ${anomaly.state}` : 'Unknown location'}
                                                </span>
                                                <span className="anomaly-compact-time">
                                                    {anomaly.time ? new Date(anomaly.time).toLocaleString('en-US', { hour: '2-digit', minute:'2-digit', month:'short', day:'numeric' }) : 'Recently'}
                                                </span>
                                            </div>
                                            <div className="anomaly-compact-details">
                                                <span className="pollutant-badge mini">{anomaly.pollutant || 'N/A'}</span>
                                                <span className="anomaly-compact-desc">{anomaly.message || 'Abnormal air quality.'}</span>
                                            </div>
                                        </div>
                                        <div className="anomaly-compact-aqi" style={{ color: severityColor }}>
                                            <span className="aqi-val">{anomaly.aqi || 'N/A'}</span>
                                            <span className="aqi-lbl">AQI</span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        <div className="no-data">No anomalies detected recently in the country.</div>
                    )}
                </section>
                {/* Section TopK Global AQI */}
                <section className="stats-section">
                    <h2>Top 10 AQI Global (Most Polluted Stations)</h2>
                    {topKGlobal.length > 0 ? (
                        <div className="topk-container">
                            <div className="table-container scrollable-table">
                            {topKGlobal.map((item, idx) => {
                                const aqiValue = Math.round(item.aqi ?? 0);
                                const aqiColor = getSeverityColor(
                                    aqiValue > 200 ? 'CRITICAL' :
                                        aqiValue > 150 ? 'HIGH' :
                                            aqiValue > 50 ? 'MEDIUM' : 'LOW'
                                );
                                // On calcule un pourcentage pour la barre (max 300 pour l'échelle)
                                const fillPercentage = Math.min((aqiValue / 300) * 100, 100);

                                return (
                                    <div key={`${item.stationId}-${idx}`} className="topk-row">
                                        <div className="topk-rank">
                                            <span className={`rank-number ${idx < 3 ? `podium-${idx + 1}` : ''}`}>
                                                #{idx + 1}
                                            </span>
                                        </div>
                                        <div className="topk-info">
                                            <h4>{item.stationId || 'Unknown Station'}</h4>
                                            <span className="pollutant-badge">{item.pollutant || 'N/A'}</span>
                                        </div>
                                        <div className="topk-bar-container">
                                            <div className="topk-bar-bg">
                                                <div
                                                    className="topk-bar-fill"
                                                    style={{
                                                        width: `${fillPercentage}%`,
                                                        backgroundColor: aqiColor
                                                    }}
                                                ></div>
                                            </div>
                                        </div>
                                        <div className="topk-score" style={{ color: aqiColor }}>
                                            {aqiValue} AQI
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                        </div>
                    ) : (
                        <div className="no-data">TopK unavailable at the moment.</div>
                    )}
                </section>
            </main>
        </div>
    );
};

export default Statistics;

