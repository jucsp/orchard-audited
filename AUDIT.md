# AUDIT.md — Historial de auditorías de Orchard (fork `orchard-audited`)

Registro de cada versión del upstream [sfg5453/orchard](https://github.com/sfg5453/orchard) revisada antes de incorporarse a este fork. El monitor del upstream (REQ-M1) compara contra la **última versión registrada en la tabla siguiente**.

| Versión upstream | Commit | Fecha revisión | Resultado | Release del fork |
| --- | --- | --- | --- | --- |
| `1.0.0-canary.9` | `aa900002d0541ce6bd7beab8d6011a05d181daaa` | 2026-10-15 | Línea base del fork, con hallazgos a corregir en el fork (§1.4) y bloqueo de licencia para publicar binarios (L1) | Pendiente (HU-ORCH-05) |

---

## 1. Informe `1.0.0-canary.9` (línea base)

- **Tag base:** `v1.0.0-canary.9` → `aa900002d0541ce6bd7beab8d6011a05d181daaa` (fecha del commit: 2026-10-12 02:32:27 -0500, "Pin a current FFmpeg build for the Windows adaptive mix worker").
- **Rama del fork:** `audited`, creada desde el tag (no desde `main` del upstream). `git diff v1.0.0-canary.9 audited` solo contiene archivos de documentación del fork.
- **Licencias upstream:** AGPL-3.0-or-later (`LICENSE`), permiso adicional bajo la sección 7 (`LICENSE-EXCEPTION.md`) y Orchard-Only License 1.0 para el motor de mezcla adaptativa (`LICENSE-OOL`). Los tres archivos se conservan sin cambios.
- **Titulares de copyright:** SFG545 y Julian-FF2000 (AGPL); SFG545 (OOL-1.0).
- **Auditor:** Claude, a solicitud del propietario (contrato SDD v1.0).

### 1.1 Qué se verificó

| # | Verificación | Método | Resultado |
| --- | --- | --- | --- |
| V1 | Código fuente del tag | Revisión estática del árbol en `v1.0.0-canary.9` (auditoría base) | Aprobado como línea base según el contrato SDD; superficie de actualización re-verificada en §1.3 |
| V2 | Compilaciones publicadas | Reconstrucción desde fuente de los componentes reproducibles y comparación de hashes con los artefactos del upstream (auditoría base) | Aprobado como línea base según el contrato SDD; ver §1.2 para lo no reproducido |
| V3 | Firmas YARA | Escaneo de artefactos publicados (auditoría base) | Aprobado como línea base según el contrato SDD; reglas y salida no adjuntas |
| V4 | Esquema de firma del actualizador | Lectura de `bootstrapper/src/security/Signature.{hpp,cpp}` y `TrustedKeys.cpp` | Sobre `orchard-signed-v1`, ECDSA P-256/SHA-256, una sola clave de confianza `orchard-release-1` embebida |
| V5 | Anti-rollback | `bootstrapper/src/update/UpdateManager.cpp:29` | Rechaza canales con `sequence` menor que la última vista |
| V6 | Árbol del fork vs tag | `git diff --stat v1.0.0-canary.9 audited` (2026-10-15) | Solo `SDD-orchard-audit-fork.md`, `backlog.md`, `sprint_board.md`, `technical_memory.md` y los documentos de HU-ORCH-01 |
| V7 | Hosts del autor en el código | `grep -rn 'sfg545\.dev'` fuera de `vendor/` y `third_party/` (2026-10-15) | Inventario en §1.3 y §1.5 |
| V8 | Workflows de CI/CD | Lectura de `.github/workflows/*.yml` del tag | Inventario en §2 |

> [!IMPORTANT]
> V1–V3 provienen de la auditoría base que dio origen al contrato SDD. Los hashes, reglas YARA y logs de esa sesión **no están adjuntos** en el repositorio; se agregarán en `audit/1.0.0-canary.9/` si se re-ejecutan. V4–V8 se re-verificaron el 2026-10-15 sobre la rama `audited`. A partir de la próxima versión, cada informe debe adjuntar su evidencia (hashes, comandos y salidas).

### 1.2 Qué NO se verificó

| # | Elemento | Motivo | Mitigación en el fork |
| --- | --- | --- | --- |
| N1 | **QtWebEngine / Chromium** | Su compilación no se pudo reproducir en la auditoría base; los binarios empaquetados no se compararon contra un build propio | El fork reutiliza estas dependencias de la misma forma que el upstream (Qt oficial). Riesgo aceptado y documentado |
| N2 | **Partes del build C++** (app Qt y bootstrapper) | No se reprodujeron byte a byte en la auditoría base | El pipeline del fork (HU-ORCH-05) las compilará desde fuente y registrará SHA-256 y toolchain (REQ-A8) |
| N3 | **Clave `orchard-release-1` y `depot.sfg545.dev`** | El auditor no controla la clave privada ni el servidor del autor; no se puede verificar qué se publicará en el futuro bajo esa clave | El fork deja de confiar en esa clave y en ese host (REQ-U1, REQ-U2, HU-ORCH-02) |
| N4 | **Secretos de CI del autor** (`ORCHARD_RELEASE_PRIVATE_KEY`, `R2_*`, keystore Android) | Sin acceso | El fork usa secretos propios en el environment `release` (REQ-A4, REQ-A5) |
| N5 | **Servicios del autor** (`account`, `lastfm`, `party`, `artwork`, `cast`, `songlinks` en `sfg545.dev`; `services/`) | Backend fuera del alcance del fork | Ver §1.5 |
| N6 | **App Android** (`mobile/`) | Fuera de la auditoría base | Decisión abierta (SDD §9) |
| N7 | Modelos (`models/`) y binarios de `vendor/` / `third_party/` | No se re-derivaron desde su origen | Procedencia declarada en `THIRD_PARTY_NOTICES.md`; pendiente de verificación de hashes |

### 1.3 Superficie de actualización remota (código del tag)

Escritorio (bootstrapper `orchard`):

- `bootstrapper/src/update/ReleaseClient.hpp:19` fija `kDefaultServer = "https://depot.sfg545.dev/"`. Se consulta `releases/<canal>.json` y `manifests/<versión>-<plataforma>.json`, ambos en sobre `orchard-signed-v1`.
- `bootstrapper/src/app/Cli.cpp` acepta `--server URL` (solo `https://`; la firma se sigue exigiendo).
- `bootstrapper/src/app/Bootstrapper.cpp:229` lanza un proceso `--background-update` sin intervención del usuario.
- `app/src/update/bootstrapper_client.cpp` consulta el estado del bootstrapper cada 30 s.
- `docs/app/updates.md`: "Orchard downloads updates in the background and installs them the next time it starts". Activar **Canary builds** descarga la última canary al instante; desactivarlo instala la estable aunque sea más antigua.

Android (`mobile/`):

- `UpdateManager.kt` consulta `https://downloads.sfg545.dev/orchard/latest-android.json` y `api.github.com/repos/sfg5453/orchard/releases`. Muestra estado `Available` y deja decidir al usuario; `UPDATER_ENABLED` es `true` en release y `false` en debug/canary.

Conclusión: en escritorio, quien controle `depot.sfg545.dev` y la clave `orchard-release-1` puede hacer que el cliente descargue e instale una versión nueva sin confirmación. Es el motivo principal del fork.

### 1.4 Hallazgos y estado

| ID | Severidad | Hallazgo | Estado | HU |
| --- | --- | --- | --- | --- |
| H1 | Alta | Descarga en segundo plano e instalación al siguiente arranque sin confirmación (§1.3) | Abierto | HU-ORCH-03 |
| H2 | Alta | Confianza exclusiva en la clave del autor y en `depot.sfg545.dev` | Abierto | HU-ORCH-02 |
| H3 | Media | El cambio a canal estable instala versiones más antiguas (rollback forzado por canal) | Abierto | HU-ORCH-03 |
| H4 | Media | Updater Android apunta a `downloads.sfg545.dev` y a releases del upstream | Abierto, sujeto a la decisión de SDD §9 | — |
| H5 | Baja | Acciones de GitHub fijadas por tag móvil (`@v4`, `@stable`) y `pip install` sin hash en `manual-desktop-build.yml` | Abierto | HU-ORCH-05 |

### 1.5 Servicios en red del autor (no son actualizaciones)

Hosts referenciados en el código del tag que no entregan binarios ejecutables: `account.sfg545.dev` (cuenta Orchard / Connect), `lastfm.sfg545.dev`, `artwork.sfg545.dev` (Discord), `party.sfg545.dev`, `cast.sfg545.dev`, `songlinks.sfg545.dev`, y el enlace informativo `sfg545.dev/orchard`. Quedan fuera de INV-3 pero envían datos a infraestructura del autor cuando el usuario usa esas funciones. Una búsqueda preliminar (`telemetry|analytics|sentry` en `app/`, `core/`, `services/`, `bootstrapper/`, `mobile/`) devuelve `app/src/connect/connect_service.cpp`, `SettingsIndex.kt` y `services/lastfm/worker-configuration.d.ts`, aún sin revisar; INV-6 queda pendiente de una revisión dedicada.

### 1.6 Limitaciones conocidas

- **L1 — Licencia OOL-1.0 del motor de mezcla adaptativa.** `LICENSE-OOL` prohíbe expresamente "Fork, modify, or redistribute this Software" y "Recompile, repackage, or redistribute modified versions"; solo permite ver el código y ejecutar binarios oficiales sin modificar. Afecta a Earmark, el núcleo y planificador de transiciones, el worker de mezcla adaptativa, el código de mezcla Android, los controladores C++ de mezcla y los scripts del planificador de crossfade (archivos con cabecera OOL-1.0 y crates con `LICENSE` propio). Consecuencia: **el fork no puede publicar binarios que incluyan esos componentes** sin autorización escrita de SFG545. Antes de HU-ORCH-05 el propietario debe decidir entre: (a) solicitar autorización al autor, (b) compilar y distribuir sin el motor OOL (Adaptive mix y Best Mix deshabilitados, documentado en el release), u (c) no publicar binarios y limitar el fork a auditoría del código. La presencia del código OOL en un repositorio público con fork también debe revisarla el propietario.
- **L2 — Repositorio declarado por el upstream.** El README del tag indica `github.com/SFG5453/orchard-v4` para clonar e issues, mientras el contrato usa `sfg5453/orchard`. El monitor (HU-ORCH-04) debe confirmar cuál es el repositorio canónico.

---

## 2. Inventario de workflows del upstream (REQ-A1)

Observado en `.github/workflows/` del tag. El contrato esperaba `manual-desktop-build.yml` y `ci-cd.yml`; **`ci-cd.yml` no existe** en este tag.

| Workflow | Disparo | Runners | Artefactos | Secretos | Destino |
| --- | --- | --- | --- | --- | --- |
| `manual-desktop-build.yml` | `workflow_dispatch` (entrada `channel`) | `ubuntu-24.04`, `windows-2025-vs2026` | `Orchard-Linux-x86_64.AppImage`, `AppDir`, `orchard-bootstrapper`; `Orchard-Windows-x86_64.7z`, `OrchardSetup.exe`, `orchard-bootstrapper.exe` | `ORCHARD_RELEASE_PRIVATE_KEY`, `R2_ACCOUNT_ID`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | Cloudflare R2 vía `scripts/release/publish_r2.py` (objetos, manifiestos firmados y canal) |
| `android-release.yml` | push de tag `v*-mobile` | `ubuntu-24.04` | APK firmado | `ANDROID_KEYSTORE_*`, `ANDROID_KEY_*`, `GITHUB_TOKEN` | GitHub Releases del upstream |
| `android-canary-build.yml` | push y `workflow_dispatch` | `ubuntu-24.04` | APK canary | `ANDROID_KEYSTORE_*`, `ANDROID_KEY_*` | Artefacto de Actions |
| `android.yml` | push | `ubuntu-latest` | Ninguno (build/test) | — | — |

Diferencias con lo observado al redactar el contrato: no hay build Linux ARM64 ni macOS en los workflows del tag; la plataforma de escritorio publicada es Linux x86_64 y Windows x64. El fork añadirá `.rpm` (REQ-A2, REQ-A10) como formato propio y lo indicará en cada release.

## 3. Decisiones del fork pendientes de registrar

- Firma en CI o fuera de CI (REQ-A5): pendiente, HU-ORCH-05.
- Origen de canal y manifiestos (GitHub Releases o Pages, REQ-U2): pendiente, HU-ORCH-02.
- Resolución de L1: pendiente del propietario.
