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

// Orchard listening parties: one Durable Object per room. It keeps the host's
// last playback snapshot, relays WebRTC signaling, and hands the host seat to
// the oldest listener when the host leaves. Playback traffic itself never
// passes through the worker.

import { randomToken } from "./crypto.js";
import { HttpError, issuer, json, nowSeconds, readJson } from "./http.js";

export const MAX_PARTICIPANTS = 10;
export const ROOM_SECONDS = 6 * 60 * 60;
const MAX_SIGNAL_BYTES = 64 * 1024;
const DEFAULT_ICE = [{ urls: "stun:stun.cloudflare.com:3478" }];
const ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

// 32 characters, a power of two, so `byte % length` is uniform with no bias.
export function generateRoomCode(length = 6) {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  let code = "";
  for (const byte of bytes) code += ROOM_CODE_ALPHABET[byte % ROOM_CODE_ALPHABET.length];
  return code;
}

// Uppercase, then drop anything outside A-Z and 2-9. I and O are kept on input
// so a one-character typo misses the room instead of shifting the whole code.
export function normalizeCode(value) {
  return String(value || "").toUpperCase().replace(/[^A-Z2-9]/g, "");
}

function cleanName(value) {
  const name = String(value ?? "").replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, 32);
  return name || "Listener";
}

