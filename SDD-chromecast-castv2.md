# Contrato SDD — Chromecast CastV2 propio (HU-ORCH-12)

- **Versión del contrato:** 1.0 · 2026-10-17 · borrador para revisión del PO
- **Propietario:** juanca
- **Decisión de origen:** el propietario aprobó la Opción 2 (CastV2 propio, sin Google Play Services) el 2026-10-17. Registro en `AUDIT.md` §5.10 (conflicto C1).
- **Contrato padre:** `SDD-orchard-audit-fork.md`. Sus invariantes INV-1 a INV-7 siguen vigentes; este documento agrega los invariantes `CAST-INV-*`.
- **Base de código:** `audited` en `d432ff5`.

---

## 1. Propósito

Que la app pueda enviar audio a Chromecast, Google TV y parlantes Google Home / Nest (Mini, Audio, Hub y grupos multizona) en la red local, sin:

- **Costos:** no se registra App ID en Google Cast SDK Developer Console (US$5). Se usa el Default Media Receiver público `CC1AD845`.
- **Play Services:** fuera `androidx.media3:media3-cast` y su dependencia transitiva `play-services-cast-framework`, y `androidx.mediarouter`. Ningún binario cerrado de Google en el APK por esta función.
- **Servidores externos:** ni del autor (`cast.sfg545.dev`), ni propios, ni de Google iniciados por la app. Todo el tráfico que origina la app queda en la LAN.

Es una excepción aprobada por el propietario a SDD §3.2 ("sin funcionalidades nuevas"): restaura una función del upstream que el fork había retirado (HU-ORCH-08, H15), con otra implementación.

## 2. Alcance

### 2.1 Fase 1: Android (dentro, se ejecuta)

1. Motor CastV2 en Kotlin puro: codificación de `CastMessage`, framing, canal TLS, sesión y canales `connection`, `heartbeat`, `receiver` y `media`.
2. Descubrimiento mDNS `_googlecast._tcp` con `NsdManager` del sistema.
3. Un `Player` de Media3 propio (`SimpleBasePlayer`) que sustituye a `RemoteCastPlayer`, para que `ChromecastPlayback` conserve una sola cola autoritativa.
4. `ChromecastStreamServer` endurecido (CAST-INV-4).
5. UI: selector de dispositivos en `FrostedDevicesScreen.kt`, fila en ajustes (`SettingsPages.kt`, `SettingsIndex.kt`) y reescritura de `ChromecastSettingsRow.kt` sin `CastContext` ni `MediaRouteButton`.
6. Retiro de `media3-cast`, `OrchardCastOptionsProvider.kt`, la meta-data `OPTIONS_PROVIDER_CLASS_NAME`, `MediaTransferReceiver` y el string `cast_app_id`.

### 2.2 Fase 2: escritorio (base, no se ejecuta sin orden del PO)

- Mismo protocolo en C++/Qt (`QSslSocket`; mDNS con `QUdpSocket` o Avahi por D-Bus en Linux), en un módulo nuevo `app/src/cast/`.
- Se reutilizan los vectores de prueba de la Fase 1 (`tests/fixtures/castv2/*.hex`), así ambas implementaciones validan contra los mismos bytes.
- Requiere servidor de stream equivalente en escritorio. Se diseña al abrir la fase.

### 2.3 Fuera

- Receptor propio (HTML en un host) y App ID registrado.
- Video, subtítulos, DRM y `customData` de receptores de terceros.
- Autenticación de dispositivo (`urn:x-cast:com.google.cast.tp.deviceauth`). El emisor no está obligado a hacerla con `CC1AD845`.
- IPv6 en la Fase 1.
- Integración con el selector de salida del sistema (Output Switcher). Depende de `MediaTransferReceiver` de `mediarouter`.

## 3. Invariantes

