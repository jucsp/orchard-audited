-- One-time provisioning codes for pairing a new device from a signed-in phone.
-- code_hash stores SHA-256(code) so a leaked D1 dump holds nothing replayable.

CREATE TABLE provision_codes (
    code_hash TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issuer_device_id TEXT NOT NULL,
    binding TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX provision_codes_expiry ON provision_codes(expires_at);
CREATE INDEX provision_codes_user ON provision_codes(user_id);
