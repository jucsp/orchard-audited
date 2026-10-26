# Sprint Board — Orchard Audited Fork

**Sprint Goal:** Establecer la línea base auditada en el fork `orchard-audited`, desmantelar el actualizador no consentido hacia `depot.sfg545.dev` reemplazándolo por clave y repositorio propio, documentar auditoría y habilitar el pipeline controlado con soporte RPM.

---

## 📋 To Do
- [ ] **HU-ORCH-03: Actualizador con Confirmación del Usuario**
  - [Responsable: DeepSeek Dev] Comprobación de nueva versión en el repositorio del fork y consulta única al usuario de si desea actualizar o no, sin validación de hash en la aplicación.
  - [Responsable: Claude PM/QA] Verificación de que no ocurran descargas ni actualizaciones sin la confirmación del usuario.

- [ ] **HU-ORCH-04: Workflow Monitor del Upstream**
  - [Responsable: Claude PM/QA] Crear `.github/workflows/monitor-upstream.yml` fijado por SHA, permisos mínimos y creación de issue con diff.

---

## 🏗️ In Progress
_Rama de trabajo: `integrate/hu-orch-19-20-21`._

- [ ] **HU-ORCH-19: Soporte de Navegación y Reproducción Completa en Android Auto** · Issue [#1](https://github.com/jucsp/orchard-audited/issues/1) · `SDD-android-auto.md` 1.0 APROBADO
  - [Responsable: DeepSeek Dev] Implementar árbol de exploración en `MediaLibraryService` y metadatos `com.google.android.gms.car.application` (`automotive_app_desc.xml`) para habilitar la apertura y navegación de Orchard en la consola del vehículo.
  - [Responsable: Claude PM/QA] Revisión de manifiesto, validación de que no se introduzcan dependencias privativas y ejecución de tests unitarios.

- [ ] **HU-ORCH-20: Gestión de Rotación de Certificados TLS y Credenciales TOFU en CastV2 (Google Nest Mini)** · Issue [#2](https://github.com/jucsp/orchard-audited/issues/2) · `SDD-castv2-tofu-rotation.md` 1.0 APROBADO
  - [Responsable: DeepSeek Dev] Diagnosticar y corregir el rechazo de credenciales/certificados en `CastTrust` / `CastSession` ante rotación del certificado TLS autofirmado de Google Nest Mini y validar autorización en `ChromecastStreamServer`.
  - [Responsable: Claude PM/QA] Auditoría de seguridad del flujo TOFU (`CAST-INV-*`) y validación de tests unitarios de CastV2.

- [ ] **HU-ORCH-21: Propagación Integral de Credenciales (Orchard Connect e Integraciones) vía Login QR en Android TV** · Issue [#3](https://github.com/jucsp/orchard-audited/issues/3) · `SDD-tv-qr-pairing-connect.md` 1.0 APROBADO
  - [Responsable: DeepSeek Dev] Extender el payload cifrado `orchard-pair-v1` para transferir la sesión/tokens de Orchard Connect junto con todas las integraciones activas (YouTube Music, Spotify, Qobuz) e inicializar Orchard Connect en Android TV al completar el emparejamiento QR.
  - [Responsable: Claude PM/QA] Adaptar UI de confirmación en móvil/TV, verificar invariantes `PAIR-INV-1` a `PAIR-INV-8` y validar suite de pruebas unitarias de emparejamiento.

---

## 🧪 In Review / QA
_(vacío)_

---

## ✅ Done
- [x] **HU-ORCH-16: Corrección de pantalla negra en video musical y playhead continuo (Android TV)** `[Responsables: DeepSeek Dev & Claude PM/QA]`
  - Abrir el video desde el reproductor activa el modo video antes de navegar a `TvRoutes.MUSIC_VIDEO`; salir con Atrás, Cerrar o "Volver a la canción" lo desactiva.
  - La posición se conserva: `switchVideoSource` cambia la fuente sin reiniciar el reproductor.
- [x] **HU-ORCH-17: Soporte Multicuentas en Android TV (YouTube Music multi-session, cambio en 1 clic y login QR)** `[Responsables: DeepSeek Dev & Claude PM/QA]`
  - `SecureYouTubeSessionStore` guarda varias sesiones cifradas (`AndroidKeystoreCipher`) y migra la sesión única existente; id de cuenta = SHA-256 truncado a 16 bytes.
  - `AccountSwitcher` cambia o elimina cuentas y vacía las colecciones sincronizadas de la cuenta anterior (`clearAccountCollections()`).
  - Ajustes TV → "Cuentas guardadas": cuenta activa, cambio en 1 clic, eliminar, "+ Agregar otra cuenta" por QR (HU-ORCH-13) y "Cambiar de canal" para cuentas de marca.
- [x] **HU-ORCH-18: Compatibilidad con Podcasts en YouTube Music (búsqueda y biblioteca)** `[Responsables: DeepSeek Dev & Claude PM/QA]`
  - Proveedor JS: tipo `podcast` (`MPSP`), episodios `musicMultiRowListItemRenderer`, filtro de búsqueda `EgWKAQJQAQ%3D%3D`, categoría de biblioteca "Podcasts" y detalle con `normalizePodcastDetail`.
  - Android: `LibraryFilter.PODCASTS`, `savedPodcasts` en `LibraryCache` separado de las playlists, filtro en biblioteca móvil y TV, sección en búsqueda.
  - 187/187 tests del proveedor `providers/youtube` en verde.
- [x] **HU-ORCH-14: Rediseño TV Etapa 2, Modo Nocturno y Salida con Control Remoto** `[Responsables: DeepSeek Dev, Claude PM/QA & Antigravity PO]`
  - Implementación completa de Etapa 2 en Compose para pantallas de 65" (safe area 48/27 dp, `ContentStartX` 116 dp, rail retráctil animado de 180 dp que empuja el contenido con `RailPush` de 136 dp y oscurece al 45%).
  - Fondos ambientales por rol con `TvAmbientBackdrop` (Carátula animada Kawarp equilibrada/vibrante, resplandor, oscuro y noche).
  - Modo nocturno en reproductor: botón 🌙 en transporte y menú `TvPlayerMenu`, mini-reproductor de 170 dp sobre negro absoluto `#000000`, desplazamiento OLED anti-burn-in cada 60 s, temporizador de 8 s al pulsar D-pad y soporte de horario automático programado.
  - Navegación con control remoto y salida limpia: pulsar Atrás en `TvRoutes.HOME` con rail colapsado y sin diálogo cierra la aplicación (`Activity.finish()`); descarte prioritario de acciones `actionTrack` y colapso de rail al retroceder.
  - Ajustes nativos de TV en dos columnas (`TvSettingsScreen`, `TvSettingsPages`, `TvHomeLayoutScreen`) con `TvUiPrefs` fuera de `OrchardSettings`.
  - Preservación íntegra de vinculación por código QR y LAN (`HU-ORCH-13`).
- [x] **HU-ORCH-15: Infraestructura de Firma Permanente y Manifiestos de Actualización TV/Móvil** `[Responsables: DeepSeek Dev, Claude PM/QA & Antigravity PO]`
  - Almacén de claves permanente del fork (`orchard-release.jks`, RSA 4096 bits, 10.000 días) configurado en GitHub Secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`).
  - Resolución definitiva del error de incompatibilidad de firma ("las versiones no son compatibles") entre actualizaciones de APK.
  - Actualizador `UpdateManager.kt` adaptado para consultar `latest-android-tv.json` prioritariamente en TV con fallback al manifiesto móvil, evaluando compatibilidad con `manifestFitsDevice`.
  - Pipeline `.github/workflows/release.yml` actualizado para generar automáticamente `dist/latest-android.json` y `dist/latest-android-tv.json` con SHA-256 real de los APKs y subirlos a los releases del fork.
  - 16/16 tests unitarios en verde en `UpdateManagerTest.kt`.
- [x] **HU-ORCH-13: Login por Código QR y Vinculación Remota de Dispositivos (Android TV & Móvil)** `[Responsables: DeepSeek Dev, Claude PM/QA & Antigravity PO]`
  - Contrato formalizado en `SDD-tv-qr-pairing.md` bajo las invariantes `PAIR-INV-1` a `PAIR-INV-8`.
  - Protocolo criptográfico soberano `orchard-pair-v1` (ECDH P-256 + HKDF + AES-256-GCM) sin fuga a servidores externos, canal autenticado por huella SHA-256 en QR o SAS numérico de 6 dígitos.
  - Servidor efímero en la TV (`TvPairingServer`) en IPv4 privada, máx 5 fallos, timeout 300 s, mDNS `_orchard-pair._tcp` (`PairingAdvertiser`), pantalla TV `TvQrLoginScreen` (Canvas QR, timer, SAS y fallback web a `TvRoutes.LOGIN_WEB`) y diálogo `TvPairingConfirmDialog`.
  - Cliente móvil (`PairingClient`, `PairingDiscovery`), pantalla `PairWithTvScreen`, diálogo `PairConfirmDialog`, acceso en `SettingsConnections.kt` y receptor de deep link `orchard://pair` en `MainActivity.kt`.
  - Suite de pruebas unitarias 100% pasando en verde (43/43 tests JVM en 5 suites).
- [x] **HU-ORCH-11b: Paridad Funcional Total de Android TV con Móvil (Cuentas, Ajustes, Onboarding, Video y Social)** `[Responsables: DeepSeek Dev, Claude PM/QA & Antigravity PO]`
  - Conexión de `SettingsScreen` real (todas las secciones: cuentas, calidad de streaming, autoplay, crossfade, caché, descargas, actualizaciones y diseño de inicio).
  - Onboarding `WelcomeScreen` en primer arranque para TV y rutas de autenticación nativas (`NativeLoginScreen` para YouTube Music, `SpotifyLoginScreen`, `QobuzLoginScreen`, selector de cuenta).
  - Video musical en TV (`TvMusicVideoScreen`) a pantalla completa y salas de Listening Party en TV (`TvListeningPartyScreen`) con teclado en pantalla.
  - Menús contextuales de pista (`TvTrackActionsPopup` y `tvLongClickable`) por pulsación prolongada en Inicio, Reproductor, Detalle y Biblioteca.
  - Envoltura `TvSafeContent` para zona segura de TV (TV-INV-2) y estados vacíos con botones enfocables. Compilación y publicación en release oficial `v1.0.0-canary.9-audited.22`.
- [x] **HU-ORCH-11: Soporte y Compilación para Android TV (D-Pad & Leanback)** `[Responsables: DeepSeek Dev & Claude PM/QA]`
  - Implementación completa de la interfaz Opción A "Glass Rail" para Android TV: `TvDetection.kt`, tokens y tipografía TV en `TvTokens.kt`, sistema de foco unificado `Modifier.tvFocus` con 6 estilos (`Card`, `Button`, `Row`, `Key`, `NavItem`, `Transport`) con elevación de zIndex y escala sin alterar layout.
  - Rail lateral expandible `TvNavigationRail.kt`, Scaffold y NavHost `TvOrchardApp.kt`, pantalla de Inicio (`TvHomeScreen.kt`), Reproductor con paneles dinámicos (`TvNowPlaying.kt`, `TvLyricsPanel.kt`, `TvQueuePanel.kt`, `TvPanelTabs.kt`), Detalle de colección (`TvCollectionScreen.kt`), Búsqueda con teclado D-pad en pantalla (`TvSearchScreen.kt`) y Biblioteca (`TvLibraryScreen.kt`).
  - Manifiesto actualizado con `LEANBACK_LAUNCHER`, banner `tv_banner.png` y `touchscreen=false`. Actualizador `UpdateManager.kt` adaptado para discriminar `Orchard-AndroidTV-arm.apk` en TV.
  - Pipeline `.github/workflows/release.yml` actualizado para compilar y publicar `Orchard-AndroidTV-arm.apk` junto a `Orchard-Android-arm64-v8a.apk`. Verificación en verde en CI (Run #39179846143).
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