| ID | Invariante | Cómo se verifica |
| --- | --- | --- |
| CAST-INV-1 | Cero dependencias propietarias: sin `media3-cast`, `play-services-*`, `com.google.android.gms.*` ni `androidx.mediarouter` | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` sin `gms`, `play-services` ni `mediarouter`; `git grep com.google.android.gms` vacío en `mobile/` |
| CAST-INV-2 | Cero servidores externos originados por la app: solo mDNS (`224.0.0.251:5353`), TLS al IP:puerto anunciado y HTTP del receptor al teléfono | Revisión del código de red del paquete `cast`; captura opcional con `tcpdump` en QA manual |
| CAST-INV-3 | Opt-in: no hay multicast lock, `NsdManager`, socket ni servidor de stream hasta que el usuario abre el selector o pide conectar. La búsqueda se detiene al cerrar el selector o a los 30 s. Al arrancar la app no ocurre nada (H15 sigue cumplido) | Test de `OrchardPlaybackService`/`MainActivity` sin instancias de cast al iniciar; revisión de llamadas a `discoverServices` |
| CAST-INV-4 | `ChromecastStreamServer` vive solo durante una sesión activa: token de 128 bits con `SecureRandom` nuevo por sesión, escucha en la IPv4 de la LAN, atiende solo al IP del receptor conectado, no lista contenido, responde 404 a todo lo demás y se cierra al terminar la sesión | Tests JVM del servidor (token inválido, IP ajena, cierre) |
| CAST-INV-5 | App ID fijo `CC1AD845` como constante. Sin campo, string ni propiedad Gradle para cambiarlo | `git grep -i cast_app_id` vacío; test de la constante |
| CAST-INV-6 | Sin telemetría. Los logs no incluyen token, URL del stream ni `deviceId` completo | Revisión de `Log.*` en el paquete |
| CAST-INV-7 | Solo se conecta a direcciones privadas (RFC 1918 y link-local; `100.64.0.0/10` no se acepta). TLS sin validar CA (los dispositivos usan certificados autofirmados), con pin TOFU: SHA-256 del certificado guardado por `id` del TXT; si cambia, se aborta y se avisa | Tests del filtro de IP y del pin (primer uso, coincidencia, cambio) |
| CAST-INV-8 | Mensajes de 64 KiB como máximo, en ambos sentidos. Frame mal formado, longitud excesiva o JSON inválido cierran el canal sin colgar la app | Tests del codec y del lector |
| CAST-INV-9 | Archivos de menos de 500 líneas (`contributing.md`). Los archivos nuevos llevan cabecera AGPL-3.0-or-later; los del upstream que se modifican conservan la de SFG545 | Revisión QA |

Una implementación que viole un `CAST-INV` o un `INV` del contrato padre no se integra.

## 4. Arquitectura del protocolo

### 4.1 Descubrimiento

- Servicio `_googlecast._tcp.local.` con `NsdManager.discoverServices` y resolución por servicio. Antes de buscar se toma `WifiManager.MulticastLock` (permiso normal `CHANGE_WIFI_MULTICAST_STATE`); se libera al terminar.
- TXT usados: `id` (identificador estable, clave del pin TOFU), `fn` (nombre visible), `md` (modelo), `ca` (bitmask de capacidades), `rs` (app en ejecución). Un parlante sin video tiene `ca` sin el bit de video: se muestra igual, porque el contenido es solo audio.
- Puerto: el del registro SRV. Suele ser `8009`, pero los **grupos multizona** anuncian otro (p. ej. `32xxx`). No se asume `8009`.
- `targetSdk` es 36. Si sube a una versión que exija permiso de red local, se pide solo al abrir el selector (CAST-INV-3). Verificar al cambiar `targetSdk`.

### 4.2 Transporte y framing

- TCP + TLS al IP:puerto resuelto, timeout de conexión de 5 s. `SSLSocket` con un `TrustManager` que acepta el certificado y luego aplica el pin TOFU (CAST-INV-7).
- Cada mensaje: longitud de 4 bytes big-endian (sin signo) seguida del `CastMessage` serializado. Longitud > 65536 = error de protocolo.

### 4.3 `CastMessage` sin librería de protobuf

Codificación proto2 a mano (varint + length-delimited). No se agrega `protobuf-lite`.

| Campo | N.º | Tipo | Tag | Notas |
| --- | --- | --- | --- | --- |
| `protocol_version` | 1 | enum | `0x08` | Siempre `0` (`CASTV2_1_0`). Es `required`: se escribe aunque sea 0 |
| `source_id` | 2 | string | `0x12` | `sender-0` |
| `destination_id` | 3 | string | `0x1a` | `receiver-0` o el `transportId` de la app |
| `namespace` | 4 | string | `0x22` | Ver 4.4 |
| `payload_type` | 5 | enum | `0x28` | `0` STRING, `1` BINARY. `required` |
| `payload_utf8` | 6 | string | `0x32` | JSON |
| `payload_binary` | 7 | bytes | `0x3a` | No se usa; al decodificar se ignora |

El decodificador ignora campos desconocidos según su wire type y falla ante varints de más de 5 bytes para longitudes.

### 4.4 Canales

| Namespace | Mensajes | Regla |
| --- | --- | --- |
| `urn:x-cast:com.google.cast.tp.connection` | `CONNECT`, `CLOSE` | `CONNECT` a `receiver-0` al abrir y al `transportId` tras lanzar la app. `CLOSE` recibido = la app o el dispositivo cerró la sesión |
| `urn:x-cast:com.google.cast.tp.heartbeat` | `PING`, `PONG` | El emisor envía `PING` cada 5 s y responde `PONG` a cada `PING`. Sin ningún mensaje en 15 s: se cierra el canal y se reintenta una vez |
| `urn:x-cast:com.google.cast.receiver` | `GET_STATUS`, `LAUNCH`, `STOP`, `SET_VOLUME`, `RECEIVER_STATUS` | `LAUNCH` con `appId: "CC1AD845"`. `SET_VOLUME` aquí es el volumen del dispositivo (`level` 0..1 o `muted`), el que importa en un Nest Mini |
| `urn:x-cast:com.google.cast.media` | `LOAD`, `PLAY`, `PAUSE`, `SEEK`, `STOP`, `GET_STATUS`, `MEDIA_STATUS`, `LOAD_FAILED`, `INVALID_REQUEST` | Todo comando salvo `LOAD` lleva el `mediaSessionId` del último `MEDIA_STATUS` |

`requestId` entero creciente por canal TLS. Las respuestas se correlacionan por `requestId` con timeout de 10 s. Los `MEDIA_STATUS` con `requestId: 0` son eventos no solicitados.

### 4.5 Secuencia de una sesión

1. Usuario elige dispositivo → TLS → `CONNECT` a `receiver-0` → `GET_STATUS`.
2. Si `CC1AD845` no está en `RECEIVER_STATUS.applications`: `LAUNCH`, esperar `RECEIVER_STATUS` con la app y su `transportId` y `sessionId`.
3. `CONNECT` al `transportId`.
4. Se abre `ChromecastStreamServer` con token nuevo; `LOAD`:

   ```json
   {"type":"LOAD","requestId":7,"sessionId":"<sessionId>","autoplay":true,"currentTime":12.5,
    "media":{"contentId":"http://192.168.1.20:41234/cast/<token>/<mediaId>","contentType":"audio/mp4",
     "streamType":"BUFFERED","metadata":{"metadataType":3,"title":"…","artist":"…","albumName":"…",
     "images":[{"url":"https://i.ytimg.com/…"}]}}}
   ```

5. `MEDIA_STATUS` alimenta el `Player`: `playerState` (`IDLE`, `BUFFERING`, `PLAYING`, `PAUSED`), `currentTime`, `media.duration`, `idleReason`.
6. `idleReason: FINISHED` → el teléfono, dueño de la cola, envía `LOAD` del siguiente ítem. Sin `QUEUE_LOAD` en la Fase 1.
7. Desconectar: `STOP` en `media` (si el usuario detiene) o solo `CLOSE` (si sale dejando de controlar), cierre del socket y del servidor de stream; la reproducción vuelve al teléfono en la posición conocida.

### 4.6 Formatos

`contentType` debe ser el MIME real del stream que sirve `ChromecastStreamServer`. Verificar en dispositivo real que `CC1AD845` reproduce AAC en MP4 y Opus en WebM; si un modelo rechaza Opus (`LOAD_FAILED`), resolver el formato AAC para la sesión de cast.

Las URL de carátula se pasan tal cual; las descarga el dispositivo desde hosts ya inventariados en `AUDIT.md` §5.2. El Chromecast descarga el receptor `CC1AD845` desde Google: es tráfico del dispositivo, no de la app, y queda fuera de CAST-INV-2.

### 4.7 Componentes Android

Paquete nuevo `dev.sfg.orchard.mobile.cast`. Las clases de protocolo no importan `android.*` y se prueban en JVM.

| Archivo | Responsabilidad | `android.*` |
| --- | --- | --- |
| `CastMessage.kt` | Modelo y codec proto2 + framing | No |
| `CastChannel.kt` | Socket TLS, lector, escritor serializado, `requestId`, correlación, límites | No (recibe un `SocketFactory`) |
| `CastTrust.kt` | Pin TOFU y filtro de IP privada | No (almacén inyectado) |
| `CastSession.kt` | Máquina de estados: connection, heartbeat, receiver, `LAUNCH`, reconexión | No |
| `CastMediaChannel.kt` | Comandos de media y parseo de `MEDIA_STATUS` | No |
| `CastDiscovery.kt` | `NsdManager`, `MulticastLock`, parseo de TXT, timeout | Sí |
| `CastV2Player.kt` | `SimpleBasePlayer` sobre `CastSession`/`CastMediaChannel` | Sí (Media3) |
| `playback/ChromecastPlayback.kt` | Handoff teléfono ↔ receptor usando `CastV2Player` | Sí (existente, se adapta) |
| `playback/ChromecastStreamServer.kt` | Servidor con CAST-INV-4 | Sí (existente, se endurece) |

`ChromecastMediaItemConverter.kt` depende de `com.google.android.gms.cast.MediaInfo`: se reemplaza por la construcción del JSON de `LOAD` en `CastMediaChannel`.

## 5. Desglose para el Swarm

| ID | Tarea | Responsable | Entrega | Depende de |
| --- | --- | --- | --- | --- |
| T1 | Codec `CastMessage` + framing, con vectores hex en `tests/fixtures/castv2/` | DeepSeek Dev | `CastMessage.kt` + tests JVM | — |
| T2 | `CastChannel` TLS, `CastTrust` (TOFU, IP privada), límites y timeouts | DeepSeek Dev | `CastChannel.kt`, `CastTrust.kt` + tests con socket falso | T1 |
| T3 | `CastSession`: connection, heartbeat, receiver, `LAUNCH CC1AD845`, `SET_VOLUME` de dispositivo, reconexión única | DeepSeek Dev | `CastSession.kt` + tests con receptor simulado | T2 |
| T4 | `CastMediaChannel`: `LOAD`/`PLAY`/`PAUSE`/`SEEK`/`STOP`/`GET_STATUS`, `MEDIA_STATUS`, avance de cola por `FINISHED` | DeepSeek Dev | `CastMediaChannel.kt` + tests | T3 |
| T5 | `CastV2Player` (`SimpleBasePlayer`) y adaptación de `ChromecastPlayback` | DeepSeek Dev | `CastV2Player.kt`, `ChromecastPlayback.kt` | T4 |
| T6 | `CastDiscovery` con `NsdManager` y `MulticastLock`, opt-in y timeout de 30 s | DeepSeek Dev | `CastDiscovery.kt` + test de parseo TXT | — |
| T7 | Endurecer `ChromecastStreamServer` (CAST-INV-4) y ligar su ciclo de vida a la sesión | DeepSeek Dev | `ChromecastStreamServer.kt` + tests | — |
| Q1 | Retirar `media3-cast`, `OrchardCastOptionsProvider.kt`, meta-data del manifest, `MediaTransferReceiver`, `cast_app_id`, `ChromecastMediaItemConverter.kt` | Claude PM/QA | `build.gradle`, `AndroidManifest.xml`, `strings.xml` | T5 |
| Q2 | UI: selector en `FrostedDevicesScreen.kt`, ajustes en `SettingsPages.kt` y `SettingsIndex.kt`, `ChromecastSettingsRow.kt` sin `CastContext`; volumen de dispositivo y estado de conexión; operable con D-Pad (HU-ORCH-11) | Claude PM/QA | Compose | T5, T6 |
| Q3 | QA: tests JVM, `assembleRelease` en CI, verificación de CAST-INV-1..9 y prueba manual (sección 6) | Claude PM/QA | Informe en `AUDIT.md` | Q1, Q2 |
| Q4 | Auditoría: zonas protegidas en `UPSTREAM.md`, cierre de C1 en `AUDIT.md`, `technical_memory.md` | Claude PM/QA | Docs | Q3 |
| I1 | Rama `integrate/hu-orch-12`, orden de commits `audit-fork:` (uno por tarea) | Antigravity PO | Rama | T1..Q4 |
| T8 | Fase 2 escritorio (C++/Qt) | DeepSeek Dev | Se especifica al abrir la fase | Orden del PO |

Cada tarea de DeepSeek se entrega con su test y se valida por Claude antes de la siguiente. DeepSeek no toca `build.gradle`, el manifest ni la UI.

## 6. Criterios de aceptación (Fase 1)

- [ ] CAST-INV-1: el árbol de dependencias de release no contiene `gms`, `play-services` ni `mediarouter`; el APK no trae `com.google.android.gms.cast`.
- [ ] Al arrancar la app no hay tráfico mDNS ni sockets de cast (CAST-INV-3).
- [ ] Descubre en la LAN al menos un Chromecast o Google TV, un Nest/Home Mini y un grupo multizona, con su nombre (`fn`).
- [ ] Conecta, lanza `CC1AD845` y reproduce la pista actual desde la posición del teléfono.
- [ ] Play, pausa, seek, siguiente/anterior y volumen del dispositivo funcionan desde la app y desde la notificación de Media3.
- [ ] Al terminar una pista, la siguiente de la cola del teléfono empieza sola.
- [ ] Desconectar devuelve la reproducción al teléfono en la posición conocida y cierra el servidor de stream.
- [ ] Cortar el Wi-Fi o apagar el dispositivo termina la sesión en ≤ 20 s sin bloquear la app.
- [ ] El servidor de stream rechaza token inválido e IP distinta del receptor (CAST-INV-4).
- [ ] Certificado distinto para el mismo `id` aborta la conexión con aviso (CAST-INV-7).
- [ ] Tests JVM de T1–T7 en verde; `assembleRelease` en verde en Actions.
- [ ] `AUDIT.md` con el informe QA y C1 cerrado; `UPSTREAM.md` con las zonas nuevas activas.

## 7. Riesgos

| Riesgo | Mitigación |
| --- | --- |
| Google cambia o retira `CC1AD845` | Es el receptor por defecto documentado del SDK; si falla, se evalúa un receptor propio (requiere App ID, fuera de este contrato) |
| TLS sin CA permite MITM en la primera conexión | Pin TOFU; la superficie es la LAN del usuario |
| Modelos que no aceptan un formato | Sección 4.6; QA en hardware real |
| Wi-Fi con aislamiento de clientes o mDNS filtrado | Mensaje claro en el selector; sin alternativa en la nube por CAST-INV-2 |
| Firewall del teléfono o VPN bloquean el servidor de stream | `localIpv4Address()` ya excluye VPN; documentar en la UI |
| Sin hardware en CI | Pruebas de protocolo con receptor simulado; la prueba en dispositivo real es manual y la registra el propietario |
