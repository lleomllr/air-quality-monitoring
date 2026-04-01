import Navbar from './Navbar';
import '../css/Home.css';

const Home = () => {
  return (
      <div className="home">
        <Navbar />
        <main className="home-content">
          <section className="hero">
            <div className="hero-content">
              <h1>Air Quality Monitoring</h1>
              <p className="hero-subtitle">
                Real-time air quality data across the United States and Canada
              </p>
              <div className="hero-stats">
                <div className="stat-card">
                  <h3>2500+</h3>
                  <p>Real-time measurements</p>
                </div>
                <div className="stat-card">
                  <h3>3+</h3>
                  <p>Monitored pollutants</p>
                </div>
                <div className="stat-card">
                  <h3>24/7</h3>
                  <p>Continuous monitoring</p>
                </div>
              </div>
            </div>
          </section>

          <section className="features">
            <h2>Features</h2>
            <div className="features-grid">
              <div className="feature-card">
                <div className="feature-icon">📊</div>
                <h3>Real-Time Data</h3>
                <p>Instant access to air quality metrics</p>
              </div>
              <div className="feature-card">
                <div className="feature-icon">🗺️</div>
                <h3>Wide Coverage</h3>
                <p>United States and Canada included</p>
              </div>
              <div className="feature-card">
                <div className="feature-icon">📈</div>
                <h3>Advanced Statistics</h3>
                <p>Data analysis and trends</p>
              </div>
              <div className="feature-card">
                <div className="feature-icon">🚨</div>
                <h3>Smart Alerts</h3>
                <p>Notifications for detected anomalies</p>
              </div>
            </div>
          </section>

          <section className="pollutants">
            <h2>Monitored Pollutants</h2>
            <div className="pollutants-list">
              <div className="pollutant-item">
                <strong>PM2.5</strong>
                <span>Fine particles</span>
              </div>
              <div className="pollutant-item">
                <strong>PM10</strong>
                <span>Coarse particles</span>
              </div>
              <div className="pollutant-item">
                <strong>O3</strong>
                <span>Ozone</span>
              </div>
            </div>
          </section>
        </main>
      </div>
  );
};

export default Home;

