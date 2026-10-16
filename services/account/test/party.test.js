import assert from "node:assert/strict";
import { beforeEach, describe, it } from "node:test";

import { generateRoomCode, MAX_PARTICIPANTS, normalizeCode, PartyRoom } from "../src/party.js";
import worker from "../src/index.js";

// Just enough of the Durable Object WebSocket hibernation API.
class FakeSocket {
  constructor() {
    this.sent = [];
    this.closed = null;
    this.attachment = null;
    this.tags = null;
  }
  send(text) {
    if (this.closed) throw new Error("closed");
    this.sent.push(JSON.parse(text));
  }
  close(code, reason) {
    this.closed = { code, reason };
  }
  serializeAttachment(value) {
    this.attachment = structuredClone(value);
  }
  deserializeAttachment() {
    return this.attachment;
  }
  last(type) {
    return [...this.sent].reverse().find((message) => message.type === type);
  }
}

class FakeCtx {
  constructor() {
    this.sockets = [];
    const data = new Map();
    this.storage = {
      get: async (key) => structuredClone(data.get(key)),
      put: async (key, value) => void data.set(key, structuredClone(value)),
      delete: async (key) => data.delete(key),
    };
  }
  acceptWebSocket(socket, tags) {
    socket.tags = tags;
    this.sockets.push(socket);
  }
  getWebSockets(tag) {
    return this.sockets.filter((socket) => !socket.closed && (!tag || socket.tags?.includes(tag)));
  }
}

function initRequest(code = "ABC234", hostName = "Host") {
  return new Request("https://party.test/init", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ code, hostName }),
  });
}

function joinRequest(name = "Guest") {
  return new Request("https://party.test/join", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ name }),
  });
}

