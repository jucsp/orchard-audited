# UPSTREAM.md — Diferencias con sfg5453/orchard

Registro de **todos** los cambios del fork respecto del upstream (INV-7). Cada cambio vive en un commit cuyo mensaje empieza por `audit-fork:`, para que al promover una versión nueva (SDD §7) se puedan reaplicar sobre el tag nuevo con un rebase limpio.

- **Upstream:** https://github.com/sfg5453/orchard (remoto `upstream`)
- **Base actual:** tag `v1.0.0-canary.9` → `aa900002d0541ce6bd7beab8d6011a05d181daaa`
- **Rama del fork:** `audited`
- **Licencia:** AGPL-3.0-or-later con el permiso adicional de `LICENSE-EXCEPTION.md`. Los archivos modificados conservan su licencia y avisos de copyright originales (SFG545 y Julian-FF2000); los archivos nuevos del fork se publican bajo AGPL-3.0-or-later. El fork **no modifica** archivos con cabecera OOL-1.0 (ver L1 en `AUDIT.md`).

## Política de integración

Las reglas de integración del upstream están en `AGENTS.md` (auditoría previa en `AUDIT.md`, preservación de los cambios del fork, integración quirúrgica y protocolo de conflictos). Este archivo guarda los datos que esas reglas consultan.

## Convención de commits

```
audit-fork: <área>: <descripción breve> (<REQ>, <HU>)
```

Áreas: `docs`, `audit`, `updater`, `trust`, `ci`, `build`. Un commit = un cambio lógico; sin mezclar código del upstream con cambios del fork. Responsable en el cuerpo como trailer `Responsable: <agente>`.

## Zonas protegidas

Rutas donde el fork tiene o tendrá parches. Ningún cambio del upstream las sobrescribe sin pasar por el protocolo de conflictos de `AGENTS.md`. Las marcadas como _prevista_ se protegen desde que su HU se aplica.

| Ruta | Qué protege | INV / REQ | Estado |
| --- | --- | --- | --- |
| `bootstrapper/src/security/TrustedKeys.cpp` | Solo la clave `orchard-fork-1` es de confianza | INV-3, REQ-U1 | activa |
| `bootstrapper/src/update/` | Origen en el fork, verificación firma/SHA-256, anti-rollback, sin descarga sin confirmación | INV-1–4, REQ-U2–U8 | activa (parcial: U2 aplicado) |
| `bootstrapper/src/app/Bootstrapper.cpp` | Sin lanzamiento automático de `--background-update` | INV-1, REQ-U5 | activa |
| `app/src/update/`, QML de ajustes de actualización | Diálogos Sí/No e Instalar/Más tarde, canal único | INV-1–2, REQ-U4, U7 | _prevista_ HU-ORCH-03 |
| `mobile/android/app/src/main/java/dev/sfg/orchard/mobile/UpdateManager.kt` | Updater Android hacia el fork | INV-3 | activa |
| `app/src/account/orchard_account.cpp`, `mobile/.../auth/OrchardAccountService.kt` | Cuenta/Connect hacia `connect.kasper-cloud.com` | INV-3 | activa (HU-ORCH-06) |
| `app/src/update/bootstrapper_client.{h,cpp}` | Auto-update off por defecto y sin sondeo periódico | INV-1, REQ-U5 | activa (HU-ORCH-06) |
| `app/src/integrations/discord_artwork.cpp` | No confiar en `artwork.sfg545.dev` | INV-3 | activa (HU-ORCH-06) |
| `app/src/integrations/lastfm.cpp`, `mobile/.../lastfm/LastfmRepository.kt` | Sin llamadas a `lastfm.sfg545.dev` | INV-3 | activa (HU-ORCH-06) |
| `services/account/wrangler.production.jsonc` | Worker propio `orchard-account` en `connect.kasper-cloud.com` | INV-3 | activa (HU-ORCH-06) |
| Telemetría o red en segundo plano desactivada por el fork (H7) | Valores por defecto del fork | INV-6 | _prevista_ |
| `core/native/translation/translation_pack_table.cpp` | Tabla de packs vacía: sin descargas de modelos (autor ni Helsinki-NLP) | INV-3 | activa (H12, HU-ORCH-09) |
| Ausencia de `scripts/translation/`; `THIRD_PARTY_NOTICES.md` sin sección de modelos de traducción | Sin herramienta ni atribución de packs de HuggingFace/Helsinki-NLP | INV-3 | activa (HU-ORCH-09) |
| `app/src/lyrics/translation/lyrics_translator.{h,cpp}`, `app/qml/components/settings/LyricTranslationSettings*.qml`, `mobile/.../ui/screens/SettingsLyrics.kt`, `mobile/.../lyrics/translation/LyricTranslator.kt` | Sin proveedor `local` ni UI de modelos offline; traducción solo por API elegida | INV-6 | activa (HU-ORCH-09) |
| `app/src/offline/connectivity_monitor.cpp` | Sonda de conectividad solo hacia el Worker propio | INV-6 | activa (H13) |
| `app/src/integrations/discord.cpp`, `discord_sdk_bridge.cpp`, `mobile/.../discord/DiscordArtwork.kt`, `DiscordPresenceCoordinator.kt`, `buildConfigField DISCORD_APPLICATION_ID` en `mobile/android/app/build.gradle` | Sin Discord App ID del autor | INV-3 | activa (H14) |
| `mobile/.../connect/app/MainActivity.kt`, `mobile/.../playback/OrchardPlaybackService.kt` | Cast sin inicializar | INV-6 | activa (H15) |
| `.github/workflows/release.yml` y ausencia de `manual-desktop-build.yml` | Release solo hacia GitHub Releases del fork, acciones por SHA, publicación directa en `jucsp/orchard-audited`; sin job que publique en R2 | REQ-A2–A6, A10 | activa |
| `.github/workflows/` (monitor) | Monitor solo alerta | REQ-M1–M4 | _prevista_ HU-ORCH-04 |
| `scripts/package-linux-rpm.sh`, `packaging/rpm/` | Empaquetado `.rpm` del fork | REQ-A2, A10 | activa |
| `scripts/release/` | Firma de manifiestos con `orchard-fork-1` | INV-5, REQ-A3 | _prevista_ |
| `tests/` (tests negativos del updater) | Prueba que falla ante descarga sin confirmación | REQ-U5, U6 | _prevista_ HU-ORCH-03 |
| `README.md` (bloque inicial REQ-R3) | Aviso de fork de auditoría y verificación de descargas | REQ-R3 | activa |
| `AUDIT.md`, `UPSTREAM.md` | Trazabilidad de auditorías y cambios del fork | REQ-R4, R5 | activa |
| `AGENTS.md`, `CLAUDE.md`, `GEMINI.md` | Gobernanza del Swarm. `AGENTS.md` es archivo del upstream: conservar su línea `@contributing.md` y reaplicar el bloque del fork si el upstream lo cambia | INV-7 | activa |
| `SDD-orchard-audit-fork.md`, `backlog.md`, `sprint_board.md`, `technical_memory.md` | Contrato y planificación del fork | — | activa |

