/**
 * @vitest-environment jsdom
 */
import type { GeoJsonObject } from 'geojson';
import L from 'leaflet';
import 'leaflet.heat/dist/leaflet-heat.js';
import 'leaflet/dist/leaflet.css';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { CircleMarker, GeoJSON, MapContainer, Popup, TileLayer, useMap } from 'react-leaflet';
import '../css/AirQuality.css';
import Navbar from './Navbar';

// Interface pour typer nos données de mesure
interface Measurement {
    id: number;
    timestamp: string;
    location: string;
    state?: string;
    pollutant: string;
    value: number;
    unit: string;
    aqi: number | null;
    category: string | null;
    longitude?: number;
    latitude?: number;
}

type MapMode = 'heatmap' | 'points' | 'choropleth';

interface HeatPoint {
    latitude: number;
    longitude: number;
    intensity: number;
}

interface StateAggregate {
    avgAqi: number;
    count: number;
}

interface AqiLegendRange {
    label: string;
    min: number;
    max: number;
}

// Interface pour étendre Leaflet avec le plugin HeatLayer
interface HeatLayerFactory {
    heatLayer: (
        points: Array<[number, number, number]>,
        options: {
            radius: number;
            blur: number;
            maxZoom: number;
            minOpacity: number;
            gradient: Record<number, string>;
        }
    ) => L.Layer;
}

// Interfaces pour le GeoJSON des états US
interface GeoJsonFeatureProperties {
    name?: string;
}

interface GeoJsonFeature {
    type: string;
    properties?: GeoJsonFeatureProperties;
    // Remplacement de 'any' par 'unknown' pour satisfaire le linter
    geometry: unknown;
}

// Interface pour les données brutes de l'API
interface MeasurementApiItem {
    id?: number;
    timestamp?: string;
    time?: string;
    created_at?: string;
    location?: string;
    city?: string;
    state?: string;
    pollutant?: string;
    value?: number;
    concentration?: number;
    aqi?: number;
    unit?: string;
    category?: string;
    longitude?: number;
    latitude?: number;
}

const STATE_NAME_TO_CODE: Record<string, string> = {
    Alabama: 'AL', Alaska: 'AK', Arizona: 'AZ', Arkansas: 'AR', California: 'CA', Colorado: 'CO',
    Connecticut: 'CT', Delaware: 'DE', 'District of Columbia': 'DC', Florida: 'FL', Georgia: 'GA',
    Hawaii: 'HI', Idaho: 'ID', Illinois: 'IL', Indiana: 'IN', Iowa: 'IA', Kansas: 'KS', Kentucky: 'KY',
    Louisiana: 'LA', Maine: 'ME', Maryland: 'MD', Massachusetts: 'MA', Michigan: 'MI', Minnesota: 'MN',
    Mississippi: 'MS', Missouri: 'MO', Montana: 'MT', Nebraska: 'NE', Nevada: 'NV', 'New Hampshire': 'NH',
    'New Jersey': 'NJ', 'New Mexico': 'NM', 'New York': 'NY', 'North Carolina': 'NC', 'North Dakota': 'ND',
    Ohio: 'OH', Oklahoma: 'OK', Oregon: 'OR', Pennsylvania: 'PA', 'Rhode Island': 'RI',
    'South Carolina': 'SC', 'South Dakota': 'SD', Tennessee: 'TN', Texas: 'TX', Utah: 'UT', Vermont: 'VT',
    Virginia: 'VA', Washington: 'WA', 'West Virginia': 'WV', Wisconsin: 'WI', Wyoming: 'WY'
};

const AQI_LEGEND_RANGES: AqiLegendRange[] = [
    { label: 'Good', min: 0, max: 50 },
    { label: 'Moderate', min: 51, max: 100 },
    { label: 'Unhealthy for sensitive groups', min: 101, max: 150 },
    { label: 'Unhealthy', min: 151, max: 200 },
    { label: 'Very Unhealthy', min: 201, max: 300 },
    { label: 'Hazardous', min: 301, max: 500 }
];

const extractStateCode = (measurement: Measurement): string | null => {
    if (measurement.state && measurement.state.length === 2) {
        return measurement.state.toUpperCase();
    }
    const locationParts = measurement.location.split(',');
    const lastPart = locationParts[locationParts.length - 1]?.trim();
    if (lastPart && lastPart.length === 2) {
        return lastPart.toUpperCase();
    }
    return null;
};