describe("Listening party room", () => {
  let env;
  let room;

  beforeEach(() => {
    env = { PUBLIC_URL: "https://account.test" };
    room = new PartyRoom(new FakeCtx(), env);
  });

  async function create(hostName = "Host") {
    return (await room.fetch(initRequest("ABC234", hostName)).then((response) => response.json())).data;
  }

  async function join(name = "Guest") {
    return (await room.fetch(joinRequest(name)).then((response) => response.json())).data;
  }

  it("generates unambiguous room codes and normalises input", () => {
    for (let i = 0; i < 100; i++) {
      const code = generateRoomCode();
      assert.equal(code.length, 6);
      assert.match(code, /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/);
    }
    assert.equal(generateRoomCode(4).length, 4);
    assert.equal(normalizeCode("abc234"), "ABC234");
    assert.equal(normalizeCode("AB-C!234"), "ABC234");
  });

  it("creates a room and seats the host", async () => {
    const data = await create("Alice");
    assert.equal(data.room.id, "ABC234");
    assert.equal(data.room.code, "ABC234");
    assert.equal(data.room.hostId, data.participant.id);
    assert.equal(data.room.participantCount, 1);
    assert.equal(data.room.maxParticipants, 10);
    assert.equal(data.room.closed, false);
    assert.equal(data.room.socketUrl, "https://account.test/rooms/ABC234/socket");
    assert.equal(data.room.joinUrl, "https://account.test/party/ABC234");
    assert.equal(data.participant.role, "host");
    assert.ok(data.participant.token);
    assert.equal(data.participant.name, "Alice");
  });

  it("joins a room as a guest and rejects the eleventh listener", async () => {
    const host = await create();
    for (let i = 0; i < MAX_PARTICIPANTS - 1; i++) {
      const data = await join(`Guest ${i}`);
      assert.equal(data.room.participantCount, i + 2);
      assert.equal(data.participant.role, "guest");
    }
    await assert.rejects(room.fetch(joinRequest("Overflow")), { status: 409, code: "room_full" });
    assert.equal(room.participants.get(host.participant.id).role, "host");
  });

  it("rejects joining a closed room", async () => {
    await create();
    room.room.closed = true;
    await assert.rejects(room.fetch(joinRequest()), { status: 409, code: "room_closed" });
  });

  it("welcomes a socket with ICE servers and announces joins", async () => {
    const host = await create("Alice");
    const hostSocket = new FakeSocket();
    room.accept(hostSocket, host.participant.id);

    const welcome = hostSocket.last("party:welcome");
    assert.equal(welcome.role, "host");
    assert.deepEqual(welcome.iceServers, [{ urls: "stun:stun.cloudflare.com:3478" }]);
    assert.deepEqual(welcome.peers, []);
    assert.equal(welcome.lastState, null);
    assert.equal(welcome.room.hostId, host.participant.id);

    const guest = await join("Bob");
    const guestSocket = new FakeSocket();
    room.accept(guestSocket, guest.participant.id);

    assert.equal(guestSocket.last("party:welcome").role, "guest");
    assert.deepEqual(guestSocket.last("party:welcome").peers, [
      { id: host.participant.id, name: "Alice", role: "host" },
    ]);
    assert.deepEqual(hostSocket.last("peer:joined"), {
      type: "peer:joined",
      peer: { id: guest.participant.id, name: "Bob", role: "guest" },
    });
  });

  it("stores and relays the host's playback state", async () => {
    const host = await create();
    const guest = await join();
    const hostSocket = new FakeSocket();
    const guestSocket = new FakeSocket();
    room.accept(hostSocket, host.participant.id);
    room.accept(guestSocket, guest.participant.id);

    await room.webSocketMessage(hostSocket, JSON.stringify({ type: "party:update", payload: { isPlaying: true, currentTime: 12 } }));
    assert.deepEqual(guestSocket.last("party:update").payload, { isPlaying: true, currentTime: 12 });
    assert.deepEqual(room.lastState, { isPlaying: true, currentTime: 12 });

    // A guest cannot overwrite the room state.
    await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:update", payload: { evil: true } }));
    assert.deepEqual(room.lastState, { isPlaying: true, currentTime: 12 });

    // A late joiner replays the host's last snapshot.
    const late = await join("Late");
    const lateSocket = new FakeSocket();
    room.accept(lateSocket, late.participant.id);
    assert.deepEqual(lateSocket.last("party:welcome").lastState, {
      payload: { isPlaying: true, currentTime: 12 },
    });
  });

  it("relays signals and guest requests to the right peer", async () => {
    const host = await create();
    const guest = await join();
    const hostSocket = new FakeSocket();
    const guestSocket = new FakeSocket();
    room.accept(hostSocket, host.participant.id);
    room.accept(guestSocket, guest.participant.id);

    await room.webSocketMessage(guestSocket, JSON.stringify({ type: "signal", to: host.participant.id, kind: "offer", data: { sdp: "v=0" } }));
    assert.deepEqual(hostSocket.last("signal"), {
      type: "signal",
      from: guest.participant.id,
      kind: "offer",
      data: { sdp: "v=0" },
    });

    await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:request", payload: { action: "play" } }));
    assert.deepEqual(hostSocket.last("party:request"), {
      type: "party:request",
      from: guest.participant.id,
      payload: { action: "play" },
    });

    // A signal cannot loop back to its sender, and a host does not request itself.
    await room.webSocketMessage(guestSocket, JSON.stringify({ type: "signal", to: guest.participant.id, kind: "offer", data: { sdp: "x" } }));
    assert.deepEqual(guestSocket.last("signal"), undefined);
    const countBefore = hostSocket.sent.length;
    await room.webSocketMessage(hostSocket, JSON.stringify({ type: "party:request", payload: { action: "skip" } }));
    assert.equal(hostSocket.sent.length, countBefore);
  });

  it("announces when a participant leaves", async () => {
    const host = await create();
    const guest = await join();
    const hostSocket = new FakeSocket();
    const guestSocket = new FakeSocket();
    room.accept(hostSocket, host.participant.id);
    room.accept(guestSocket, guest.participant.id);

    guestSocket.close(1000, "bye");
    await room.webSocketClose(guestSocket, 1000, "bye");
    assert.deepEqual(hostSocket.last("peer:left"), { type: "peer:left", participantId: guest.participant.id });
    assert.equal(room.room.hostId, host.participant.id);
  });

  it("hands the host seat to the oldest listener when the host leaves", async () => {
    const host = await create();
    const first = await join("First");
    const second = await join("Second");
    const hostSocket = new FakeSocket();
    const firstSocket = new FakeSocket();
    const secondSocket = new FakeSocket();
    room.accept(hostSocket, host.participant.id);
    room.accept(firstSocket, first.participant.id);
    room.accept(secondSocket, second.participant.id);

    hostSocket.close(1000, "bye");
    await room.webSocketClose(hostSocket, 1000, "bye");

    assert.equal(room.room.hostId, first.participant.id);
    assert.equal(room.participants.get(first.participant.id).role, "host");
    assert.equal(firstSocket.last("peer:left").participantId, host.participant.id);
    assert.equal(firstSocket.last("party:host-changed").hostId, first.participant.id);
  });

  it("transfers the host seat on request and closes the room for everyone", async () => {
    const host = await create();
    const guest = await join();
    const hostSocket = new FakeSocket();
    const guestSocket = new FakeSocket();
    room.accept(hostSocket, host.participant.id);
    room.accept(guestSocket, guest.participant.id);

    await room.webSocketMessage(hostSocket, JSON.stringify({ type: "party:host-transfer", participantId: guest.participant.id }));
    assert.equal(room.room.hostId, guest.participant.id);
    assert.equal(room.participants.get(guest.participant.id).role, "host");
    assert.equal(room.participants.get(host.participant.id).role, "guest");
    assert.equal(hostSocket.last("party:host-changed").hostId, guest.participant.id);
    assert.equal(guestSocket.last("party:host-changed").hostId, guest.participant.id);

    await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:close" }));
    assert.equal(room.room.closed, true);
    assert.deepEqual(hostSocket.last("party:closed"), { type: "party:closed", reason: "host_closed" });
    assert.equal(hostSocket.closed.code, 1000);
  });

  it("ignores host-only messages from guests", async () => {
    const host = await create();
    const guest = await join();
    const hostSocket = new FakeSocket();
    const guestSocket = new FakeSocket();
    room.accept(hostSocket, host.participant.id);
    room.accept(guestSocket, guest.participant.id);

    await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:host-transfer", participantId: guest.participant.id }));
    assert.equal(room.room.hostId, host.participant.id);

    await room.webSocketMessage(guestSocket, JSON.stringify({ type: "party:close" }));
    assert.equal(room.room.closed, false);
  });
});

