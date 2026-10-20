# Memoria Técnica — Orchard Audited Fork

## 1. Contexto y Repositorios
- **Upstream:** `https://github.com/sfg5453/orchard` (Licencia: AGPL-3.0)
- **Fork:** `https://github.com/jucsp/orchard-audited`
- **Rama principal:** `audited` (basada en el tag `v1.0.0-canary.9` commit `aa900002d0541ce6bd7beab8d6011a05d181daaa`).

## 2. Invariantes de Seguridad Críticas
- **INV-1 & INV-2:** Toda descarga e instalación requiere consentimiento explícito (flujo de 2 pasos).
- **INV-3:** Origen de descargas restringido estrictamente al fork (`jucsp/orchard-audited`). Cero llamadas a `depot.sfg545.dev`.
- **INV-5:** La clave privada de firma (`orchard-fork-1`) no debe entrar jamás al repositorio ni a CI en texto plano.

## 3. Puntos de Intervención en el Código Base
- `bootstrapper/src/security/TrustedKeys.cpp`: clave ECDSA P-256 de confianza ya sustituida por `orchard-fork-1` (HU-ORCH-02).
- `bootstrapper/src/update/ReleaseClient.hpp`: `kDefaultServer` ya apunta a GitHub Releases del fork (HU-ORCH-02).
- `bootstrapper/src/app/Bootstrapper.cpp`: lanzamiento automático de `--background-update` ya eliminado (HU-ORCH-02).
- `mobile/.../UpdateManager.kt`: updater Android ya apunta al fork (HU-ORCH-02).
- `.github/workflows/release.yml`: build Linux/Windows, `.rpm` y publicación en GitHub Releases del fork (HU-ORCH-05). `manual-desktop-build.yml` (R2) eliminado. Monitor del upstream pendiente (HU-ORCH-04).
- `scripts/package-linux-rpm.sh` + `packaging/rpm/orchard.spec`: RPM desde el AppDir en `/opt/orchard`, `Requires` calculados con `readelf` (sonames no incluidos), `AutoReqProv: no`.
- `app/src/account/orchard_account.cpp` + `mobile/.../OrchardAccountService.kt`: servicio de cuenta/Connect apunta a `https://connect.kasper-cloud.com` (HU-ORCH-06).
- `app/src/update/bootstrapper_client.{h,cpp}`: `m_autoUpdate` falso por defecto y sin sondeo periódico; el timer solo corre durante una actualización solicitada (INV-1, REQ-U5, HU-ORCH-06).
- `app/src/integrations/discord_artwork.cpp`: `artwork.sfg545.dev` ya no es host de confianza para el artwork (HU-ORCH-06).
- `app/src/integrations/lastfm.cpp` + `mobile/.../LastfmRepository.kt`: endpoint Last.fm vacío por defecto; sin llamadas a `lastfm.sfg545.dev` (HU-ORCH-06).
- `services/account/wrangler.production.jsonc`: config de producción del Worker propio (`orchard-account` en `connect.kasper-cloud.com`) (HU-ORCH-06).
- `services/account/src/party.js`: Durable Object `PartyRoom` (salas de Listening Party con relay WebSocket, transferencia de host y persistencia en `ctx.storage`) + `generateRoomCode()` (HU-ORCH-06).
- `mobile/.../social/ListeningPartyClient.kt`: `DEFAULT_SERVICE_URL` y `DEFAULT_PARTY_SERVICE_URL` apuntan a `https://connect.kasper-cloud.com` (HU-ORCH-06).
- `core/native/translation/translation_pack_table.cpp`: `translationPackTable()` devuelve un `std::span` vacío; sin packs del autor (H12) ni de Helsinki-NLP (HU-ORCH-09). Android lee la misma tabla por JNI.
- Traducción de letras solo por API (HU-ORCH-09): `lyrics_translator` con proveedor vacío por defecto y sin `local`; QML sin calidad ni lista de modelos; Android trata `LyricTranslationProvider.LOCAL` como "sin proveedor" y no traduce.
- `app/src/offline/connectivity_monitor.cpp`: sonda única a `connect.kasper-cloud.com/.well-known/jwks.json` (H13).
- `app/src/integrations/discord.cpp`, `discord_sdk_bridge.cpp`, `mobile/.../discord/DiscordArtwork.kt`: sin App ID por defecto; `ORCHARD_DISCORD_APP_ID` (escritorio) y `-PorchardDiscordAppId` (Android) (H14).
- `mobile/.../MainActivity.kt`, `OrchardPlaybackService.kt`: sin `CastContext`; `ChromecastPlayback` solo se construye al elegir un dispositivo (H15, CAST-INV-3).
- UI de escritorio/móvil: Last.fm purgado de `IntegrationsSection.ui.qml`, `SettingsConnections.kt` y `SettingsIndex.kt`; Chromecast purgado de `FrostedDevicesScreen.kt`, `SettingsPages.kt` y `SettingsIndex.kt` (HU-ORCH-08).

