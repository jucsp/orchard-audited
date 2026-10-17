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
- `mobile/.../MainActivity.kt`, `OrchardPlaybackService.kt`: sin `CastContext` ni `ChromecastPlayback` (H15).
- UI de escritorio/móvil: Last.fm purgado de `IntegrationsSection.ui.qml`, `SettingsConnections.kt` y `SettingsIndex.kt`; Chromecast purgado de `FrostedDevicesScreen.kt`, `SettingsPages.kt` y `SettingsIndex.kt` (HU-ORCH-08).

## 4. Estado de auditoría
- **Base integrada:** tag `v1.0.0-canary.9` (`aa900002d054`). Informe en `AUDIT.md` §1.
- **`upstream/main` `3c8acc1e1e39`** (30 commits sobre el tag): revisión estática sin malware, no promovida por ser rama y porque el updater sigue en `depot.sfg545.dev` (`AUDIT.md` §4). `bootstrapper/` sin cambios en ese rango.
- **Licencia del motor adaptativo:** OOL-1.0 en el tag (bloqueo L1); `upstream/main` lo relicencia a PolyForm Shield 1.0.0, lo que desbloquea L1 al promover un tag que lo incluya, conservando el `Required Notice`.
- **HU-ORCH-02 aplicada:** clave `orchard-fork-1`, servidor del fork y fin del auto-update en segundo plano. `grep` sin `depot.sfg545.dev`/`downloads.sfg545.dev` en `bootstrapper/` y `mobile/`.
- **HU-ORCH-06 aplicada y desplegada:** cuenta/Connect → `connect.kasper-cloud.com`; auto-update off por defecto y sin sondeo; `artwork.sfg545.dev` y `lastfm.sfg545.dev` neutralizados en escritorio y Android; `providers/apple-music/` no existe en este tag. Worker `orchard-account` desplegado en `connect.kasper-cloud.com` con D1 `orchard-accounts`, secretos cargados y Durable Objects `ConnectHub` y `PartyRoom`.
- **Contra-auditoría de red (§5):** H12–H15 resueltos en el árbol de trabajo (§5.6). Residual: dependencia `media3-cast` y `MediaTransferReceiver` en el APK sin inicializar.
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
- **APK de Android (HU-ORCH-10):** job `android` (`ubuntu-24.04`) con Node 24, JDK 17, Rust `aarch64-linux-android` y `x86_64-linux-android` y cargo-ndk. Instala los dos targets porque la tarea `cargo ndk` de `build.gradle` compila `orchardAbis` (arm64-v8a y x86_64) también en `assembleRelease`, aunque el APK solo empaquete arm64-v8a. Instala el NDK de `orchardNdkVersion` y fija `ANDROID_NDK_HOME` porque el del runner es otro. Publica `Orchard-Android-arm64-v8a.apk`, que `publish` exige. Firma con los secretos `ANDROID_KEYSTORE_*`; sin ellos usa una keystore desechable, y como cambia la firma en cada release el APK no se instala sobre el anterior. No publica `latest-android.json`, así que el `UpdateManager` de Android no ve versiones nuevas.

## 7. Soberanía de servicios (HU-ORCH-06) y purga de frontend (HU-ORCH-07/08)
- **Worker propio (`orchard-account`):** desplegado en `connect.kasper-cloud.com` con base D1 `orchard-accounts`, secrets cargados (`GOOGLE_CLIENT_SECRET`, `SIGNING_KEY`, etc.) y Durable Objects `ConnectHub` y `PartyRoom`.
- **Listening Party (salas compartidas):** implementado en `services/account/src/party.js` (Durable Object `PartyRoom` con relay WebSocket, transferencia de host, persistencia y código de sala sin caracteres ambiguos). Desplegado en `connect.kasper-cloud.com`. Suite de pruebas unitarias 100% pasando (76/76 tests en `services/account`). Cliente Android `ListeningPartyClient.kt` reconfigurado al dominio propio. Verificación en vivo completada exitosamente.
- **Purga de Last.fm:** UI escritorio (`IntegrationsSection.ui.qml`) y móvil (`SettingsConnections.kt`, `SettingsIndex.kt`). El endpoint backend quedó neutralizado previamente en HU-ORCH-06.
- **Purga de Chromecast (móvil):** `FrostedDevicesScreen.kt`, `SettingsPages.kt`, `SettingsIndex.kt`. Decisión: evitar pagos a Google y priorizar app nativa de Android TV en el futuro.
- **Atribución (HU-ORCH-07):** `README.md` con crédito total a SFG545 y colaboradores, propósito de uso personal y privacidad, y descargo de responsabilidad.
