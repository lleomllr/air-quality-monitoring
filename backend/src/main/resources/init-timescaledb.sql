CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE SCHEMA IF NOT EXISTS aqi;
SET search_path TO aqi, public;

CREATE TABLE IF NOT EXISTS aqi.city_location (
     city            TEXT                NOT NULL,
     state           TEXT                NOT NULL,
     longitude       DOUBLE PRECISION    NOT NULL,
     latitude        DOUBLE PRECISION    NOT NULL,
     geom            GEOMETRY(POINT, 4326),

     PRIMARY KEY (city, state)
);
CREATE INDEX ON aqi.city_location USING GIST (geom);

CREATE TABLE IF NOT EXISTS aqi.state_location (
      state           TEXT                 PRIMARY KEY,
      state_name      TEXT,
      geom            GEOMETRY(MULTIPOLYGON, 4326)
);
CREATE INDEX ON aqi.state_location USING GIST (geom);

CREATE TABLE IF NOT EXISTS aqi.city (
    time            TIMESTAMPTZ         NOT NULL,
    state           TEXT                NOT NULL,
    city            TEXT                NOT NULL,
    pollutant       TEXT                NOT NULL,
    concentration   REAL                NOT NULL,
    aqi             SMALLINT            NOT NULL,
    category        TEXT                NOT NULL,
    created_at      TIMESTAMPTZ         DEFAULT NOW(),

    PRIMARY KEY (state, city, pollutant)
);
CREATE INDEX ON aqi.city (city, pollutant, time DESC);

CREATE TABLE IF NOT EXISTS aqi.avg_city_hour (
    time_start      TIMESTAMPTZ          NOT NULL,
    time_end        TIMESTAMPTZ          NOT NULL,
    state           TEXT                 NOT NULL,
    city            TEXT                 NOT NULL,
    pollutant       TEXT                 NOT NULL,
    aqi             REAL                 NOT NULL,
    created_at      TIMESTAMPTZ          DEFAULT NOW()
);
SELECT create_hypertable('aqi.avg_city_hour', 'time_start', if_not_exists => TRUE);
CREATE INDEX ON aqi.avg_city_hour (city, pollutant, time_start DESC);

CREATE TABLE IF NOT EXISTS aqi.avg_state_hour (
    time_start      TIMESTAMPTZ          NOT NULL,
    time_end        TIMESTAMPTZ          NOT NULL,
    state           TEXT                 NOT NULL,
    pollutant       TEXT                 NOT NULL,
    aqi             REAL                 NOT NULL,
    created_at      TIMESTAMPTZ          DEFAULT NOW()
);
SELECT create_hypertable('aqi.avg_state_hour', 'time_start', if_not_exists => TRUE);
CREATE INDEX ON aqi.avg_state_hour (state, pollutant, time_start DESC);

CREATE TABLE IF NOT EXISTS aqi.avg_city_8_hour (
    time_start      TIMESTAMPTZ          NOT NULL,
    time_end        TIMESTAMPTZ          NOT NULL,
    state           TEXT                 NOT NULL,
    city            TEXT         NOT NULL,
    pollutant       TEXT                 NOT NULL,
    aqi             REAL            NOT NULL,
    created_at      TIMESTAMPTZ          DEFAULT NOW()
);
SELECT create_hypertable('aqi.avg_city_8_hour', 'time_start', if_not_exists => TRUE);
CREATE INDEX ON aqi.avg_city_8_hour (city, pollutant, time_start DESC);

CREATE TABLE IF NOT EXISTS aqi.avg_state_8_hour (
    time_start      TIMESTAMPTZ          NOT NULL,
    time_end        TIMESTAMPTZ          NOT NULL,
    state           TEXT                 NOT NULL,
    pollutant       TEXT                 NOT NULL,
    aqi             REAL            NOT NULL,
    created_at      TIMESTAMPTZ          DEFAULT NOW()
);
SELECT create_hypertable('aqi.avg_state_8_hour', 'time_start', if_not_exists => TRUE);
CREATE INDEX ON aqi.avg_state_8_hour (state, pollutant, time_start DESC);