## 4. Estado de auditoría
- **Base integrada:** tag `v1.0.0-canary.9` (`aa900002d054`). Informe en `AUDIT.md` §1.
- **`upstream/main` `3c8acc1e1e39`** (30 commits sobre el tag): revisión estática sin malware, no promovida por ser rama y porque el updater sigue en `depot.sfg545.dev` (`AUDIT.md` §4). `bootstrapper/` sin cambios en ese rango.
- **Licencia del motor adaptativo:** OOL-1.0 en el tag (bloqueo L1); `upstream/main` lo relicencia a PolyForm Shield 1.0.0, lo que desbloquea L1 al promover un tag que lo incluya, conservando el `Required Notice`.
- **HU-ORCH-02 aplicada:** clave `orchard-fork-1`, servidor del fork y fin del auto-update en segundo plano. `grep` sin `depot.sfg545.dev`/`downloads.sfg545.dev` en `bootstrapper/` y `mobile/`.
- **HU-ORCH-06 aplicada y desplegada:** cuenta/Connect → `connect.kasper-cloud.com`; auto-update off por defecto y sin sondeo; `artwork.sfg545.dev` y `lastfm.sfg545.dev` neutralizados en escritorio y Android; `providers/apple-music/` no existe en este tag. Worker `orchard-account` desplegado en `connect.kasper-cloud.com` con D1 `orchard-accounts`, secretos cargados y Durable Objects `ConnectHub` y `PartyRoom`.
- **Contra-auditoría de red (§5):** H12–H15 resueltos en el árbol de trabajo (§5.6). El residual `media3-cast`/`MediaTransferReceiver` se retira en HU-ORCH-12 (§8).
- **HU-ORCH-09 en el árbol de trabajo:** sin modelos offline de traducción (`AUDIT.md` §5.7). Verificado: sintaxis C++ con Qt real, `TranslationPacks` en ejecución con tabla vacía (3/3), `qmllint`, `npm test` 76/76. Sin verificar: suite meson y build Android. `scripts/translation/` eliminada y `THIRD_PARTY_NOTICES.md` sin la sección de modelos de traducción (§5.8). Residual: runtime LiteRT/Marian sigue compilado sin packs.
- **HU-ORCH-07/08 aplicadas:** atribución al autor original y descargo de responsabilidad en `README.md`; purga de Last.fm y Chromecast en las UIs de escritorio y móvil.

## 5. Gobernanza de actualizaciones del upstream
Reglas canónicas en `AGENTS.md`; `CLAUDE.md` y `GEMINI.md` lo importan y añaden el checklist de su rol. Datos en `UPSTREAM.md`.
- **Auditoría previa:** nada del upstream entra a `audited` sin entrada en `AUDIT.md`. Solo se promueven tags.
- **Preservación del fork:** prohibido revertir parches, desacoples de red o adaptaciones; rutas en `UPSTREAM.md` → Zonas protegidas. Prohibidos `git merge upstream/*`, `git pull upstream` y "Sync fork".
- **Integración quirúrgica:** cada commit del rango se clasifica `aceptado`/`rechazado`/`conflicto`; se integra en `integrate/<tag>` por rebase (todo aceptado) o `cherry-pick -x` (selección).
- **Conflictos:** detenerse, registrar la propuesta con la plantilla de `AGENTS.md` en `AUDIT.md` y esperar la decisión del propietario.
- **Deuda de convención:** `1301e8f`, `f7923ae`, `3ead5f1` sin prefijo `audit-fork:`; `a019c98` y `c9fb6ad` con formato incompleto. Corregirlos reescribe historial y depende del propietario.

