-- Dev/test seed: a retail site for local attendant testing (attendant1's Keycloak
-- locationCode claim, docker/keycloak/realm-export.json, is set to STN-04 to match).
INSERT INTO locations (code, name, type, active, created_at)
VALUES ('STN-04', 'Station 4', 'RETAIL_SITE', true, now());