describe("Listening party routes", () => {
  let env;
  let forwarded;

  beforeEach(() => {
    forwarded = [];
    env = {
      PUBLIC_URL: "https://account.test",
      PARTY_ROOM: {
        idFromName: (name) => `party:${name}`,
        get: (id) => ({
          fetch: async (request) => {
            forwarded.push({ id, request });
            return new Response(JSON.stringify({ ok: true, data: { room: { id }, participant: {} } }), {
              headers: { "content-type": "application/json" },
            });
          },
        }),
      },
    };
  });

  it("creates a room with a fresh code and delegates to /init", async () => {
    const response = await worker.fetch(new Request("https://account.test/rooms", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ hostName: "Alice" }),
    }), env);

    const body = await response.json();
    assert.equal(body.ok, true);
    assert.equal(forwarded.length, 1);
    assert.match(forwarded[0].id, /^party:[A-Z2-9]{6}$/);
    assert.equal(new URL(forwarded[0].request.url).pathname, "/init");

    const init = await forwarded[0].request.json();
    assert.equal(init.hostName, "Alice");
    assert.equal(init.code, forwarded[0].id.slice("party:".length));
  });

  it("normalises the code on join and socket routes", async () => {
    const join = await worker.fetch(new Request("https://account.test/rooms/abc234/join", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ name: "Bob" }),
    }), env);
    assert.equal(join.status, 200);
    assert.equal(forwarded[0].id, "party:ABC234");
    assert.equal(new URL(forwarded[0].request.url).pathname, "/join");

    const socket = await worker.fetch(new Request("https://account.test/rooms/abc234/socket?participantId=p1&token=t1", {
      headers: { upgrade: "websocket" },
    }), env);
    assert.equal(socket.status, 200);
    assert.equal(forwarded[1].id, "party:ABC234");
    assert.match(forwarded[1].request.url, /participantId=p1/);
    assert.match(forwarded[1].request.url, /token=t1/);
  });

  it("requires a WebSocket upgrade for the socket route", async () => {
    const response = await worker.fetch(new Request("https://account.test/rooms/ABC234/socket"), env);
    assert.equal(response.status, 426);
    assert.equal((await response.json()).error, "upgrade_required");
    assert.equal(forwarded.length, 0);
  });

  it("serves a browser page for a party code", async () => {
    const response = await worker.fetch(new Request("https://account.test/party/ABC234"), env);
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-type"), /text\/html/);
    assert.match(await response.text(), /ABC234/);
  });
});