## 6. Pipeline de release (HU-ORCH-05)
- **Disparo:** push a `audited`/`main` → prerelease `v<versión>-audited.<run>`; tag `v*` → release `--latest` (el tag debe empezar por `v<versión de meson.build>`); `workflow_dispatch`.
- **Publicación:** job `publish` con `contents: write`, directo cuando `github.repository == 'jucsp/orchard-audited'`, sin variable de activación ni `environment: release`. Usa `gh release` con `GITHUB_TOKEN`; sin secretos adicionales.
- **Sin firmar todavía:** RPM sin GPG y sin manifiestos `orchard-signed-v1`. El bootstrapper busca `releases/<canal>.json` en `releases/latest/download/`, una ruta que GitHub Releases no sirve (assets planos). Se resuelve en HU-ORCH-03.
- **Deuda heredada (REQ-A6):** `pip install` sin hashes y `linuxdeploy` *continuous* sin verificar el hash en `package-linux-appimage.sh`.
- **Backup de rol (HU-ORCH-05):** Claude (PM/QA) alcanzó rate limit (resets 6:20pm); DeepSeek Dev asumió las funciones de PM/QA e implementó/verificó el pipeline y el RPM.
- **Deuda menor:** `docs/DEVELOPMENT.md` y `docs/BOOTSTRAPPER.md` aún enlazan a `.github/workflows/manual-desktop-build.yml` (borrado); actualizar en una HU futura.
- **APK de Android (HU-ORCH-10, HU-ORCH-15):** job `android` (`ubuntu-24.04`) con Node 24, JDK 17, Rust `aarch64-linux-android` y `x86_64-linux-android` y cargo-ndk. Instala los dos targets porque la tarea `cargo ndk` de `build.gradle` compila `orchardAbis` (arm64-v8a y x86_64) también en `assembleRelease`, aunque el APK solo empaquete arm64-v8a. Instala el NDK de `orchardNdkVersion` y fija `ANDROID_NDK_HOME` porque el del runner es otro. Publica `Orchard-Android-arm64-v8a.apk` y `Orchard-AndroidTV-arm.apk`. Firma permanente garantizada mediante los secretos `ANDROID_KEYSTORE_*` del fork (RSA 4096 bits, 10.000 días), permitiendo actualización directa sin desinstalación. Genera y publica `latest-android.json` y `latest-android-tv.json` con SHA-256 real.

## 7. Soberanía de servicios (HU-ORCH-06) y purga de frontend (HU-ORCH-07/08)
- **Worker propio (`orchard-account`):** desplegado en `connect.kasper-cloud.com` con base D1 `orchard-accounts`, secrets cargados (`GOOGLE_CLIENT_SECRET`, `SIGNING_KEY`, etc.) y Durable Objects `ConnectHub` y `PartyRoom`.
- **Listening Party (salas compartidas):** implementado en `services/account/src/party.js` (Durable Object `PartyRoom` con relay WebSocket, transferencia de host, persistencia y código de sala sin caracteres ambiguos). Desplegado en `connect.kasper-cloud.com`. Suite de pruebas unitarias 100% pasando (76/76 tests en `services/account`). Cliente Android `ListeningPartyClient.kt` reconfigurado al dominio propio. Verificación en vivo completada exitosamente.
- **Purga de Last.fm:** UI escritorio (`IntegrationsSection.ui.qml`) y móvil (`SettingsConnections.kt`, `SettingsIndex.kt`). El endpoint backend quedó neutralizado previamente en HU-ORCH-06.
- **Purga de Chromecast (móvil):** `FrostedDevicesScreen.kt`, `SettingsPages.kt`, `SettingsIndex.kt`. Decisión: evitar pagos a Google y priorizar app nativa de Android TV en el futuro.
- **Chromecast CastV2 propio (HU-ORCH-12):** decisión C1 del propietario (2026-10-17, Opción B) sobre la purga de HU-ORCH-08. Contrato `SDD-chromecast-castv2.md`: sin Play Services ni App ID registrado, receptor `CC1AD845`, solo LAN y a pedido del usuario. Implementado en `integrate/hu-orch-12`; arquitectura en §8.
- **Atribución (HU-ORCH-07):** `README.md` con crédito total a SFG545 y colaboradores, propósito de uso personal y privacidad, y descargo de responsabilidad.

