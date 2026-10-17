# Sprint Board — Orchard Audited Fork

**Sprint Goal:** Establecer la línea base auditada en el fork `orchard-audited`, desmantelar el actualizador no consentido hacia `depot.sfg545.dev` reemplazándolo por clave y repositorio propio, documentar auditoría y habilitar el pipeline controlado con soporte RPM.

---

## 📋 To Do
- [ ] **HU-ORCH-03: Actualizador con Confirmación del Usuario**
  - [Responsable: DeepSeek Dev] Comprobación de nueva versión en el repositorio del fork y consulta única al usuario de si desea actualizar o no, sin validación de hash en la aplicación.
  - [Responsable: Claude PM/QA] Verificación de que no ocurran descargas ni actualizaciones sin la confirmación del usuario.

- [ ] **HU-ORCH-04: Workflow Monitor del Upstream**
  - [Responsable: Claude PM/QA] Crear `.github/workflows/monitor-upstream.yml` fijado por SHA, permisos mínimos y creación de issue con diff.

- [ ] **HU-ORCH-11: Soporte y Compilación para Android TV (D-Pad & Leanback)**
  - [Responsable: Claude PM/QA] Adaptación de UI, navegación con D-Pad focus traversal, manifiesto Leanback y generación de APK en el pipeline de releases.
  - [Responsable: DeepSeek Dev] Integración del reproductor y sincronización continua con Orchard Connect en Android TV.

- [ ] **HU-ORCH-12: Chromecast con CastV2 propio, sin Play Services** (contrato `SDD-chromecast-castv2.md`; C1 aprobado por el propietario, `AUDIT.md` §5.10)
  - [x] [Responsable: Claude PM/QA] Contrato SDD y registro de la decisión en `AUDIT.md`/`UPSTREAM.md`. Pendiente revisión del PO antes de lanzar a DeepSeek.
  - [x] [Responsable: DeepSeek Dev] T1 codec `CastMessage` + framing (`cast/CastMessage.kt`, `CastMessageTest.kt`, vectores en `tests/fixtures/castv2/`).
    - QA Claude: conforme a §4.2, §4.3 y CAST-INV-8/9 (sin `android.*`, cabecera AGPL-3.0-or-later, 281 y 229 líneas). Vectores `CONNECT` y `PING` verificados contra un codificador de referencia independiente.
    - Corregido en QA: límite de 32 niveles al saltar grupos desconocidos (frame de 64 KiB con grupos anidados desbordaba la pila), `decodeFrame` rechaza bytes sobrantes, tests leen los `.hex` de `tests/fixtures/castv2/`, tests nuevos de límite exacto 65536, bytes sobrantes y grupos anidados.
    - Pendiente: ejecutar `testDebugUnitTest` en Actions (sin SDK de Android en local) y decisión del propietario sobre el titular del copyright de los archivos nuevos (hoy `SFG545`).
  - [x] [Responsable: DeepSeek Dev] T2 `CastChannel` TLS + pin TOFU (`cast/CastChannel.kt`, `cast/CastTrust.kt`, `CastChannelTest.kt`, `CastTrustTest.kt`).
    - QA Claude: conforme a §4.2, §4.4 y CAST-INV-7/8/9 (sin `android.*`, cabecera AGPL-3.0-or-later, 320/130/382/261 líneas). TLS sin CA con pin SHA-256 por `id`, timeout de conexión 5 s, correlación por `requestId` con timeout 10 s, `requestId: 0` va a listeners.
    - Corregido en QA: `CastTrust.createSslContext` inexistente y falta de `import java.io.IOException` en el test (no compilaban); handshake TLS explícito con timeout en `open()` y `CastSecurityException` de pin desenvuelta del `SSLHandshakeException`; el socket se cierra si falla connect/handshake; la IP se conecta desde los bytes validados (`parsePrivateIpv4`, sin DNS) y se rechazan literales con ceros a la izquierda; longitud con bit alto (negativa) cerraba por `NegativeArraySizeException`, ahora es `CastProtocolException`; carrera `open()`/`close()`; payload no objeto descartado en silencio, ahora `IllegalArgumentException`. Tests nuevos: bordes de rango, literales no canónicos, pin por dispositivo, pin intacto tras mismatch, frame >64 KiB, longitud con bit alto, cuerpo mal formado, EOF del par con `onClose` único, listener que lanza, payload no objeto.
    - Pendiente: `testDebugUnitTest` en Actions (sin SDK ni `kotlinc` en local; revisión solo estática). Sin test de handshake TLS real (requiere certificado autofirmado de prueba); cubrir en T3 o QA manual. El JSON inválido (CAST-INV-8) se valida en T3/T4, el canal no parsea JSON.
  - [x] [Responsable: DeepSeek Dev] T3 `CastSession` (`cast/CastSession.kt`, `cast/CastJson.kt`, `CastSessionTest.kt`, `CastSessionTestSupport.kt`).
    - QA Claude: conforme a §4.4, §4.5 y CAST-INV-5/8/9 (sin `android.*`, cabecera AGPL-3.0-or-later, 413/144/323/165 líneas). `CONNECT` a `receiver-0` → `GET_STATUS` → `LAUNCH CC1AD845` solo si no corre → `CONNECT` al `transportId`; `PING` cada 5 s y `PONG` a cada `PING`; 15 s sin tráfico = un reintento de reconexión por caída; `close()` envía `CLOSE` al transporte y a `receiver-0`.
    - Corregido en QA: `CLOSE` entrante ignorado (§4.4), ahora termina la sesión, igual que un `RECEIVER_STATUS` sin la app; JSON inválido se descartaba en silencio, ahora cierra el canal (CAST-INV-8); parser JSON sin límite de anidamiento (frame de 64 KiB de `[` desbordaba la pila del lector), ahora 32 niveles y movido a `CastJson.kt` para reutilizarlo en T4 (el archivo quedaba en 499 líneas); la reconexión creaba el `CastChannel` sin el `SocketFactory` inyectado (`socketFactory` pasa a `internal`); `SET_VOLUME` mandaba `level` y `muted` juntos, ahora van por separado y `NaN` se ignora; la reconexión emitía `DISCONNECTED` transitorio; carrera `CLOSE`/`READY` que dejaba la sesión en `READY` con el canal cerrado (transición guardada); `GET_STATUS` sin `RECEIVER_STATUS` reutilizaba apps obsoletas; `transportId` vacío aceptado; el ejecutor del heartbeat nunca se apagaba. Tests nuevos: constante `CC1AD845`, `CLOSE` entrante, JSON inválido, app ausente, reconexión exitosa sin `DISCONNECTED`, reconexión fallida; test de `PONG` sin carrera con los `PING` periódicos.
    - Pendiente: `testDebugUnitTest` en Actions (sin SDK ni `kotlinc` en local; revisión solo estática). Peor caso de corte de Wi-Fi: 20 s hasta detectar más hasta 15 s del reintento, puede superar el criterio "≤ 20 s" de §6; validar en QA manual (Q3). T4 necesitará acceso al canal activo tras una reconexión (`CastSession.channel` es el original).
  - [ ] [Responsable: DeepSeek Dev] T4 `CastMediaChannel` · T5 `CastV2Player` + `ChromecastPlayback` · T6 `CastDiscovery` (`NsdManager`) · T7 `ChromecastStreamServer` con token por sesión.
  - [ ] [Responsable: Claude PM/QA] Q1 retiro de `media3-cast` y restos de Play Services · Q2 UI en `FrostedDevicesScreen.kt`, `SettingsPages.kt`, `SettingsIndex.kt`, `ChromecastSettingsRow.kt` · Q3 tests y verificación de `CAST-INV-*` · Q4 auditoría y zonas protegidas.
  - [ ] [Responsable: Antigravity PO] I1 rama `integrate/hu-orch-12` y commits `audit-fork:` por tarea.
  - Fase 2 (escritorio C++/Qt): fuera del sprint hasta orden del PO.

