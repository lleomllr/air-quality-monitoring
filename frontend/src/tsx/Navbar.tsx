import { Link, useNavigate } from 'react-router-dom';
import '../css/Navbar.css';

const Navbar = () => {
    const navigate = useNavigate();
    const isAuthenticated = localStorage.getItem('token') !== null;

    const handleLogout = async () => {
        const token = localStorage.getItem('token');

        // On prévient le Backend que le client se déconnecte (active = false)
        if (token) {
            try {
                await fetch('http://localhost:8095/api/auth/logout', {
                    method: 'POST',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
            } catch (error) {
                console.error("Erreur serveur lors de la déconnexion", error);
            }
        }

        // On nettoie le navigateur
        localStorage.removeItem('token');

        // On utilise React Router pour rediriger fluidement sans recharger la page
        navigate('/login');
    };

    return (
        <nav className="navbar">
            <div className="navbar-container">
                <Link to="/" className="navbar-logo">
                    🌍 AirQuality Monitor
                </Link>

                <ul className="navbar-menu">
                    <li className="navbar-item">
                        <Link to="/" className="navbar-link">Home</Link>
                    </li>
                    <li className="navbar-item">
                        <Link to="/air-quality" className="navbar-link">Air Quality</Link>
                    </li>
                    <li className="navbar-item">
                        <Link to="/statistics" className="navbar-link">Statistics</Link>
                    </li>
                    {isAuthenticated && (
                        <li className="navbar-item">
                            <Link to="/dashboard" className="navbar-link">Dashboard</Link>
                        </li>
                    )}

                    {isAuthenticated ? (
                        <li className="navbar-item">
                            <button onClick={handleLogout} className="navbar-button">
                                Logout
                            </button>
                        </li>
                    ) : (
                        <>
                            <li className="navbar-item">
                                <Link to="/login" className="navbar-link">Login</Link>
                            </li>
                            <li className="navbar-item">
                                <Link to="/register" className="navbar-button-link">
                                    Register
                                </Link>
                            </li>
                        </>
                    )}
                </ul>
            </div>
        </nav>
    );
};

export default Navbar;