## 8. Chromecast CastV2 propio (HU-ORCH-12)
Rama `integrate/hu-orch-12` (`70175ed..d228910`). Informe QA y cierre de C1 en `AUDIT.md` §5.10. Paquete `dev.sfg.orchard.mobile.cast`; las clases de protocolo no importan `android.*` y se prueban en JVM.

- **Protocolo (JVM puro):**
  - `CastMessage.kt`: codec proto2 a mano y framing de 4 bytes big-endian, máximo 65536 bytes, varints de 5 bytes, grupos desconocidos hasta 32 niveles.
  - `CastTrust.kt`: `parsePrivateIpv4` (RFC 1918 y link-local en forma canónica; sin CGNAT, loopback ni públicas) y `PinningTrustManager` sin CA del sistema, con pin SHA-256 por `id` del TXT.
  - `CastChannel.kt`: `SSLSocket` desde los bytes de IP validados (sin DNS), conexión y handshake con 5 s, hilo lector, escritura serializada, `requestId` creciente con timeout de 10 s; `requestId: 0` va a los listeners.
  - `CastSession.kt` + `CastJson.kt`: canales connection/heartbeat/receiver, `LAUNCH` de `CC1AD845` solo si no corre, `PING` cada 5 s, 15 s sin tráfico = un reintento, `SET_VOLUME` del dispositivo. Parser JSON propio con 32 niveles.
  - `CastMediaChannel.kt`: `LOAD`/`PLAY`/`PAUSE`/`SEEK`/`STOP`/`GET_STATUS` con `mediaSessionId`, `MEDIA_STATUS` y `onItemFinished` una vez por `mediaSessionId`.
- **Android:**
  - `CastDiscovery.kt`: `NsdManager` `_googlecast._tcp.` y `MulticastLock` perezosos, resoluciones en serie, timeout de 30 s; `CastTxtParser` extrae `id`, `fn`, `md`, `ca` y descarta IPs no privadas.
  - `CastV2Player.kt`: `SimpleBasePlayer`; la cola vive en el teléfono y el receptor solo carga el ítem actual. Volumen del receptor como `DeviceInfo` remoto 0..20.
  - `SharedPreferencesCastTrustStore.kt`: pins en `orchard_cast_tofu_pins`, `commit()` síncrono, sin backup.
  - `playback/ChromecastPlayback.kt`: handoff teléfono ↔ receptor y `teardown` de player, media, sesión, canal y servidor.
  - `playback/ChromecastStreamServer.kt`: una instancia por sesión, token de 128 bits, solo atiende a `receiverIp`, 404 a todo lo demás.
  - `playback/CastControls.kt` (`OrchardGraph.cast`): `StateFlow<CastStatus>` y comandos para la UI; el servicio se registra como `CastHost`.
  - UI: `ChromecastSettingsRow.kt` en Ajustes → Audio → Devices y en la hoja Connect (`FrostedDevicesScreen.kt`), operable con D-Pad.