## Rebase sobre un tag nuevo

Solo tras aprobar la auditoría de `<tag-nuevo>` en `AUDIT.md` y con todos sus commits clasificados `aceptado`. Se hace en una rama temporal; `<tag-base>` es la base actual indicada arriba.

```sh
git fetch upstream --tags
git log --oneline --grep '^audit-fork:' <tag-base>..audited
git switch -c integrate/<tag-nuevo> audited
git rebase --onto <tag-nuevo> <tag-base> integrate/<tag-nuevo>
```

Si el rango tiene commits `rechazado`, no se rebasa: se aplican los aceptados con `git cherry-pick -x` sobre `integrate/<tag-nuevo>` y se listan en **Commits importados del upstream**. Llevar la rama a `audited` reescribe historial publicado y requiere autorización explícita del propietario. Después, actualizar la base de este archivo y la tabla de `AUDIT.md`.

## Commits aplicados

| # | Commit | Área | Descripción | REQ | HU | Archivos | Convención |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `1301e8f` | docs | Contrato SDD, backlog, tablero y memoria técnica | — | — | `SDD-orchard-audit-fork.md`, `backlog.md`, `sprint_board.md`, `technical_memory.md` | ❌ sin prefijo |
| 2 | `a019c98` | docs | Aviso de fork en README, informe base `AUDIT.md`, este `UPSTREAM.md` | REQ-R3, R4, R5 | HU-ORCH-01 | `README.md`, `AUDIT.md`, `UPSTREAM.md` | ⚠️ sin `(<REQ>, <HU>)` |
| 3 | `f7923ae` | docs | Tablero: HU-ORCH-01 completada | — | HU-ORCH-01 | `sprint_board.md` | ❌ sin prefijo |
| 4 | `3ead5f1` | docs | Backlog: disparo del release del fork | REQ-A4 | HU-ORCH-05 | `backlog.md` | ❌ sin prefijo |
| 5 | `c9fb6ad` | audit | Auditoría estática de `upstream/main` (§4 de `AUDIT.md`) | REQ-R4 | — | `AUDIT.md` | ⚠️ sin área ni `(<REQ>, <HU>)`; responsable en el asunto |
| 6 | `cb6ac5f` | docs | Gobernanza de actualizaciones del upstream | INV-7, REQ-R5 | — | `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `UPSTREAM.md`, `technical_memory.md` | — |
| 7 | `115120a` | trust | Sustituir la clave de confianza por `orchard-fork-1` | REQ-U1 | HU-ORCH-02 | `bootstrapper/src/security/TrustedKeys.cpp` | — |
| 8 | `a4deeac` | updater | Servidor del fork, sin auto-update en segundo plano y Android hacia el fork | REQ-U2, U5 | HU-ORCH-02 | `bootstrapper/src/update/ReleaseClient.hpp`, `bootstrapper/src/app/Bootstrapper.cpp`, `mobile/.../UpdateManager.kt`, `mobile/.../UpdateManagerTest.kt` | — |
| 9 | _pendiente_ | ci | Pipeline de release hacia GitHub Releases del fork (creado, sin commit); se elimina `manual-desktop-build.yml` (publicación a R2) | REQ-A2–A6 | HU-ORCH-05 | `.github/workflows/release.yml`, `.github/workflows/manual-desktop-build.yml` (borrado) | — |
| 10 | _pendiente_ | build | Empaquetado `.rpm` desde el AppDir (creado, sin commit) | REQ-A2, A10 | HU-ORCH-05 | `scripts/package-linux-rpm.sh`, `packaging/rpm/orchard.spec` | — |
| 11 | _pendiente_ | account | Worker propio y cuenta/Connect hacia `connect.kasper-cloud.com` | INV-3 | HU-ORCH-06 | `services/account/wrangler.production.jsonc`, `app/src/account/orchard_account.cpp`, `mobile/.../OrchardAccountService.kt` | — |
| 12 | _pendiente_ | updater | Auto-update off por defecto y sin sondeo periódico | REQ-U5 | HU-ORCH-06 | `app/src/update/bootstrapper_client.{h,cpp}`, `app/qml/.../GeneralSection.qml`, `app/qml/.../WelcomeSystemPage.qml` | — |
| 13 | _pendiente_ | integrations | Neutralizar `artwork.sfg545.dev` y `lastfm.sfg545.dev` | INV-3 | HU-ORCH-06 | `app/src/integrations/discord_artwork.cpp`, `app/src/integrations/lastfm.cpp`, `mobile/.../LastfmRepository.kt`, `tests/discord_artwork_test.cpp` | — |

Los commits marcados ❌/⚠️ no cumplen la convención. Corregirlos implica reescribir historial publicado (`git rebase` con reword y push forzado), así que queda a decisión del propietario; hasta entonces, `git log --grep '^audit-fork:'` no los lista y deben revisarse a mano en cada rebase.

**Backup de rol (HU-ORCH-05):** Claude (PM/QA) alcanzó su rate limit (resets 6:20pm). DeepSeek Dev asumió las funciones de PM/QA para implementar y verificar el pipeline de release y el empaquetado `.rpm`; los commits 9 y 10 quedan pendientes de autorización del propietario.

## Commits importados del upstream

Commits del upstream integrados por `cherry-pick -x` fuera de un tag completo. Vacío mientras la base sea un tag sin selección.

| Commit upstream | Commit en `audited` | Tag de origen | Clasificación en `AUDIT.md` |
| --- | --- | --- | --- |

## Cambios previstos

| Área | Cambio | REQ | HU | Archivos previstos |
| --- | --- | --- | --- | --- |
| updater | Comprobación solo de metadatos al iniciar (el lanzamiento automático ya fue eliminado) | REQ-U3 | HU-ORCH-03 | `bootstrapper/src/update/UpdateManager.cpp` |
| updater | Diálogo Sí/No y luego Instalar/Más tarde, con versión, notas, host de origen y enlace a `AUDIT.md`; silencio de 7 días | REQ-U4, U8 | HU-ORCH-03 | `app/src/update/bootstrapper_client.cpp`, QML de ajustes de actualización |
| updater | Canal único auditado; desactivar el cambio a canary y el rollback por cambio de canal | REQ-U7 | HU-ORCH-03 | `app/src/update/*`, `bootstrapper/src/update/*` |
| updater | Verificación de firma, hash de manifiesto y SHA-256 por archivo; anti-rollback por `sequence` | REQ-U6 | HU-ORCH-03 | `bootstrapper/src/update/*`, `bootstrapper/src/security/*` |
| updater | Tests negativos: descarga sin confirmación, firma inválida, hash alterado, versión ≤ instalada | REQ-U5, U6 | HU-ORCH-03 | `tests/` |
| docs | Ajustar la guía de actualizaciones al flujo con confirmación | REQ-U4 | HU-ORCH-03 | `docs/app/updates.md`, `docs/BOOTSTRAPPER.md` |
| ci | Monitor del upstream que solo abre issues; alerta alta si cambian `TrustedKeys.cpp` o `bootstrapper/src/update/` | REQ-M1–M4 | HU-ORCH-04 | `.github/workflows/monitor-upstream.yml` |
| ci | Manifiestos y canal firmados con `orchard-fork-1` en una estructura que el bootstrapper pueda leer desde GitHub Releases | REQ-A3, A9 | HU-ORCH-03/05 | `scripts/release/`, `.github/workflows/release.yml` |
| build | Firma GPG del `.rpm` y publicación de la clave pública | REQ-A10 | HU-ORCH-05 | `packaging/rpm/` |