---

## 🏗️ In Progress
_(vacío)_

---

## 🧪 In Review / QA
- [ ] **HU-ORCH-05: Pipeline de Build, Release Automatizado y Empaquetado RPM** `[Responsable: DeepSeek Dev (backup de Claude PM/QA)]`
  - Hecho: `.github/workflows/release.yml` (Linux AppImage + RPM, prueba de instalación en Fedora, Windows 7z + setup, `SHA256SUMS.txt`, publicación en GitHub Releases del fork), `scripts/package-linux-rpm.sh`, `packaging/rpm/orchard.spec`. Eliminado `manual-desktop-build.yml` (publicaba en R2).
  - Backup de rol: Claude (PM/QA) alcanzó su rate limit (resets 6:20pm); DeepSeek Dev asumió las funciones de PM/QA para implementar y verificar esta HU.
  - Publicación directa: el job `publish` corre sin variable ni *environment* de aprobación cuando `github.repository == 'jucsp/orchard-audited'`.
  - Pendiente: primera ejecución en Actions, firma GPG del RPM y manifiestos firmados compatibles con el bootstrapper (REQ-A9, A10).
- [ ] **HU-ORCH-09: Eliminación total de Helsinki-NLP y modelos offline de traducción** `[Responsable: Claude PM/QA]`
  - Hecho: tabla de packs vacía (core, compartida con Android por JNI); proveedor por defecto vacío y sin `local` en escritorio; QML y Android sin selector `Local`, sin calidad y sin lista de modelos; test `packTableIsEmpty` (`AUDIT.md` §5.7).
  - QA: `npm test` 76/76; `g++ -fsyntax-only` con Qt real; `TranslationPacks` en ejecución con tabla vacía 3/3; `qmllint` sin errores.
  - Pendiente: commit, suite C++ con meson y build Android/Kotlin.