- **Flujo de sesión:** Buscar → `CastDiscovery` (máx. 30 s) → elegir dispositivo → `ChromecastPlayback.connectToDevice` → `ChromecastStreamServer(receiverIp)` → `CastChannel.open` (IP privada, pin TOFU) → `CONNECT receiver-0` → `GET_STATUS` → `LAUNCH CC1AD845` si hace falta → `CONNECT transportId` → `LOAD http://<ipv4-lan>:<puerto>/cast/<token>/<mediaId>` → `MEDIA_STATUS` alimenta `CastV2Player`; `FINISHED` → `LOAD` del siguiente. Desconectar o `DISCONNECTED` devuelven la reproducción al teléfono en la posición conocida y cierran todo.
- **Retirado:** `media3-cast`, `OrchardCastOptionsProvider.kt`, `ChromecastMediaItemConverter.kt`, meta-data `OPTIONS_PROVIDER_CLASS_NAME`, `MediaTransferReceiver`, `cast_app_id` y `mediaRouteTheme` de `styles.xml`. La meta-data `com.google.android.gms.car.application` de Android Auto se queda: es una clave del manifest, no una dependencia.
- **Pendiente:** tests JVM, `:app:dependencies` y `assembleRelease` en Actions; QA manual en hardware (descubrimiento, grupos multizona, corte de Wi-Fi ≤ 20 s); titular del copyright de los archivos nuevos. Fase 2 (escritorio) solo con orden del PO.

## 9. Android TV "Glass Rail" (HU-ORCH-11)
Rama `integrate/hu-orch-11`, contrato `SDD-android-tv.md`. Informe QA en `AUDIT.md` §5.11.

- **Bifurcación:** `OrchardApp` llama a `TvOrchardApp(viewModel)` y retorna si `isTvLayout()` (modo UI televisión o `FEATURE_LEANBACK`, evaluado una vez). Mismo `OrchardViewModel`; sin flavors.
- **Árbol TV (`ui/tv/`):** `TvOrchardApp` monta fondo ambiental (paleta + Kawarp a media resolución, 30 fps, apagado en low-RAM), NavHost (`TvRoutes`: home, search, library, settings, now_playing, `detail/{id}`) y rail superpuesto. El rail se expande al recibir foco y se contrae cuando el foco vuelve al contenido; Atrás con el rail abierto lo cierra. Ajustes es un placeholder hasta T11.
- **Foco:** `Modifier.tvFocus` solo dibuja (escala en `graphicsLayer`, glow, borde, `zIndex`) y debe ir antes de un único `clickable`/`focusable` y antes de `clip`/`background`. `tvClickable` y `tvFocusable` lo combinan.
- **Tiempo de reproducción:** `playbackState` llega sin posición; la barra de progreso y las letras leen `LocalPlayerClock`, que `TvOrchardApp` provee con `rememberPlayerPresentation`.
- **Build:** `-PorchardTvBuild=true` cambia solo las ABI de release a `armeabi-v7a` + `arm64-v8a` (y agrega `armv7` a Earmark). `release.yml` compila el teléfono, copia, compila TV y publica `Orchard-AndroidTV-arm.apk`. En 32 bits no hay LiteRT (traducción desactivada) ni ORT reducido.
- **Actualizador:** TV acepta solo `Orchard-AndroidTV-arm.apk` (también en un manifest); teléfono prefiere `Orchard-Android-arm64-v8a.apk` y nunca toma el de TV. Sin `sha256` cuando el APK sale de los assets (H-TV1, pendiente del propietario).
- **Pendiente:** T0 (ABI real del Chromecast con Google TV, `kopus`/Earmark en `armv7`), T10, T11, T12, T13, Q2, Q4, H-TV1 y build/tests en Actions.

