/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

import { fromBase64url, hashSecret, randomToken } from "./crypto.js";
import { HttpError, nowSeconds } from "./http.js";

export const PROVISION_CODE_SECONDS = 120;
export const MAX_LIVE_PROVISION_CODES = 5;

const BINDING_PATTERN = /^[A-Za-z0-9_-]{43}$/;

// 43 base64url characters decode to exactly 32 bytes (256 bits) with no padding.
export function isProvisionBinding(value) {
  if (typeof value !== "string" || !BINDING_PATTERN.test(value)) return false;
  try {
    return fromBase64url(value).length === 32;
  } catch {
    return false;
  }
}

export async function createProvisionCode(env, userId, issuerDeviceId, binding) {
  const now = nowSeconds();
  await env.DB.prepare("DELETE FROM provision_codes WHERE expires_at <= ?").bind(now).run();

  const { results } = await env.DB.prepare("SELECT COUNT(*) AS n FROM provision_codes WHERE user_id = ?")
    .bind(userId).all();
  if (results[0].n >= MAX_LIVE_PROVISION_CODES) {
    throw new HttpError(429, "slow_down", "Too many provisioning codes");
  }

  const code = randomToken(32);
  await env.DB.prepare(
    "INSERT INTO provision_codes (code_hash, user_id, issuer_device_id, binding, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
  ).bind(await hashSecret(code), userId, issuerDeviceId, binding, now + PROVISION_CODE_SECONDS, now).run();
  return { code, expires_in: PROVISION_CODE_SECONDS };
}

// Single use: the row is gone even when the binding does not match (PAIR-CONN-INV-1).
export async function consumeProvisionCode(env, body) {
  const codeHash = await hashSecret(String(body.code || ""));
  const row = await env.DB.prepare("DELETE FROM provision_codes WHERE code_hash = ? RETURNING *").bind(codeHash).first();
  if (!row || row.expires_at <= nowSeconds() || row.binding !== body.binding) {
    throw new HttpError(400, "invalid_grant", "Provisioning code is invalid or expired");
  }
  return row;
}
