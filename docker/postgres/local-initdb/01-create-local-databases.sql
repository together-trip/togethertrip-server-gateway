SELECT 'CREATE DATABASE together_trip_notification'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'together_trip_notification'
)\gexec
