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

- [ ] **HU-ORCH-12: Re-implementación de Soporte para Chromecast**
  - [Responsable: DeepSeek Dev] Restaurar e implementar `CastContext`, `ChromecastPlayback` y el manejo de sesión y envío de audio.
  - [Responsable: Claude PM/QA] Reincorporar los controles de Chromecast en la UI (`FrostedDevicesScreen.kt`, `SettingsPages.kt`, `SettingsIndex.kt`) y validar la transmisión.

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
