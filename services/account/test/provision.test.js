import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it, mock } from "node:test";

import { encodeJsonSegment, pkceChallenge, randomToken } from "../src/crypto.js";
import worker from "../src/index.js";
import { createD1 } from "./d1.js";

const LOOPBACK = "http://127.0.0.1:53682/callback";
const now = () => Math.floor(Date.now() / 1000);

async function makeEnv() {
  const { privateKey } = await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"]);
  const jwk = { ...(await crypto.subtle.exportKey("jwk", privateKey)), kid: "test-key" };
  return {
    DB: createD1(),
    PUBLIC_URL: "https://account.test",
    GOOGLE_CLIENT_ID: "google-client",
    GOOGLE_CLIENT_SECRET: "google-secret",
    SIGNING_KEY: JSON.stringify(jwk),
  };
}

function fakeIdToken(claims) {
  return `${encodeJsonSegment({ alg: "RS256" })}.${encodeJsonSegment(claims)}.sig`;
}

let env;
let googleClaims;

function call(method, path, { body, token } = {}) {
  const init = { method, headers: {} };
  if (body !== undefined) {
    init.body = JSON.stringify(body);
    init.headers["content-type"] = "application/json";
  }
  if (token) init.headers.authorization = `Bearer ${token}`;
  return worker.fetch(new Request(`https://account.test${path}`, init), env);
}

async function signIn(deviceName = "Phone") {
  const verifier = randomToken();
  const clientState = randomToken(16);
  const query = new URLSearchParams({
    redirect_uri: LOOPBACK,
    state: clientState,
    code_challenge: await pkceChallenge(verifier),
    code_challenge_method: "S256",
  });
  const start = await call("GET", `/auth/google/start?${query}`);
  assert.equal(start.status, 302);
  const google = new URL(start.headers.get("location"));

  const callback = await call("GET", `/auth/google/callback?code=google-code&state=${google.searchParams.get("state")}`);
  assert.equal(callback.status, 302);
  const back = new URL(callback.headers.get("location"));

  const response = await call("POST", "/auth/token", {
    body: {
      grant_type: "authorization_code",
      code: back.searchParams.get("code"),
      code_verifier: verifier,
      redirect_uri: LOOPBACK,
      device: { name: deviceName, platform: "android" },
    },
  });
  assert.equal(response.status, 200);
  return response.json();
}

async function issueCode(token, binding = randomToken(32)) {
  const response = await call("POST", "/devices/provision", { body: { binding }, token });
  assert.equal(response.status, 200);
  const { code, expires_in } = await response.json();
  assert.equal(expires_in, 120);
  return { code, binding };
}

function redeem(code, binding, device = { name: "Living Room TV", platform: "android" }) {
  return call("POST", "/auth/token", {
    body: { grant_type: "urn:orchard:grant-type:provision", code, binding, device },
  });
}

beforeEach(async () => {
  env = await makeEnv();
  googleClaims = {
    iss: "https://accounts.google.com",
    aud: "google-client",
    sub: "google-user-1",
    exp: now() + 3600,
    email: "fan@example.com",
    email_verified: true,
    name: "Orchard Fan",
    picture: "https://example.com/p.png",
  };
  mock.method(globalThis, "fetch", async () => Response.json({ id_token: fakeIdToken(googleClaims) }));
});

afterEach(() => mock.restoreAll());

describe("provision codes", () => {
  it("rejects emission without a bearer token", async () => {
    const response = await call("POST", "/devices/provision", { body: { binding: randomToken(32) } });
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), {
      error: "invalid_token",
      error_description: "Sign in to Orchard again",
    });
  });

  it("applies the provision rate-limit bucket before issuing", async () => {
    const phone = await signIn();
    const keys = [];
    env.AUTH_LIMITER = {
      limit: async ({ key }) => {
        keys.push(key);
        return { success: !key.startsWith("provision:") };
      },
    };
    const response = await call("POST", "/devices/provision", { body: { binding: randomToken(32) }, token: phone.access_token });
    assert.equal(response.status, 429);
    assert.ok(keys.some((key) => key.startsWith("provision:")));
  });

  it("rejects emission with a malformed binding", async () => {
    const phone = await signIn();
    for (const binding of ["", "short", randomToken(16), "!".repeat(43), 12345]) {
      const response = await call("POST", "/devices/provision", { body: { binding }, token: phone.access_token });
      assert.equal(response.status, 400, String(binding));
    }
  });

  it("redeems a code into a separate device", async () => {
    const phone = await signIn("Phone");
    const { code, binding } = await issueCode(phone.access_token);

    const response = await redeem(code, binding);
    assert.equal(response.status, 200);
    const tv = await response.json();
    assert.equal(tv.token_type, "Bearer");
    assert.equal(tv.user.id, phone.user.id);
    assert.notEqual(tv.device_id, phone.device_id);
    assert.match(tv.refresh_token, new RegExp(`^${tv.device_id}\\.`));
  });

  it("burns a code on first use", async () => {
    const phone = await signIn();
    const { code, binding } = await issueCode(phone.access_token);

    assert.equal((await redeem(code, binding)).status, 200);
    const retry = await redeem(code, binding);
    assert.equal(retry.status, 400);
    assert.deepEqual(await retry.json(), {
      error: "invalid_grant",
      error_description: "Provisioning code is invalid or expired",
    });
  });

  it("rejects an expired code", async () => {
    const phone = await signIn();
    const { code, binding } = await issueCode(phone.access_token);
    env.DB.raw.prepare("UPDATE provision_codes SET expires_at = ?").run(now() - 1);

    const response = await redeem(code, binding);
    assert.equal(response.status, 400);
    assert.equal((await response.json()).error, "invalid_grant");
  });

  it("rejects a mismatched binding and consumes the code", async () => {
    const phone = await signIn();
    const { code, binding } = await issueCode(phone.access_token);
    const otherBinding = randomToken(32);

    const mismatch = await redeem(code, otherBinding);
    assert.equal(mismatch.status, 400);
    assert.equal((await mismatch.json()).error, "invalid_grant");

    // The code is gone even though the binding did not match (PAIR-CONN-INV-1).
    const retry = await redeem(code, binding);
    assert.equal(retry.status, 400);
    assert.equal((await retry.json()).error, "invalid_grant");
  });

  it("limits live codes to five per user", async () => {
    const phone = await signIn();
    for (let i = 0; i < 5; i++) {
      assert.equal((await call("POST", "/devices/provision", {
        body: { binding: randomToken(32) },
        token: phone.access_token,
      })).status, 200);
    }
    const sixth = await call("POST", "/devices/provision", {
      body: { binding: randomToken(32) },
      token: phone.access_token,
    });
    assert.equal(sixth.status, 429);
    assert.equal((await sixth.json()).error, "slow_down");
  });
});