export class PartyRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.room = null;
    this.participants = new Map();
    this.lastState = null;
  }

  async hydrate() {
    if (this.room) return;
    const [room, participants, lastState] = await Promise.all([
      this.ctx.storage.get("room"),
      this.ctx.storage.get("participants"),
      this.ctx.storage.get("lastState"),
    ]);
    this.room = room || null;
    this.participants = new Map(Object.entries(participants || {}));
    this.lastState = lastState || null;
  }

  async persist() {
    await this.ctx.storage.put("room", this.room);
    await this.ctx.storage.put("participants", Object.fromEntries(this.participants));
    await this.ctx.storage.put("lastState", this.lastState ?? null);
  }

  async fetch(request) {
    await this.hydrate();
    const { pathname } = new URL(request.url);
    if (request.method === "POST" && pathname === "/init") return this.init(request);
    if (request.method === "POST" && pathname === "/join") return this.join(request);
    if (request.method === "GET" && pathname.endsWith("/socket")) return this.openSocket(request);
    throw new HttpError(404, "not_found", "No such route");
  }

  async init(request) {
    if (this.room) throw new HttpError(409, "already_exists", "This room code is already taken");
    const body = await readJson(request);
    const code = normalizeCode(body.code);
    if (!code) throw new HttpError(400, "invalid_request", "A room code is required");
    const now = nowSeconds();
    const participant = {
      id: crypto.randomUUID(),
      name: cleanName(body.hostName) || "Host",
      role: "host",
      token: randomToken(),
      joinedAt: now,
    };
    this.room = {
      id: code,
      code,
      hostId: participant.id,
      createdAt: now,
      expiresAt: now + ROOM_SECONDS,
      closed: false,
      maxParticipants: MAX_PARTICIPANTS,
    };
    this.participants.set(participant.id, participant);
    await this.persist();
    return json({ ok: true, data: { room: this.roomSummary(), participant: this.participantView(participant) } });
  }

  async join(request) {
    if (!this.room) throw new HttpError(404, "not_found", "Room not found");
    if (this.room.closed) throw new HttpError(409, "room_closed", "This party has ended");
    if (this.participants.size >= this.room.maxParticipants) {
      throw new HttpError(409, "room_full", "This party is full");
    }
    const body = await readJson(request);
    const now = nowSeconds();
    const participant = {
      id: crypto.randomUUID(),
      name: cleanName(body.name),
      role: "guest",
      token: randomToken(),
      joinedAt: now,
    };
    this.participants.set(participant.id, participant);
    await this.persist();
    return json({ ok: true, data: { room: this.roomSummary(), participant: this.participantView(participant) } });
  }

  async openSocket(request) {
    if ((request.headers.get("upgrade") || "").toLowerCase() !== "websocket") {
      throw new HttpError(426, "upgrade_required", "Connect to a party room with a WebSocket");
    }
    if (!this.room) throw new HttpError(404, "not_found", "Room not found");
    if (this.room.closed) throw new HttpError(409, "room_closed", "This party has ended");
    const params = new URL(request.url).searchParams;
    const participantId = params.get("participantId") || "";
    const token = params.get("token") || "";
    const participant = this.participants.get(participantId);
    if (!participant || participant.token !== token) {
      throw new HttpError(401, "invalid_token", "Join the room first");
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.accept(server, participantId);
    return new Response(null, { status: 101, webSocket: client });
  }

  accept(socket, participantId) {
    const participant = this.participants.get(participantId);
    if (!participant) return;
    this.ctx.acceptWebSocket(socket, [participantId]);
    socket.serializeAttachment({ participantId });
    this.send(socket, {
      type: "party:welcome",
      iceServers: DEFAULT_ICE,
      room: this.roomSummary(),
      role: participant.role,
      peers: this.peerList(participantId),
      lastState: this.lastState ? { payload: this.lastState } : null,
    });
    this.broadcast(
      { type: "peer:joined", peer: { id: participant.id, name: participant.name, role: participant.role } },
      socket,
    );
  }

  send(socket, message) {
    try {
      socket.send(JSON.stringify(message));
    } catch {
      // A socket closing underneath us is cleaned up by webSocketClose.
    }
  }

  broadcast(message, excludeSocket = null) {
    for (const socket of this.ctx.getWebSockets()) {
      if (socket === excludeSocket) continue;
      this.send(socket, message);
    }
  }

  socketFor(participantId) {
    return this.ctx.getWebSockets(participantId)
      .find((socket) => socket.deserializeAttachment()?.participantId === participantId) || null;
  }

  peerList(excludeId) {
    return [...this.participants.values()]
      .filter((participant) => participant.id !== excludeId)
      .map((participant) => ({ id: participant.id, name: participant.name, role: participant.role }));
  }

  participantView(participant) {
    return {
      id: participant.id,
      name: participant.name,
      role: participant.role,
      token: participant.token,
      joinedAt: participant.joinedAt,
    };
  }

  roomSummary() {
    const origin = issuer(this.env);
    const code = this.room.code;
    return {
      id: code,
      code,
      hostId: this.room.hostId,
      createdAt: this.room.createdAt,
      expiresAt: this.room.expiresAt,
      closed: this.room.closed,
      maxParticipants: this.room.maxParticipants,
      participantCount: this.participants.size,
      socketUrl: `${origin}/rooms/${code}/socket`,
      joinUrl: `${origin}/party/${code}`,
      shareUrl: `${origin}/party/${code}`,
    };
  }

  async webSocketMessage(socket, raw) {
    const state = socket.deserializeAttachment();
    if (!state) return;
    const participant = this.participants.get(state.participantId);
    if (!participant) return;
    if (typeof raw !== "string") return;
    let message;
    try {
      message = JSON.parse(raw);
    } catch {
      return;
    }
    if (!message || typeof message !== "object") return;
    switch (message.type) {
      case "party:update":
        return this.updateState(socket, participant, message);
      case "party:request":
        return this.forwardRequest(participant, message);
      case "signal":
        return this.relaySignal(participant, message);
      case "party:host-transfer":
        return this.transferHost(participant, message);
      case "party:close":
        return this.closeRoom(participant);
      default:
    }
  }

  async updateState(socket, participant, message) {
    if (participant.role !== "host") return;
    this.lastState = message.payload;
    await this.persist();
    this.broadcast({ type: "party:update", payload: message.payload }, socket);
  }

  async forwardRequest(participant, message) {
    if (participant.role !== "guest") return;
    const host = this.socketFor(this.room.hostId);
    if (host) this.send(host, { type: "party:request", from: participant.id, payload: message.payload });
  }

  async relaySignal(participant, message) {
    const to = String(message.to || "");
    if (!to || to === participant.id) return;
    if (!message.data || typeof message.data !== "object" || JSON.stringify(message.data).length > MAX_SIGNAL_BYTES) return;
    const target = this.socketFor(to);
    if (target) this.send(target, { type: "signal", from: participant.id, kind: message.kind, data: message.data });
  }

  async transferHost(participant, message) {
    if (participant.role !== "host") return;
    const newHost = this.participants.get(message.participantId);
    if (!newHost || newHost.id === participant.id) return;
    participant.role = "guest";
    newHost.role = "host";
    this.room.hostId = newHost.id;
    await this.persist();
    this.broadcast({ type: "party:host-changed", hostId: newHost.id });
  }

  async closeRoom(participant) {
    if (participant.role !== "host") return;
    this.room.closed = true;
    await this.persist();
    this.broadcast({ type: "party:closed", reason: "host_closed" });
    for (const socket of this.ctx.getWebSockets()) socket.close(1000, "host_closed");
  }

  async webSocketClose(socket, code, reason) {
    const state = socket.deserializeAttachment();
    if (!state) return;
    const participant = this.participants.get(state.participantId);
    if (!participant) return;
    const wasHost = participant.role === "host";
    this.participants.delete(participant.id);

    if (this.participants.size === 0) {
      this.room.closed = true;
      await this.persist();
      return;
    }

    this.broadcast({ type: "peer:left", participantId: participant.id }, socket);

    if (wasHost) {
      const next = [...this.participants.values()].sort((a, b) => a.joinedAt - b.joinedAt)[0];
      next.role = "host";
      this.room.hostId = next.id;
      this.broadcast({ type: "party:host-changed", hostId: next.id }, socket);
    }

    await this.persist();
  }

  async webSocketError(socket) {
    await this.webSocketClose(socket);
  }
}
