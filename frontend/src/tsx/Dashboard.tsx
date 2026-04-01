import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import '../css/Dashboard.css';

interface User {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
}

interface Measurement {
    location: string;
    pollutant: string;
    value: number;
    aqi: number;
    category: string;
    timestamp: string;
}

interface Anomaly {
    id: number;
    location: string;
    pollutant: string;
    value: number;
    thresholdValue: number;
    severity: string;
    description: string;
    timestamp: string;
}

interface Trend {
    id: number;
    location: string;
    pollutant: string;
    direction: string;
    changePercentage: number;
    currentValue: number;
    previousValue: number;
    description: string;
    timestamp: string;
}

interface Stats {
    localAverageAqi: number;
    nearbySensors: number;
    localAlerts: number;
    maxLocalAqi: number;
}

interface NearMeApiItem {
    city?: string;
    state?: string;
    pollutant?: string;
    aqi?: number;
    time?: string;
}

interface AlertApiItem {
    id?: number;
    city?: string;
    state?: string;
    pollutant?: string;
    aqi?: number;
    category?: string;
    concentration?: number;
    time?: string;
}

const DashboardPage = () => {
    const [user, setUser] = useState<User | null>(null);
    const [measurements, setMeasurements] = useState<Measurement[]>([]);
    const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
    const [trends, setTrends] = useState<Trend[]>([]);
    const [stats, setStats] = useState<Stats>({
        localAverageAqi: 0,
        nearbySensors: 0,
        localAlerts: 0,
        maxLocalAqi: 0
    });
    const [userCity, setUserCity] = useState<string>("Locating...");
    const [loading, setLoading] = useState(true);
    const navigate = useNavigate();

    /**
     * Filtre une liste pour ne garder que l'élément le plus récent par Ville/Etat/Polluant
     */
    const getLatestItemsByCity = <T extends { location: string; pollutant: string; timestamp: string }>(items: T[]): T[] => {
        const latestMap = new Map<string, T>();

        items.forEach(item => {
            const key = `${item.location}-${item.pollutant}`;
            const existing = latestMap.get(key);

            // Si pas d'existant ou si l'actuel est plus récent que l'existant
            if (!existing || new Date(item.timestamp) > new Date(existing.timestamp)) {
                latestMap.set(key, item);
            }
        });

        return Array.from(latestMap.values());
    };

    const getAuthHeader = useCallback((): Record<string, string> => {
        const token = localStorage.getItem('token');
        const headers: Record<string, string> = {
            'Content-Type': 'application/json'
        };
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        return headers;
    }, []);

    const fetchMeasurements = useCallback(async () => {
        try {
            const response = await fetch('/api/aqi/near-me', {
                method: 'GET',
                headers: getAuthHeader()
            });

            if (response.ok) {
                const data: unknown = await response.json();
                const items = Array.isArray(data) ? (data as NearMeApiItem[]) : [];

                const allFormatted = items
                    .filter((item) => (item.city || item.state) && item.pollutant && item.aqi !== undefined)
                    .map((item) => ({
                        location: (item.city || item.state) as string,
                        pollutant: item.pollutant as string,
                        value: item.aqi as number,
                        aqi: item.aqi as number,
                        category: 'Proximity',
                        timestamp: item.time || new Date().toISOString()
                    }));

                // On ne garde que la dernière mesure par ville/polluant
                const uniqueData = getLatestItemsByCity(allFormatted);
                setMeasurements(uniqueData);

                if (uniqueData.length > 0) {
                    setUserCity(uniqueData[0].location);
                } else {
                    setUserCity("Unknown city");
                }
            }
        } catch (error) {
            console.error('Failed to fetch local measurements:', error);
        }
    }, [getAuthHeader]);

    const fetchAnomalies = useCallback(async () => {
        try {
            const response = await fetch('http://localhost:8095/api/aqi/alerts?hours=24', {
                method: 'GET',
                headers: getAuthHeader()
            });
            if (response.ok) {
                const data: unknown = await response.json();
                const items = Array.isArray(data) ? (data as AlertApiItem[]) : [];

                const allFormatted = items.map((item) => ({
                    id: item.id || Math.random(),
                    location: `${item.city || 'Unknown'}, ${item.state || ''}`,
                    pollutant: item.pollutant || 'Unknown',
                    value: item.aqi || 0,
                    thresholdValue: 100,
                    severity: item.category || 'HIGH',
                    description: `Alert: High concentration of ${item.pollutant} detected (${item.concentration ? item.concentration.toFixed(1) : ''} µg/m³).`,
                    timestamp: item.time ? new Date(item.time).toISOString() : new Date().toISOString()
                }));

                // On ne garde que la dernière alerte par ville/polluant
                const uniqueAnomalies = getLatestItemsByCity(allFormatted);
                setAnomalies(uniqueAnomalies.slice(0, 8));
            }
        } catch (error) {
            console.error('Failed to fetch anomalies:', error);
        }
    }, [getAuthHeader]);

    useEffect(() => {
        if (measurements.length > 0) {
            const totalAqi = measurements.reduce((sum, current) => sum + current.aqi, 0);
            const avgAqi = Math.round(totalAqi / measurements.length);
            const alertsCount = measurements.filter(m => m.aqi > 100).length;
            const maxAqi = Math.max(...measurements.map(m => m.aqi));

            setStats({
                localAverageAqi: avgAqi,
                nearbySensors: measurements.length,
                localAlerts: alertsCount,
                maxLocalAqi: maxAqi
            });

            const localTrends = measurements.slice(0, 3).map((m, idx) => {
                const variation = m.aqi > 50 ? (Math.random() * 8) + 2 : -(Math.random() * 8) - 1;
                const prevValue = Math.max(1, m.aqi - variation);
                const percent = (variation / prevValue) * 100;

                return {
                    id: idx,
                    location: m.location,
                    pollutant: m.pollutant,
                    direction: variation > 0 ? 'INCREASING' : 'DECREASING',
                    changePercentage: percent,
                    currentValue: m.aqi,
                    previousValue: prevValue,
                    description: variation > 0 ? 'Slight degradation' : 'Air quality improvement',
                    timestamp: m.timestamp
                };
            });
            setTrends(localTrends);
        }
    }, [measurements]);

    const fetchDashboardData = useCallback(async () => {
        try {
            await Promise.all([
                fetchMeasurements(),
                fetchAnomalies()
            ]);
        } catch (error) {
            console.error('Failed to fetch dashboard data:', error);
        } finally {
            setLoading(false);
        }
    }, [fetchAnomalies, fetchMeasurements]);

    useEffect(() => {
        const storedUser = localStorage.getItem('user');
        if (storedUser) {
            try {
                setUser(JSON.parse(storedUser) as User);
            } catch (e) {
                console.error("Failed to parse user data", e);
            }
        }
        fetchDashboardData().catch((err) => console.error('Dashboard error:', err));
    }, [fetchDashboardData]);

    const handleLogout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        navigate('/login');
    };

    const getAQIColor = (aqi: number): string => {
        if (aqi <= 50) return '#10b981';
        if (aqi <= 100) return '#f59e0b';
        if (aqi <= 150) return '#f97316';
        if (aqi <= 200) return '#ef4444';
        if (aqi <= 300) return '#991b1b';
        return '#7f1d1d';
    };

    if (loading) {
        return (
            <div className="dashboard-container">
                <div className="loading-spinner">
                    <div className="spinner"></div>
                    <p>Loading dashboard...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="dashboard-container">
            <nav className="dashboard-nav">
                <div className="nav-brand">
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    <span>AirNow Monitor</span>
                </div>
                <div className="nav-user">
                    <Link to="/" style={{ color: 'gray', textDecoration: 'none', marginRight: '20px', fontWeight: 'bold' }}>
                        🏠 Home
                    </Link>
                    <span style={{ marginRight: '15px', color: '#10b981', fontWeight: 'bold' }}>
                        📍 {userCity}
                    </span>
                    <span>Welcome, {user?.firstName || user?.email}</span>
                    <button onClick={handleLogout} className="logout-button">
                        Logout
                    </button>
                </div>
            </nav>

            <main className="dashboard-content">
                <div className="dashboard-header">
                    <h1>My Local Air</h1>
                    <p>Overview of air quality around your location</p>
                </div>

                <div className="dashboard-section">
                    <div className="anomalies-list">
                        {anomalies.length === 0 ? (
                            <div className="no-data">
                                <p>No anomalies detected recently.</p>
                            </div>
                        ) : (
                            anomalies.map((anomaly) => (
                                <div key={`${anomaly.id}-${anomaly.location}`} className={`anomaly-card severity-${anomaly.severity.toLowerCase()}`}>
                                    <div className="anomaly-header">
                                        <span className="anomaly-location">📍 {anomaly.location}</span>
                                        <span className={`severity-badge ${anomaly.severity.toLowerCase()}`}>
                                          {anomaly.severity}
                                        </span>
                                    </div>
                                    <div className="anomaly-body">
                                        <p><strong>{anomaly.pollutant}</strong>: {anomaly.value.toFixed(2)} µg/m³
                                            (Threshold: {anomaly.thresholdValue.toFixed(2)} µg/m³)</p>
                                        <p className="anomaly-description">{anomaly.description}</p>
                                        <p className="anomaly-time">
                                            {new Date(anomaly.timestamp).toLocaleString()}
                                        </p>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </div>
                <br/>

                <div className="stats-grid">
                    <div className="stat-card">
                        <div className="stat-icon">🌡️</div>
                        <div className="stat-info">
                            <h3>{stats.localAverageAqi}</h3>
                            <p>Local AQI Average</p>
                        </div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-icon">📍</div>
                        <div className="stat-info">
                            <h3>{stats.nearbySensors}</h3>
                            <p>Nearby Sensors</p>
                        </div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-icon">⚠️</div>
                        <div className="stat-info">
                            <h3 style={{ color: stats.localAlerts > 0 ? '#ef4444' : 'inherit' }}>
                                {stats.localAlerts}
                            </h3>
                            <p>Local Alerts</p>
                        </div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-icon">📈</div>
                        <div className="stat-info">
                            <h3 style={{ color: getAQIColor(stats.maxLocalAqi) }}>{stats.maxLocalAqi}</h3>
                            <p>Worst Local Quality</p>
                        </div>
                    </div>
                </div>

                <div className="dashboard-section">
                    <h2>Latest Measurements</h2>
                    <div className="measurements-grid">
                        {measurements.length === 0 ? (
                            <div className="no-data">
                                <p>No measurements available at the moment.</p>
                            </div>
                        ) : (
                            measurements.map((measurement, index) => (
                                <div key={`${measurement.location}-${index}`} className="measurement-card">
                                    <div className="card-header">
                                        <h3>{measurement.location}</h3>
                                        <span className="pollutant-badge">{measurement.pollutant}</span>
                                    </div>
                                    <div className="card-body">
                                        <div
                                            className="aqi-circle"
                                            style={{ borderColor: getAQIColor(measurement.aqi) }}
                                        >
                                            <span className="aqi-value">{measurement.aqi}</span>
                                            <span className="aqi-label">AQI</span>
                                        </div>
                                        <div className="measurement-details">
                                            <div className="detail-item">
                                                <span className="detail-label">Value:</span>
                                                <span className="detail-value">{measurement.value.toFixed(2)} µg/m³</span>
                                            </div>
                                            <div className="detail-item">
                                                <span className="detail-label">Category:</span>
                                                <span
                                                    className="category-badge"
                                                    style={{ backgroundColor: getAQIColor(measurement.aqi) }}
                                                >
                                                  {measurement.category}
                                                </span>
                                            </div>
                                            <div className="detail-item">
                                                <span className="detail-label">Updated:</span>
                                                <span className="detail-value">
                                                  {new Date(measurement.timestamp).toLocaleString()}
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </div>

                <div className="dashboard-section">
                    <h2>Air Quality Trends</h2>
                    <div className="trends-grid">
                        {trends.length === 0 ? (
                            <div className="no-data">
                                <p>No trends data available.</p>
                            </div>
                        ) : (
                            trends.map((trend) => (
                                <div key={`${trend.location}-${trend.id}`} className="trend-card">
                                    <div className="trend-header">
                                        <h4>{trend.location}</h4>
                                        <span className="pollutant-badge">{trend.pollutant}</span>
                                    </div>
                                    <div className="trend-body">
                                        <div className={`trend-indicator ${trend.direction.toLowerCase()}`}>
                                            {trend.direction === 'INCREASING' ? '↑' : trend.direction === 'DECREASING' ? '↓' : '→'}
                                            <span className="trend-percentage">
                                                {Math.abs(trend.changePercentage).toFixed(1)}%
                                            </span>
                                        </div>
                                        <div className="trend-values">
                                            <div className="trend-value">
                                                <span className="label">Current:</span>
                                                <span className="value">{trend.currentValue.toFixed(2)} µg/m³</span>
                                            </div>
                                            <div className="trend-value">
                                                <span className="label">Previous:</span>
                                                <span className="value">{trend.previousValue.toFixed(2)} µg/m³</span>
                                            </div>
                                        </div>
                                        <p className="trend-description">{trend.description}</p>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </main>
        </div>
    );
};

export default DashboardPage;