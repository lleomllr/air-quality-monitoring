-- ATTENTION : Ne PAS mettre "CREATE DATABASE" ni "\c clients" ici.
-- Le docker-compose s'en charge automatiquement grâce à POSTGRES_DB=clients

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS clients (
                                       id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                       email           VARCHAR(255)  NOT NULL,
                                       password        VARCHAR(255)  NOT NULL,
                                       full_name       VARCHAR(255),
                                       location        GEOMETRY(POINT, 4326),
                                       active          BOOLEAN       DEFAULT true,
                                       last_updated    TIMESTAMPTZ   DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_clients_gix ON clients USING GIST (location);

-- INSERT INTO clients (email, full_name, location, active) VALUES
-- ('marie.dupont@gmail.com', 'Marie Dupont', ST_SetSRID(ST_MakePoint(2.3522, 48.8566), 4326), true),
-- ('thomas.martin@free.fr', 'Thomas Martin', ST_SetSRID(ST_MakePoint(0.0, 0.0), 4326), true),
-- ('sophie.leclerc@yahoo.fr', 'Sophie Leclerc', ST_SetSRID(ST_MakePoint(0.0, 0.0), 4326), false),
-- ('julien.bernard@orange.fr', 'Julien Bernard', ST_SetSRID(ST_MakePoint(-0.5792, 44.8378), 4326), true),
-- ('emma.leroy@protonmail.com', 'Emma Leroy', ST_SetSRID(ST_MakePoint(3.0573, 50.6292), 4326), true),
-- ('pierre.schmitt@outlook.fr', 'Pierre Schmitt', ST_SetSRID(ST_MakePoint(7.7520, 48.5839), 4326), true),
-- ('laura.roux@gmail.com', 'Laura Roux', ST_SetSRID(ST_MakePoint(1.4442, 43.6047), 4326), true),
-- ('alexandre.giraud@laposte.net', 'Alexandre Giraud', ST_SetSRID(ST_MakePoint(-1.5536, 47.2184), 4326), true),
-- ('clara.moreau@hotmail.fr', 'Clara Moreau', ST_SetSRID(ST_MakePoint(7.2661, 43.7031), 4326), true),
-- ('nicolas.fabre@gmail.com', 'Nicolas Fabre', ST_SetSRID(ST_MakePoint(2.2067, 48.9027), 4326), true);