- [ ] **HU-ORCH-10: APK de Android en el pipeline de releases** `[Responsable: Claude PM/QA]`
  - Hecho: job `android` en `release.yml` (`assembleRelease`, NDK fijado por Gradle, artifact `Orchard-Android-arm64-v8a.apk`); `publish` lo exige, lo incluye en `SHA256SUMS.txt` y en las notas (`AUDIT.md` §5.9).
  - Firma: secretos `ANDROID_KEYSTORE_*` si existen; si no, keystore desechable generada en el runner.
  - QA: YAML válido, `bash -n` en todos los `run`, generación de keystore probada en local.
  - Pendiente: commit, primera ejecución en Actions y cargar los secretos `ANDROID_KEYSTORE_*` para que los APK se actualicen sin desinstalar.

---

## ✅ Done
- [x] Fork creado en GitHub (`jucsp/orchard-audited`) `[Responsable: Antigravity PO]`
- [x] Rama por defecto `audited` fijada en tag `v1.0.0-canary.9` `[Responsable: Antigravity PO]`
- [x] Repositorio clonado localmente y remoto upstream vinculado `[Responsable: Antigravity PO]`
- [x] Contrato SDD importado y Product Backlog inicializado `[Responsable: Antigravity PO]`
- [x] **HU-ORCH-01: Documentación y Trazabilidad del Fork** `[Responsable: Claude PM/QA]` (README con REQ-R3, AUDIT.md y UPSTREAM.md)
- [x] **HU-ORCH-02: Generación de Claves Propias y Desconexión Upstream** `[Responsable: DeepSeek Dev]` (clave `orchard-fork-1`, servidor del fork y fin del auto-update en segundo plano)
- [x] **Gobernanza y Reglas del Swarm:** `AGENTS.md`, `CLAUDE.md`, `GEMINI.md` establecidos y versionados `[Responsable: Claude PM/QA]`
- [x] **HU-ORCH-06: Soberanía de Servicios, Connect Propio y Listening Party** `[Responsable: DeepSeek Dev]`
  - Despliegue exitoso del Cloudflare Worker `orchard-account` en `connect.kasper-cloud.com` con base de datos D1 `orchard-accounts`, secretos cargados y Durable Objects `ConnectHub` y `PartyRoom`.
  - Servicio de Listening Party (salas compartidas) implementado y desplegado en `connect.kasper-cloud.com` con Durable Object `PartyRoom` (relay WebSocket y rooms), suite de pruebas unitarias 100% pasando (76/76 tests en `services/account`) y cliente Android `ListeningPartyClient.kt` reconfigurado al dominio propio. Verificación en vivo completada exitosamente.
  - También cubre: cuenta/Connect en escritorio y Android apuntando al dominio propio; auto-update off por defecto y sin sondeo; `artwork.sfg545.dev` y `lastfm.sfg545.dev` neutralizados en escritorio y Android.
- [x] **HU-ORCH-07: Atribución al Autor Original y Descargo de Responsabilidad** `[Responsable: DeepSeek Dev]` (README.md con crédito total a SFG545 y colaboradores, propósito de uso personal y privacidad, y descargo de responsabilidad)
- [x] **HU-ORCH-08: Purga de Frontend Last.fm y Chromecast** `[Responsable: DeepSeek Dev]`
  - Last.fm purgado de la UI de escritorio (`IntegrationsSection.ui.qml`) y móvil (`SettingsConnections.kt`, `SettingsIndex.kt`).
  - Controles de Chromecast purgados de la UI móvil (`FrostedDevicesScreen.kt`, `SettingsPages.kt`, `SettingsIndex.kt`) tras decisión de evitar pagos a Google y priorizar app nativa de Android TV para el futuro.
- [x] **Remediación de red H12–H15 (contra-auditoría `AUDIT.md` §5.6)** `[Responsable: Claude PM/QA]`
  - H12: retirados los 15 packs de traducción del HuggingFace del autor; quedan 12 de Helsinki-NLP.
  - H13: sonda de conectividad solo hacia `connect.kasper-cloud.com/.well-known/jwks.json`.
  - H14: sin Discord App ID del autor; ID propio por `ORCHARD_DISCORD_APP_ID` (escritorio) u `orchardDiscordAppId` (Gradle).
  - H15: sin inicialización de `CastContext`/`ChromecastPlayback` en Android.
  - Tests: 76/76 en `services/account`. Pendiente: suite C++ con meson, build Android y retirar la dependencia `media3-cast`.