const HeatmapLayer = ({ points }: { points: HeatPoint[] }) => {
    const map = useMap();

    useEffect(() => {
        if (points.length === 0) return;

        const heatLayerFactory = L as unknown as HeatLayerFactory;
        const layer = heatLayerFactory.heatLayer(
            points.map((point) => [point.latitude, point.longitude, point.intensity]),
            {
                radius: 26,
                blur: 22,
                maxZoom: 8,
                minOpacity: 0.35,
                gradient: {
                    0.2: '#00e400',
                    0.4: '#ffff00',
                    0.6: '#ff7e00',
                    0.8: '#ff0000',
                    1.0: '#7e0023'
                }
            }
        );

        layer.addTo(map);

        return () => {
            map.removeLayer(layer);
        };
    }, [map, points]);

    return null;
};

const AirQuality = () => {
    const [measurements, setMeasurements] = useState<Measurement[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [selectedLocation, setSelectedLocation] = useState<string>('all');
    const [selectedPollutant, setSelectedPollutant] = useState<string>('all');
    const [mapMode, setMapMode] = useState<MapMode>('heatmap');
    const [statesGeoJson, setStatesGeoJson] = useState<GeoJsonObject | null>(null);

    const fetchMeasurements = useCallback(async (retryCount = 0) => {
        const token = localStorage.getItem('token');
        const headers: HeadersInit = {
            'Content-Type': 'application/json'
        };

        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        try {
            const response = await fetch('/api/aqi/cities/latest/geo?limit=300', {
                headers,
                mode: 'cors'
            });

            if (!response.ok) {
                if (response.status === 403 && retryCount === 0) {
                    await new Promise(resolve => setTimeout(resolve, 500));
                    return fetchMeasurements(1);
                }
                throw new Error(`Error fetching data (${response.status})`);
            }

            const data: unknown = await response.json();
            let rawData: MeasurementApiItem[] = [];

            // Cast strict au lieu de (data as any).content
            if (Array.isArray(data)) {
                rawData = data as MeasurementApiItem[];
            } else if (
                data &&
                typeof data === 'object' &&
                'content' in data &&
                Array.isArray((data as { content: unknown }).content)
            ) {
                rawData = (data as { content: MeasurementApiItem[] }).content;
            }

            const finalData: Measurement[] = rawData.map((item, index) => ({
                id: item.id || index,
                timestamp: item.timestamp || item.time || item.created_at || new Date().toISOString(),
                location: item.location || item.city || 'Unknown location',
                state: item.state,
                pollutant: item.pollutant || 'O3',
                value: item.value || item.concentration || item.aqi || 0,
                unit: item.unit || 'AQI',
                aqi: item.aqi || null,
                category: item.category || null,
                longitude: item.longitude,
                latitude: item.latitude
            }));

            setMeasurements(finalData);
            setError(null);
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : 'Unknown error';
            console.error('Error:', err);
            setError(message);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchMeasurements();
    }, [fetchMeasurements]);

    useEffect(() => {
        const fetchStatesGeoJson = async () => {
            try {
                const response = await fetch('https://raw.githubusercontent.com/PublicaMundi/MappingAPI/master/data/geojson/us-states.json');
                if (!response.ok) throw new Error('Failed to load state boundaries');
                const data: unknown = await response.json();
                setStatesGeoJson(data as GeoJsonObject);
            } catch (err) {
                console.error('GeoJSON error:', err);
            }
        };
        fetchStatesGeoJson();
    }, []);

    const locations = useMemo(() => {
        const locs = measurements.map(m => m.location);
        return ['all', ...Array.from(new Set(locs))].sort();
    }, [measurements]);

    const pollutants = useMemo(() => {
        const pols = measurements.map(m => m.pollutant);
        return ['all', ...Array.from(new Set(pols))].sort();
    }, [measurements]);

    const getAQIColor = (aqi: number | null) => {
        if (!aqi) return '#808080';
        if (aqi <= 50) return '#00e400';
        if (aqi <= 100) return '#ffff00';
        if (aqi <= 150) return '#ff7e00';
        if (aqi <= 200) return '#ff0000';
        if (aqi <= 300) return '#8f3f97';
        return '#7e0023';
    };

    const filteredMeasurements = measurements.filter(m => {
        const locationMatch = selectedLocation === 'all' || m.location === selectedLocation;
        const pollutantMatch = selectedPollutant === 'all' || m.pollutant === selectedPollutant;
        return locationMatch && pollutantMatch;
    });

    const mapMeasurements = filteredMeasurements.filter(
        (m) => typeof m.latitude === 'number' && typeof m.longitude === 'number'
    );

    const heatPoints = useMemo<HeatPoint[]>(() => {
        if (mapMeasurements.length === 0) return [];
        const rawIntensities = mapMeasurements.map((m) => Math.max(Number(m.aqi ?? m.value ?? 0), 0));
        const maxIntensity = Math.max(...rawIntensities, 1);
        return mapMeasurements.map((m, i) => ({
            latitude: m.latitude as number,
            longitude: m.longitude as number,
            intensity: Math.max(rawIntensities[i] / maxIntensity, 0.05)
        }));
    }, [mapMeasurements]);

    const stateAggregates = useMemo<Record<string, StateAggregate>>(() => {
        const aggregates: Record<string, { sum: number; count: number }> = {};
        filteredMeasurements.forEach((m) => {
            const stateCode = extractStateCode(m);
            const aqiValue = Number(m.aqi ?? m.value ?? 0);
            if (!stateCode || !Number.isFinite(aqiValue) || aqiValue < 0) return;
            if (!aggregates[stateCode]) aggregates[stateCode] = { sum: 0, count: 0 };
            aggregates[stateCode].sum += aqiValue;
            aggregates[stateCode].count += 1;
        });
        return Object.entries(aggregates).reduce<Record<string, StateAggregate>>((acc, [code, val]) => {
            acc[code] = { avgAqi: val.sum / val.count, count: val.count };
            return acc;
        }, {});
    }, [filteredMeasurements]);

    const getChoroplethStyle = (feature?: GeoJsonObject) => {
        const feat = feature as unknown as GeoJsonFeature;
        const stateName = feat?.properties?.name;
        const stateCode = stateName ? STATE_NAME_TO_CODE[stateName] : undefined;
        const aggregate = stateCode ? stateAggregates[stateCode] : undefined;

        if (!aggregate) {
            return { fillColor: '#bdbdbd', weight: 1, opacity: 1, color: '#666', fillOpacity: 0.2 };
        }
        return {
            fillColor: getAQIColor(Math.round(aggregate.avgAqi)),
            weight: 1,
            opacity: 1,
            color: '#666',
            fillOpacity: 0.65
        };
    };

    const onEachStateFeature = (feature: GeoJsonObject, layer: L.Layer) => {
        const feat = feature as unknown as GeoJsonFeature;
        const stateName = feat?.properties?.name;
        const stateCode = stateName ? STATE_NAME_TO_CODE[stateName] : undefined;
        const aggregate = stateCode ? stateAggregates[stateCode] : undefined;

        const popupContent = aggregate
            ? `<strong>${stateName}</strong><br/>Avg AQI: ${aggregate.avgAqi.toFixed(1)}<br/>Sensors: ${aggregate.count}`
            : `<strong>${stateName}</strong><br/>No data available`;

        layer.bindPopup(popupContent);
    };

    const mapCenter: [number, number] = mapMeasurements.length > 0
        ? [
            mapMeasurements.reduce((sum, m) => sum + (m.latitude as number), 0) / mapMeasurements.length,
            mapMeasurements.reduce((sum, m) => sum + (m.longitude as number), 0) / mapMeasurements.length
        ]
        : [37.0902, -95.7129];

    if (loading) return (
        <div className="air-quality">
            <Navbar />
            <div className="loading">Loading real-time data...</div>
        </div>
    );

    return (
        <div className="air-quality-page">
            <Navbar />
            <main className="air-quality-content">
                <h1>Air Quality - US & Canada</h1>

                {error && (
                    <div className="error-banner">
                        <p>⚠️ {error}</p>
                        <button onClick={() => { setLoading(true); fetchMeasurements(); }}>Retry</button>
                    </div>
                )}

                <div className="filters improved-filters">
                    <div className="filter-group">
                        <label htmlFor="location">Location</label>
                        <select id="location" value={selectedLocation} onChange={(e) => setSelectedLocation(e.target.value)} className="filter-select">
                            {locations.map(loc => <option key={loc} value={loc}>{loc === 'all' ? 'All locations' : loc}</option>)}
                        </select>
                    </div>

                    <div className="filter-group">
                        <label htmlFor="pollutant">Pollutant</label>
                        <select id="pollutant" value={selectedPollutant} onChange={(e) => setSelectedPollutant(e.target.value)} className="filter-select">
                            {pollutants.map(pol => <option key={pol} value={pol}>{pol === 'all' ? 'All pollutants' : pol}</option>)}
                        </select>
                    </div>

                    <div className="filter-group">
                        <label htmlFor="mapMode">Map Mode</label>
                        <select id="mapMode" value={mapMode} onChange={(e) => setMapMode(e.target.value as MapMode)} className="filter-select">
                            <option value="heatmap">Heatmap</option>
                            <option value="points">Points</option>
                            <option value="choropleth">Choropleth (Avg AQI by State)</option>
                        </select>
                    </div>
                </div>

                <div className="map-section">
                    <h2>Interactive Map</h2>
                    <div className="map-container">
                        <MapContainer center={mapCenter} zoom={4} scrollWheelZoom={true} style={{ height: '100%', width: '100%' }}>
                            <TileLayer attribution='&copy; OpenStreetMap' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
                            {mapMode === 'heatmap' && <HeatmapLayer points={heatPoints} />}
                            {mapMode === 'points' && mapMeasurements.map((m) => (
                                <CircleMarker
                                    key={`map-${m.id}-${m.pollutant}`}
                                    center={[m.latitude as number, m.longitude as number]}
                                    radius={7}
                                    pathOptions={{ color: getAQIColor(m.aqi), fillColor: getAQIColor(m.aqi), fillOpacity: 0.8 }}
                                >
                                    <Popup>
                                        <div>
                                            <strong>{m.location}</strong><br />
                                            Pollutant: {m.pollutant}<br />
                                            Value: {(m.value || 0).toFixed(1)} {m.unit}<br />
                                            AQI: {m.aqi ?? 'N/A'}<br />
                                            {new Date(m.timestamp).toLocaleString('en-US')}
                                        </div>
                                    </Popup>
                                </CircleMarker>
                            ))}
                            {mapMode === 'choropleth' && statesGeoJson && (
                                <GeoJSON data={statesGeoJson} style={getChoroplethStyle} onEachFeature={onEachStateFeature} />
                            )}
                        </MapContainer>
                    </div>

                    <div className="map-legend">
                        {mapMode === 'heatmap' && (
                            <>
                                <h3>Legend: Relative Intensity</h3>
                                <div className="legend-gradient" />
                                <div className="legend-gradient-labels">
                                    <span>Low</span><span>Medium</span><span>High</span><span>Very High</span>
                                </div>
                            </>
                        )}
                        {mapMode !== 'heatmap' && (
                            <>
                                <h3>Legend: AQI Levels</h3>
                                <ul className="legend-list">
                                    {AQI_LEGEND_RANGES.map((r) => (
                                        <li key={r.label}>
                                            <span className={mapMode === 'points' ? "legend-dot" : "legend-box"} style={{ backgroundColor: getAQIColor(r.max) }} />
                                            <span>{r.label} ({r.min}-{r.max})</span>
                                        </li>
                                    ))}
                                    {mapMode === 'choropleth' && <li><span className="legend-box legend-no-data" /><span>No data</span></li>}
                                </ul>
                            </>
                        )}
                    </div>
                </div>

                {/* Section avec Scroll Horizontal Intégré */}
                <div className="horizontal-scroll-container">
                    {filteredMeasurements.length > 0 ? (
                        filteredMeasurements.slice(0, 50).map(m => (
                            <div key={`${m.id}-${m.pollutant}`} className="measurement-card">
                                <div className="measurement-header">
                                    <h3>{m.location}</h3>
                                    <span className="pollutant-badge">{m.pollutant}</span>
                                </div>
                                <div className="measurement-value">
                                    <span className="value">{(m.value || 0).toFixed(1)}</span>
                                    <span className="unit">{m.unit}</span>
                                </div>
                                {m.aqi && (
                                    <div className="aqi-indicator" style={{ backgroundColor: getAQIColor(m.aqi) }}>
                                        AQI: {m.aqi} {m.category && <span className="category"> - {m.category}</span>}
                                    </div>
                                )}
                                <div className="measurement-time">
                                    {new Date(m.timestamp).toLocaleString('en-US')}
                                </div>
                            </div>
                        ))
                    ) : (
                        <div className="no-data">No data available for selected filters</div>
                    )}
                </div>
            </main>
        </div>
    );
};

export default AirQuality;