CREATE TABLE IF NOT EXISTS aqi.avg_city_day (
    time_start      TIMESTAMPTZ          NOT NULL,
    time_end        TIMESTAMPTZ          NOT NULL,
    state           TEXT                 NOT NULL,
    city            TEXT         NOT NULL        ,
    pollutant       TEXT                 NOT NULL,
    aqi             REAL            NOT NULL,
    created_at      TIMESTAMPTZ          DEFAULT NOW()
);
SELECT create_hypertable('aqi.avg_city_day', 'time_start', if_not_exists => TRUE);
CREATE INDEX ON aqi.avg_city_day (city, pollutant, time_start DESC);

CREATE TABLE IF NOT EXISTS aqi.avg_state_day (
    time_start      TIMESTAMPTZ          NOT NULL,
    time_end        TIMESTAMPTZ          NOT NULL,
    state           TEXT                 NOT NULL,
    pollutant       TEXT                 NOT NULL,
    aqi             REAL            NOT NULL,
    created_at      TIMESTAMPTZ          DEFAULT NOW()
);
SELECT create_hypertable('aqi.avg_state_day', 'time_start', if_not_exists => TRUE);
CREATE INDEX ON aqi.avg_state_day (state, pollutant, time_start DESC);

CREATE TABLE IF NOT EXISTS aqi.max_city_hour (
    time_start      TIMESTAMPTZ          NOT NULL,
    time_end        TIMESTAMPTZ          NOT NULL,
    state           TEXT                 NOT NULL,
    city            TEXT                 NOT NULL,
    pollutant       TEXT                 NOT NULL,
    aqi             SMALLINT            NOT NULL,
    created_at      TIMESTAMPTZ          DEFAULT NOW()
);
SELECT create_hypertable('aqi.max_city_hour', 'time_start', if_not_exists => TRUE);
CREATE INDEX ON aqi.max_city_hour (city, pollutant, time_start DESC);

CREATE TABLE IF NOT EXISTS aqi.max_state_hour (
    time_start      TIMESTAMPTZ          NOT NULL,
    time_end        TIMESTAMPTZ          NOT NULL,
    state           TEXT                 NOT NULL,
    pollutant       TEXT                 NOT NULL,
    aqi             SMALLINT            NOT NULL,
    created_at      TIMESTAMPTZ          DEFAULT NOW()
);
SELECT create_hypertable('aqi.max_state_hour', 'time_start', if_not_exists => TRUE);
CREATE INDEX ON aqi.max_state_hour (state, pollutant, time_start DESC);

CREATE TABLE IF NOT EXISTS aqi.alerts (
                                          time            TIMESTAMPTZ         NOT NULL,
                                          state           TEXT                NOT NULL,
                                          city            TEXT                NOT NULL,
                                          pollutant       TEXT                NOT NULL,
                                          aqi             SMALLINT            NOT NULL,
                                          created_at      TIMESTAMPTZ         DEFAULT NOW(),

                                          PRIMARY KEY (time, city, pollutant)
);

SELECT create_hypertable('aqi.alerts', 'time', if_not_exists => TRUE);

CREATE TEMP TABLE json_import (payload jsonb);
COPY json_import (payload) FROM '/data/cities.jsonl';

INSERT INTO aqi.city_location (city, state, longitude, latitude, geom)
SELECT
    payload ->> 'city',
    payload ->> 'state',
    (payload ->> 'longitude')::double precision,
    (payload ->> 'latitude')::double precision,
    ST_SetSRID(
        ST_MakePoint(
            (payload ->> 'longitude')::double precision,
            (payload ->> 'latitude')::double precision
        ),
        4326
    )
FROM json_import ON CONFLICT (city, state) DO NOTHING;

DROP TABLE json_import;

\i /data/init-states.sql