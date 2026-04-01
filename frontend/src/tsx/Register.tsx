import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import '../css/Login.css';

interface CityLocation {
    city: string;
    state: string;
    latitude: number;
    longitude: number;
}

const Register = () => {
    const [formData, setFormData] = useState({
        fullName: '',
        email: '',
        password: '',
        confirmPassword: ''
    });

    const [location, setLocation] = useState<{lat: number, lng: number} | null>(null);
    const [cityLocations, setCityLocations] = useState<CityLocation[]>([]);
    const [useManualSelection, setUseManualSelection] = useState(false);

    // Nouveaux états pour l'auto-complétion
    const [searchTerm, setSearchTerm] = useState('');
    const [filteredCities, setFilteredCities] = useState<CityLocation[]>([]);
    const [showDropdown, setShowDropdown] = useState(false);

    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        fetch("http://localhost:8095/api/aqi/city-locations")
            .then(res => res.json())
            .then((data: unknown) => setCityLocations(Array.isArray(data) ? (data as CityLocation[]) : []))
            .catch(err => console.error("Erreur chargement des villes:", err));

        if ("geolocation" in navigator && !useManualSelection) {
            navigator.geolocation.getCurrentPosition(
                (position) => {
                    setLocation({
                        lat: position.coords.latitude,
                        lng: position.coords.longitude
                    });
                },
                (err) => {
                    console.warn("Géolocalisation refusée ou impossible:", err.message);
                    setUseManualSelection(true);
                }
            );
        }
    }, [useManualSelection]);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    // Gestion de la saisie dans le champ de recherche
    const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const value = e.target.value;
        setSearchTerm(value);
        setLocation(null); // On réinitialise la position si l'utilisateur change la ville

        if (value.length > 0) {
            // Filtrer les villes (insensible à la casse)
            const filtered = cityLocations.filter(c =>
                `${c.city} ${c.state}`.toLowerCase().includes(value.toLowerCase())
            );
            setFilteredCities(filtered);
            setShowDropdown(true);
        } else {
            setFilteredCities([]);
            setShowDropdown(false);
        }
    };

    // Quand l'utilisateur clique sur une ville de la liste
    const handleCitySelect = (city: CityLocation) => {
        setSearchTerm(`${city.city} (${city.state})`); // Remplir l'input
        setLocation({ lat: city.latitude, lng: city.longitude }); // Sauvegarder les coords
        setShowDropdown(false); // Cacher la liste
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        if (formData.password !== formData.confirmPassword) {
            setError('Passwords do not match');
            return;
        }

        if (formData.password.length < 6) {
            setError('Password must be at least 6 characters');
            return;
        }

        setLoading(true);

        try {
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    fullName: formData.fullName,
                    email: formData.email,
                    password: formData.password,
                    latitude: location?.lat,
                    longitude: location?.lng
                }),
            });

            if (response.ok) {
                const data = await response.json();
                if (data.token) localStorage.setItem('token', data.token);
                if (data.userId) localStorage.setItem('userId', data.userId.toString());
                if (data.email) localStorage.setItem('userEmail', data.email);

                navigate('/dashboard');
            } else {
                const errorData = await response.json().catch(() => ({ message: 'Registration failed' }));
                setError(errorData.message || 'Registration failed');
            }
        } catch {
            setError('Connection error. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <div style={{ textAlign: 'left', marginBottom: '1rem' }}>
                    <Link to="/" style={{ color: '#64748b', textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.9rem', fontWeight: '500', transition: 'color 0.2s ease' }} onMouseOver={(e) => e.currentTarget.style.color = '#3b82f6'} onMouseOut={(e) => e.currentTarget.style.color = '#64748b'}>
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M19 12H5M12 19l-7-7 7-7"/>
                        </svg>
                        Retour à l'accueil
                    </Link>
                </div>
                <div className="login-header">
                    <div className="logo">
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                            <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                            <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                    </div>
                    <h1>Create Account</h1>
                    <p>Join AirNow to monitor air quality data</p>
                </div>

                <form onSubmit={handleSubmit} className="login-form">
                    {error && (
                        <div className="error-message">
                            ⚠️ {error}
                        </div>
                    )}

                    <div className="form-group">
                        <label htmlFor="fullName">Full Name</label>
                        <input id="fullName" name="fullName" type="text" placeholder="John Doe" value={formData.fullName} onChange={handleChange} required disabled={loading} />
                    </div>

                    <div className="form-group">
                        <label htmlFor="email">Email Address</label>
                        <input id="email" name="email" type="email" placeholder="you@example.com" value={formData.email} onChange={handleChange} required disabled={loading} />
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">Password</label>
                        <input id="password" name="password" type="password" placeholder="••••••••" value={formData.password} onChange={handleChange} required disabled={loading} minLength={6} />
                    </div>

                    <div className="form-group">
                        <label htmlFor="confirmPassword">Confirm Password</label>
                        <input id="confirmPassword" name="confirmPassword" type="password" placeholder="••••••••" value={formData.confirmPassword} onChange={handleChange} required disabled={loading} minLength={6} />
                    </div>

                    {/* --- BLOC SÉLECTEUR DE LOCALISATION AVEC AUTO-COMPLÉTION --- */}
                    <div className="location-selector">
                        <label>Position géographique</label>
                        <div className="location-toggle">
                            <button
                                type="button"
                                className={`toggle-btn ${!useManualSelection ? 'active' : ''}`}
                                onClick={() => {
                                    setUseManualSelection(false);
                                    setSearchTerm(''); // On vide la recherche si on repasse en GPS
                                }}
                            >
                                📍 Ma position GPS
                            </button>
                            <button
                                type="button"
                                className={`toggle-btn ${useManualSelection ? 'active' : ''}`}
                                onClick={() => setUseManualSelection(true)}
                            >
                                🏢 Choisir une ville
                            </button>
                        </div>

                        {useManualSelection ? (
                            <div className="autocomplete-container">
                                <input
                                    type="text"
                                    className="custom-input"
                                    placeholder="Recherchez une ville (ex: Dallas)..."
                                    value={searchTerm}
                                    onChange={handleSearchChange}
                                    onFocus={() => { if (searchTerm.length > 0) setShowDropdown(true); }}
                                    // Le délai permet de cliquer sur un élément de la liste avant qu'elle ne disparaisse
                                    onBlur={() => setTimeout(() => setShowDropdown(false), 200)}
                                />

                                {showDropdown && filteredCities.length > 0 && (
                                    <ul className="autocomplete-list">
                                        {/* On limite l'affichage aux 10 premiers résultats pour ne pas surcharger la page */}
                                        {filteredCities.slice(0, 10).map((loc, idx) => (
                                            <li
                                                key={idx}
                                                className="autocomplete-item"
                                                onMouseDown={() => handleCitySelect(loc)}
                                            >
                                                <span className="city-name">{loc.city}</span>
                                                <span className="state-badge">{loc.state}</span>
                                            </li>
                                        ))}
                                    </ul>
                                )}

                                {showDropdown && filteredCities.length === 0 && searchTerm.length > 0 && (
                                    <div className="autocomplete-no-results">Aucune ville trouvée</div>
                                )}
                            </div>
                        ) : (
                            location ? (
                                <div className="location-status success">
                                    ✓ Coordonnées GPS détectées
                                </div>
                            ) : (
                                <div className="location-status waiting">
                                    ⏳ Autorisez l'accès au GPS...
                                </div>
                            )
                        )}
                    </div>
                    {/* ----------------------------------------------------------- */}

                    <button type="submit" className="submit-button" disabled={loading || (!location && useManualSelection)}>
                        {loading ? 'Creating account...' : 'Create Account'}
                    </button>
                </form>

                <div className="login-footer">
                    <p>Already have an account? <Link to="/login">Sign in</Link></p>
                </div>
            </div>

            <div className="background-decoration">
                <div className="circle circle-1"></div>
                <div className="circle circle-2"></div>
                <div className="circle circle-3"></div>
            </div>
        </div>
    );
};

export default Register;