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

---

## 🏗️ In Progress
_(vacío)_

---

## 🧪 In Review / QA
_(vacío)_

---

## ✅ Done
- [x] **HU-ORCH-12: Chromecast con CastV2 propio, sin Play Services** `[Responsables: DeepSeek Dev & Claude PM/QA]`
  - Re-implementación soberana completa: codec proto2 `CastMessage`, canal TLS con TOFU `CastTrust` y `CastChannel`, máquina de estados `CastSession` con Default Media Receiver (`CC1AD845`), sub-canal `CastMediaChannel`, servidor de streaming con token de 128 bits y filtro de IP `ChromecastStreamServer`, descubrimiento mDNS `CastDiscovery`, reproductor `CastV2Player` (`SimpleBasePlayer`) y traspaso local ↔ Cast en `ChromecastPlayback`.
  - Retiro de dependencias privativas (`media3-cast`), UI Jetpack Compose con soporte D-Pad TV y persistencia TOFU en SharedPreferences. 95 tests unitarios en 9 clases pasando en CI y publicado en release `v1.0.0-canary.9-audited.11`.
- [x] **HU-ORCH-05: Pipeline de Build, Release Automatizado y Empaquetado RPM** `[Responsables: DeepSeek Dev & Claude PM/QA]`
  - CI/CD multi-plataforma en `.github/workflows/release.yml`: Linux AppImage, RPM Fedora (`packaging/rpm/orchard.spec`, `package-linux-rpm.sh`), verificación en contenedor Fedora 44 (`rpm-check`), Windows setup NSIS y 7z, publicación automatizada en GitHub Releases con sumas SHA256. Verificado en vivo en release `v1.0.0-canary.9-audited.11`.
- [x] **HU-ORCH-09: Eliminación total de Helsinki-NLP y modelos offline de traducción** `[Responsable: Claude PM/QA]`
  - Purga total de modelos offline: tabla de packs vacía en core C++/JNI, selector removido de QML y Android Compose, cero descargas a HuggingFace. Verificado con `packTableIsEmpty` y publicado en el fork.
- [x] **HU-ORCH-10: APK de Android en el pipeline de releases** `[Responsable: Claude PM/QA]`
  - Job `android` integrado en `release.yml`: compilación `assembleRelease` con toolchains Rust/Cargo-NDK, firma automatizada y artefacto `Orchard-Android-arm64-v8a.apk` generado y publicado en cada release.
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