## 10. Vinculación TV ↔ Teléfono por QR y LAN (HU-ORCH-13)
Rama `integrate/hu-orch-13-tv-qr-login`, contrato `SDD-tv-qr-pairing.md`.
- **Problema de Negocio:** En Android TV, el inicio de sesión web en WebView es inutilizable con control remoto (D-pad) cuando Google pide verificación en dos pasos (2FA, passkeys, selección de método alternativo).
- **Solución:** Vinculación remota local TV ↔ Teléfono. La TV muestra código QR y código de comparación SAS (`ddd-ddd`); el teléfono transfiere la sesión activa de YouTube Music (y opcional Spotify `sp_dc`) de forma cifrada de extremo a extremo por la red local.
- **Protocolo `orchard-pair-v1` (`dev.sfg.orchard.mobile.pairing`):**
  - Criptografía: Par efímero ECDH P-256 (`secp256r1`), HKDF-SHA256, sobre AES-256-GCM con AAD sobre transcripción `th`.
  - Invariantes de Seguridad: PAIR-INV-1 (cero fuga externa, solo IPs privadas RFC 1918), PAIR-INV-2 (consentimiento explícito en ambos lados con foco inicial en Cancelar), PAIR-INV-3 (cero SDKs propietarios / Firebase, QR con `zxing:core:3.5.3`), PAIR-INV-4 (servidor local efímero con timeout de 300 s, máx 5 fallos, puerto cerrado tras completar), PAIR-INV-5 (canal autenticado por huella SHA-256 en QR o SAS), PAIR-INV-6 (entradas acotadas, máx 64 KiB), PAIR-INV-7 (cero secretos en logs o `toString()`), PAIR-INV-8 (archivos < 500 líneas, AGPL).
  - TV: `TvPairingServer` en puerto efímero, anuncio mDNS `_orchard-pair._tcp` (`PairingAdvertiser`), pantalla `TvQrLoginScreen` (Canvas QR, timer, SAS, fallback web a `TvRoutes.LOGIN_WEB`) y diálogo `TvPairingConfirmDialog`.
  - Teléfono: `PairingDiscovery`, `PairingClient`, pantalla `PairWithTvScreen`, diálogo `PairConfirmDialog`, fila en `SettingsConnections.kt` y enrutamiento deep link `orchard://pair` en `MainActivity.kt`.
- **Verificación QA:** 43/43 tests unitarios JVM pasando en verde (`PairingCryptoTest` 10/10, `PairingMessagesTest` 9/9, `PairingUriTest` 8/8, `TvPairingServerTest` 11/11, `PairingClientTest` 5/5).

