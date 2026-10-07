-- Serves the daily link quota (spec 02 R13): count one owner's links in a creation-time window.
-- No grants: the application role already has SELECT on link.links.
CREATE INDEX links_owner_created_idx ON link.links (owner_key_id, created_at);