## 11. Rediseño TV Etapa 2, Modo Nocturno, Salida con Control Remoto y Firma Permanente (HU-ORCH-14 & HU-ORCH-15)
Ramas `integrate/etapa2-tv-and-updates` → `audited`.
- **Problema de Negocio:**
  1. La versión inicial en Android TV tenía mala legibilidad y ergonomía visual en TVs grandes (Phillips 65"): carátula oscura, menús desproporcionados cortados en los bordes, rail demasiado ancho y dificultad para salir de la app al retroceder.
  2. Fallo de actualización: Las actualizaciones de APK en Android arrojaban "las versiones no son compatibles", forzando desinstalar y perder la sesión autenticada debido a que GitHub Actions firmaba con certificados desechables distintos en cada ejecución.
- **Soluciones Implementadas:**
  - **HU-ORCH-14 (Rediseño TV Etapa 2):**
    - Safe area de 48 dp horizontal y 27 dp vertical, `ContentStartX` a 116 dp.
    - Rail lateral en cápsula de vidrio flotante que pasa de overlay a **push animado** (180 dp expandido, empuja contenido 136 dp y lo oscurece al 45%).
    - Fondos ambientales dinámicos por rol (`TvAmbientBackdrop`) con Kawarp equilibrado/vibrante, resplandor suave y noche absoluta.
    - Modo Nocturno en reproductor: Botón 🌙 en transporte (`TvPlayerControls`), mini-reproductor de 170 dp sobre negro `#000000`, shift anti-burn-in OLED cada 60 s, temporizador de 8 s al interactuar con D-pad y soporte de horario programado (`TvUiPrefs`).
    - Navegación D-pad: Salida limpia de la app al retroceder desde Home con rail colapsado (`Activity.finish()`), descarte prioritario de `actionTrack` y cierre de rail.
    - Ajustes nativos de TV en dos columnas (`TvSettingsScreen`, `TvSettingsPages`, `TvHomeLayoutScreen`) con `TvUiPrefs` fuera de `OrchardSettings`.
    - Preservación íntegra de `TvLoginRoutes.kt` y `pairing/*` (HU-ORCH-13).
  - **HU-ORCH-15 (Firma Permanente y Manifiestos de Actualización):**
    - Keystore permanente RSA 4096 (`orchard-release.jks`) configurado en los 4 secretos de GitHub Actions (`ANDROID_KEYSTORE_*`).
    - Generación automática en CI (`release.yml`) de los manifiestos `latest-android.json` y `latest-android-tv.json` con SHA-256 real de los APKs compilados.
    - `UpdateManager.kt` consulta `latest-android-tv.json` en TV (fallback al de teléfono) y valida con `manifestFitsDevice`.
    - 16/16 tests JVM pasando en verde en `UpdateManagerTest.kt`.

## 12. Video musical continuo, multicuentas y podcasts (HU-ORCH-16, 17 y 18)
Rama `integrate/v1.0.0-canary.9-audited.26`. Informe QA en `AUDIT.md` §5.15.

- **HU-ORCH-16 (video musical en TV):**
  - Causa de la pantalla negra: `TvRoutes.MUSIC_VIDEO` se abría sin activar el modo video, y el botón de salida llamaba a `onToggleMusicVideo(); onExit()` dejando el estado cruzado.
  - `TvOrchardApp` llama a `viewModel.toggleMusicVideo()` solo si `!musicVideo.playing` antes de navegar; `exitVideo` lo desactiva al salir por Atrás, Cerrar o "Volver a la canción".
  - El playhead es continuo porque `switchVideoSource` cambia la fuente del mismo reproductor; no hay `seek` ni reinicio.
- **HU-ORCH-17 (multicuentas):**
  - `SecureYouTubeSessionStore`: lista JSON en `youtube_accounts_v1` y cuenta activa en `youtube_active_account_id`, cifradas con `AndroidKeystoreCipher` (alias `orchard_youtube_oauth_v1`) y escritas con `commit()`. `ensureMigrated()` convierte la sesión única `youtube_credentials` en la primera cuenta y la borra.
  - Id de cuenta: `SHA-256(loginCookie:dataSyncId:accountIndex)` truncado a 16 bytes en hex. La cookie nunca sale del almacén como identificador.
  - `YouTubeAccount.isActive` se calcula al leer; no se guarda en disco.
  - `NativeYouTubeAuthRepository` expone `accounts: StateFlow`, `switchAccount` y `removeAccount`; si se elimina la activa pasa a la siguiente o cierra sesión.
  - `app/AccountSwitcher.kt` (fuera de `OrchardViewModel` por el límite de 500 líneas): si cambia la cuenta activa, `library.clearAccountCollections()` vacía likes, álbumes, artistas, playlists y podcasts sincronizados (el historial local queda), recarga la biblioteca y refresca Home.
  - UI TV: "Cuentas guardadas" con `keyId` único por cuenta; "Cerrar sesión en cuenta activa" elimina solo esa cuenta y "Cambiar de canal" (`ACCOUNT_SWITCH`) se mantiene para cuentas de marca. Agregar cuenta reutiliza el login QR de HU-ORCH-13.
- **HU-ORCH-18 (podcasts):**
  - `browseItemNormalizers.js`: `MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE` o `browseId` `MPSP*` → tipo `podcast`; `musicMultiRowListItemRenderer` → `podcastEpisode` (exportado desde `podcastCatalog.js`).
  - `searchUtils.js`: sección `podcasts` al final del orden de secciones.
  - `runtime/catalog.js`: filtro `podcast: 'EgWKAQJQAQ%3D%3D'` (campo protobuf 10; `EgWKAQIQ…` es el de videos), categoría de biblioteca `Podcasts` y `loadBrowse` de `MPSP*` con `normalizePodcastDetail`.
  - Android: sección `library-podcasts` separada de las playlists en `LibraryRepository`, `savedPodcasts` en `LibraryCache`, `LibraryFilter.PODCASTS` en móvil y TV; en `ProviderModels.kt` los episodios tienen prioridad sobre el prefijo `MPSP`.
- **Verificación:** 187/187 tests en `providers/youtube` (`npm test`). Kotlin sin compilar en local (sin Android SDK); queda para CI.
- **Pendiente:** compilación y `testDebugUnitTest` en CI; probar el filtro de podcasts contra YouTube real; sesión cifrada huérfana si se inicia sesión con la sesión única antigua aún presente (se limpia en el próximo `clearAll`).
