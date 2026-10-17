# AUDIT.md — Historial de auditorías de Orchard (fork `orchard-audited`)

Registro de cada versión del upstream [sfg5453/orchard](https://github.com/sfg5453/orchard) revisada antes de incorporarse a este fork. El monitor del upstream (REQ-M1) compara contra la **última versión registrada en la tabla siguiente**.

| Versión upstream | Commit | Fecha revisión | Resultado | Release del fork |
| --- | --- | --- | --- | --- |
| `1.0.0-canary.9` | `aa900002d0541ce6bd7beab8d6011a05d181daaa` | 2026-10-15 | Línea base del fork, con hallazgos a corregir en el fork (§1.4) y bloqueo de licencia para publicar binarios (L1) | Pendiente (HU-ORCH-05) |
| `upstream/main` (rama, no tag) | `3c8acc1e1e397a5934918c84accfa7bbb1313ab2` | 2026-10-16 | Revisión estática del diff vs tag: **sin malware**; no promovida (espera tag y HU-ORCH-02/03). Informe en §4 | — |

> El monitor del upstream (REQ-M1) vigila **tags/releases**. `upstream/main` es una rama móvil y no sustituye a `1.0.0-canary.9` como "última versión registrada" para el monitor; su revisión queda documentada en §4 a efectos de trazabilidad y de cara a la próxima promoción (SDD §7).

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
| N3 | **Clave `orchard-release-1` y `depot.sfg545.dev`** | El auditor no controla la clave privada ni el servidor del autor; no se puede verificar qué se publicará en el futuro bajo esa clave | El fork deja de confiar en esa clave y en ese host (REQ-U1, REQ-U2). Aplicado en HU-ORCH-02 |
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
| H1 | Alta | Descarga en segundo plano e instalación al siguiente arranque sin confirmación (§1.3) | Parcial: disparo automático eliminado en HU-ORCH-02; falta el diálogo de 2 pasos | HU-ORCH-03 |
| H2 | Alta | Confianza exclusiva en la clave del autor y en `depot.sfg545.dev` | Mitigado | HU-ORCH-02 |
| H3 | Media | El cambio a canal estable instala versiones más antiguas (rollback forzado por canal) | Abierto | HU-ORCH-03 |
| H4 | Media | Updater Android apunta a `downloads.sfg545.dev` y a releases del upstream | Mitigado (decisión del propietario aplicada) | HU-ORCH-02 |
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

### 2.1 Pipeline del fork (HU-ORCH-05)

`manual-desktop-build.yml` se elimina del fork: su job `publish` escribía en R2 con `ORCHARD_RELEASE_PRIVATE_KEY`. Lo sustituye `.github/workflows/release.yml`, que replica los jobs de compilación del upstream (mismos pasos, toolchain y nombres de artefacto) y cambia solo el destino. `scripts/release/publish_r2.py` queda en el árbol sin ningún workflow que lo invoque.

| Workflow | Disparo | Runners | Artefactos | Secretos | Destino |
| --- | --- | --- | --- | --- | --- |
| `release.yml` | push a `audited`/`main`, tag `v*`, `workflow_dispatch` | `ubuntu-24.04`, contenedor `fedora:44`, `windows-2025` | `Orchard-Linux-x86_64.AppImage`, `orchard-<versión>-<run>.x86_64.rpm`, `orchard-bootstrapper`, `Orchard-Windows-x86_64.7z`, `OrchardSetup.exe`, `orchard-bootstrapper.exe`, `SHA256SUMS.txt` | Solo `GITHUB_TOKEN` (`contents: write` únicamente en `publish`) | GitHub Releases de `jucsp/orchard-audited` |

- **Formato agregado por el fork:** `.rpm` (`scripts/package-linux-rpm.sh`, `packaging/rpm/orchard.spec`). Mismo AppDir del AppImage en `/opt/orchard`, enlace `/usr/bin/orchard`, `.desktop` e icono 512×512. Sin cambios de código. Los `Requires` son los sonames que el AppDir necesita y no incluye. El job `rpm-check` lo instala con `dnf` en Fedora y falla si a un ejecutable le falta una librería.
- **Publicación directa:** el job `publish` corre cuando `github.repository == 'jucsp/orchard-audited'`, sin variable de activación ni *environment* de aprobación. Cualquier push a `audited`/`main` o tag `v*` publica directamente en GitHub Releases (REQ-A4).
- **Disparo (REQ-A4):** el SDD pide solo `workflow_dispatch` o tag `-audited`; el backlog (`3ead5f1`) lo amplía a push en la rama principal. El workflow sigue el backlog. Un push publica un *prerelease* `v<versión>-audited.<run>` y un tag publica release `--latest` si coincide con la versión de `meson.build`. Falta corregir el SDD para reflejar el disparo por push.
- **Firma (REQ-A5):** opción "publicar sin firmar". Ni el `.rpm` (REQ-A10, GPG) ni los manifiestos `orchard-signed-v1` se firman en CI. Las notas del release lo dicen.
- **Consistencia con el actualizador (REQ-A9): no cumple todavía.** El bootstrapper pide `<servidor>releases/<canal>.json` y `manifests/<v>-<plataforma>.json` bajo `releases/latest/download/`. Los assets de GitHub Releases no admiten subdirectorios, así que esas rutas devuelven 404 hasta que HU-ORCH-03 defina la estructura de manifiestos.
- **Seguridad del pipeline (REQ-A6):** acciones fijadas por SHA, `persist-credentials: false` y ninguna expresión `${{ }}` con datos del evento dentro de un `run`. Pendiente heredado del upstream: `pip install` sin lockfile ni hashes (meson, cryptography, aqtinstall por commit), y `scripts/package-linux-appimage.sh` descarga `linuxdeploy` *continuous* sin verificar el hash.
- **Qué no se verificó:** no se ejecutó el workflow en GitHub Actions ni se compiló Orchard. El `.rpm` se probó en local (Fedora 44) con un AppDir sintético: build, `rpm -qpR`, `rpm -qpl` y `rpm -i --test`.

## 3. Decisiones del fork pendientes de registrar

- Firma en CI o fuera de CI (REQ-A5): publicar sin firmar en CI (§2.1). Falta definir quién firma el `.rpm` y los manifiestos fuera de CI.
- Origen de canal y manifiestos (GitHub Releases o Pages, REQ-U2): pendiente, HU-ORCH-02.
- Resolución de L1: **resuelto en `upstream/main`** por la relicencia del motor a PolyForm Shield 1.0.0 (§4.6). El fork debe conservar el `Required Notice` y confirmar con el propietario la interpretación de la cláusula Noncompete antes de publicar binarios (HU-ORCH-05).

---

## 4. Informe de auditoría `upstream/main` (diff `v1.0.0-canary.9` → `main`)

- **Rama auditada:** `upstream/main` → `3c8acc1e1e397a5934918c84accfa7bbb1313ab2` (2026-10-16 03:24 UTC, "Unify music catalogs and improve artist pages and playback recovery").
- **Base comparada:** tag `v1.0.0-canary.9` → `aa900002d0541ce6bd7beab8d6011a05d181daaa`.
- **Método:** revisión estática del diff (`ahead_by = 30` commits), con lectura directa del código nuevo en `services/account/`, `providers/apple-music/`, `scripts/performance/`, workflows de CI y archivos de licencia. **No** se recompiló ni se ejecutó YARA en esta pasada (ver §4.2).
- **Superficie del diff:** catálogo unificado YouTube/Apple Music (desktop y Android), proveedor Apple Music nuevo, dos endpoints nuevos del backend de cuenta (tokens Apple Music y traducción de letras en nube), relicencia del motor de mezcla adaptativa a PolyForm Shield, muestreador de procesos para perfiles y cambios grandes de UI. **`bootstrapper/` no cambió** (ninguno de los 30 commits lo toca).

### 4.1 Verdicto malware / puertas traseras / ofuscación

**Limpio.** No se halló malware, puerta trasera, exfiltración ni código ofuscado de autoría del proyecto en el código nuevo.

- El único código ofuscado que se ejecuta es **de YouTube y pre-existente** (no introducido en este diff): el intérprete de BotGuard y la función de descifrado `nsig` del reproductor se ejecutan con `new Function(...)` en `providers/youtube/src/playback/webPoMinter.js:30` y `providers/youtube/src/runtime/player.js:53`. Es inherente a la integración con YouTube; los argumentos se pasan como valores (sin interpolación de cadenas) y corre dentro del runtime del proveedor. No es malware, pero es una superficie de ejecución de código de terceros (H6).
- Los archivos nuevos revisados (`services/account/src/apple.js`, `translation.js`, `translation_inference.js`, `scripts/performance/sample_processes.py`, `providers/apple-music/src/*`) **no** usan `eval`, `child_process`, `exec`, `spawn` ni llamadas al sistema: solo E/S local (el muestreador lee `/proc`/sysfs y escribe en `build/performance/`; los Workers usan D1/R2/AI) y red hacia las APIs declaradas en §4.3.
- `services/account/wrangler.toml` es una plantilla con valores `replace-with-*`; la configuración real (dominio, buckets, secretos) vive en un JSONC ignorado por git. No hay secretos reales en el repo.
- Los commits siguen **sin firma GPG** (verificación `unsigned`), igual que el tag base; no es una regresión.

### 4.2 Qué NO se verificó

| Elemento | Motivo |
| --- | --- |
| Recompilación y hashes de artefactos | No se ejecutó build propio ni comparación byte a byte; pendiente si se promueve (SDD §7) |
| YARA sobre binarios | No aplicable en revisión estática |
| QtWebEngine/Chromium, `models/`, `vendor/`, `third_party/` | Misma limitación que §1.2 |
| Comportamiento real de `depot.sfg545.dev` y claves del autor | Fuera del control del auditor; `bootstrapper/` no cambió |
| UI de Android/desktop (QML/Kotlin) | Revisada por muestreo y por mensajes de commit, no línea a línea |

### 4.3 Servicios externos y backend del autor

#### Backend del autor (Cloudflare Workers y almacenamiento)

| Componente | Host / endpoint | Qué envía o hace | ¿Nuevo en main? |
| --- | --- | --- | --- |
| Cuenta Orchard (`services/account/`) | `account.sfg545.dev` | Autenticación Google OAuth + PKCE, sesiones JWT, registro de dispositivos (`/devices`, máx. 50), refresh tokens con rotación y detección de robo | No (ampliado) |
| Tokens Apple Music | `account.sfg545.dev/apple/developer-token` | Emite developer token ES256 (60 días) firmado con `APPLE_MUSIC_PRIVATE_KEY` del autor; requiere sesión; 150/día/cuenta + 5/min/cuenta; `origin` restringido a `appassets.androidplatform.net` | **Sí** |
| Traducción de letras en nube | `account.sfg545.dev/lyrics/translate` | Recibe `song_id` + líneas (≤48 KB), traduce con Cloudflare AI, cuota 10 canciones/día por cuenta **y** por IP (trigger SQLite), caché 90 días, IP hasheada | **Sí** |
| Artwork | `artwork.sfg545.dev` + Backblaze B2 | Sube/sirve artwork (WebP), fuentes | No |
| Soporte | `/support/reports` + R2 | Reportes de bugs con subida de capturas, sincroniza con issues de GitHub (webhook + `api.github.com`) | No |
| Connect | `/connect/hub` (Durable Object) + `rtc.live.cloudflare.com` | Señalización/presencia P2P; TURN opcional de Cloudflare Realtime | No |
| Last.fm proxy | `lastfm.sfg545.dev` | Proxy de scrobbling a Last.fm | No |
| Actualizador escritorio | `depot.sfg545.dev` | `releases/<canal>.json` y manifiestos firmados (`orchard-signed-v1`) | No (sin cambios) |
| Actualizador Android | `downloads.sfg545.dev` + `api.github.com` | Metadatos de versión | No (sin cambios) |

#### Proveedores de contenido

| Proveedor | Hosts | Observación |
| --- | --- | --- |
| YouTube | `music.youtube.com`, `www.youtube.com`, `ytjs.googlevideo.com`, `www.youtube.com/generate_204` (check conectividad), BotGuard/`GenerateIT` vía `bgutils-js` | Pre-existente; se añade lectura de la etiqueta "Made with AI" (§4.4) |
| **Apple Music (nuevo)** | `api.music.apple.com` (API con developer token), `amp-api.music.apple.com` (web/editorial con web token), `music.apple.com` (web token + MusicKit JS), `appassets.androidplatform.net` (origin del WebView Android) | Proveedor Node nuevo `providers/apple-music/` (AGPL-3.0-or-later). El host posee el token de usuario y las claves; el developer token lo emite el backend del autor |
| Qobuz | (existente, `providers/qobuz`) | Sin cambios observados |
| Gemini local (desktop) | `generativelanguage.googleapis.com` | Traducción on-device con clave del usuario (pre-existente), distinta de la traducción en nube del backend |

Otros terceros (no autor): `oauth2.googleapis.com` (Google OAuth), `itunes.apple.com/lookup` (artwork), `api.github.com` (issues).

### 4.4 Telemetría y comportamiento de red nuevo

- **No hay SDK de telemetría** nuevo (Sentry/Analytics) en el código nuevo revisado. No se hallaron rastreadores añadidos en los Workers de cuenta, el proveedor Apple Music ni el muestreador de procesos.
- El Worker de cuenta activa `[observability] enabled = true` (logs/traces de Cloudflare): telemetría **del backend**, no del cliente.
- **Nuevo — flagging de canciones IA** (commit `c18c1217`): desktop consulta en segundo plano la pista actual y la cola a YouTube para leer la etiqueta "Made with AI" y cachea veredictos en `ai-labels.sqlite`; Android consulta la pista actual + las 3 siguientes y guarda en la base de veredictos. El detector de audio "fakeprint" pasa a **opt-in** ("Audio AI analysis (deprecated)"). No exfiltra datos propios, pero es tráfico de red en segundo plano relevante para INV-6 (H7).
- **Nuevo — traducción en nube**: al usar la función, las letras (`song_id` + líneas) salen del dispositivo hacia `account.sfg545.dev` y Cloudflare AI. Requiere sesión, tiene cuotas y la IP se guarda solo como hash durante el procesamiento. Es un envío de datos con consentimiento implícito por uso (H8).
- **Nuevo — Apple Music**: el sign-in MusicKit JS corre en un WebView (Android) restringido a la pantalla de inicio de sesión; el Music User Token se cifra con Android Keystore y "disconnect" borra solo las cookies/storage de Apple.

### 4.5 Estado del actualizador

- **Sin cambios respecto al tag.** `bootstrapper/src/update/ReleaseClient.hpp:19` sigue fijando `kDefaultServer = "https://depot.sfg545.dev/"`, con `--background-update` lanzado sin confirmación y clave de confianza `orchard-release-1`. H1 y H2 siguen abiertos; HU-ORCH-02 y HU-ORCH-03 siguen siendo necesarias tal cual.
- El updater Android (`mobile/.../UpdateManager.kt`) tampoco cambió en el diff: `downloads.sfg545.dev` + releases del upstream (H4).

### 4.6 Licencia: OOL-1.0 → PolyForm Shield 1.0.0

- `LICENSE-OOL` (Orchard-Only License 1.0) **se elimina** y se añade `LICENSE-POLYFORM-SHIELD` (PolyForm Shield License 1.0.0) con `Required Notice: Copyright (C) 2026 SFG545` y `Licensor Line of Business: Orchard music player and its adaptive mix engine`.
- `LICENSE-EXCEPTION.md` se actualiza: permite combinar las partes AGPL con el motor bajo PolyForm Shield; el Corresponding Source AGPL no necesita incluir el motor.
- **Consecuencia para el fork:** la limitación **L1 (§1.6) queda resuelta**. El fork puede compilar y distribuir el motor adaptativo (Adaptive mix / Best Mix) siempre que no sea un "producto que compite" (cláusula Noncompete). Un fork de auditoría que distribuye el mismo Orchard no se comercializa como sustituto, pero **debe** conservar el `Required Notice` y la línea `Licensor Line of Business`. Confirmar con el propietario antes de HU-ORCH-05.
- `README.md` del upstream se actualiza en la sección de licencia para reflejar PolyForm Shield en lugar de OOL-1.0; `THIRD_PARTY_NOTICES.md` **no cambió** en este diff (mismo SHA que el tag).

### 4.7 Hallazgos nuevos

| ID | Severidad | Hallazgo | Estado | HU / acción |
| --- | --- | --- | --- | --- |
| H6 | Media | Ejecución de JS de terceros (YouTube BotGuard + `nsig`) vía `new Function` en el proveedor YouTube (pre-existente, ahora documentado) | Abierto (aceptado) | Documentar en el fork; no mitigable sin romper YouTube |
| H7 | Baja | Flagging IA: búsquedas en segundo plano a YouTube y caché local (`ai-labels.sqlite` / base de veredictos) | Abierto | INV-6; opcional desactivar por defecto en el fork |
| H8 | Baja | Traducción en nube envía letras + `song_id` al backend del autor y a Cloudflare AI | Aceptado (con cuotas y hash de IP) | Documentado §4.4 |
| H9 | Baja | El autor custodia `APPLE_MUSIC_PRIVATE_KEY` y emite los developer tokens desde su Worker | Aceptado (acotado por sesión + cuotas) | Vigilar cambios en `/apple/developer-token` (REQ-M4) |
| H10 | Info | Las variantes Android "Canary fast" (no-debuggable, sin R8/shrinking, x86_64 para emulador) son flavours de Gradle en `mobile/android`, no cambios de CI. Los workflows (`.github/`) **no cambiaron** en el diff (subárbol idéntico al tag); §2 sigue vigente | Abierto | Solo relevante si SDD §9 (Android) se activa |
| H11 | Baja | El `README.md` de `main` sigue apuntando a `github.com/SFG5453/orchard-v4` para clonar/issues (L2 sin resolver) | Abierto | Confirmar repo canónico en HU-ORCH-04 |

### 4.8 Recomendación técnica

**Contener (no promover todavía), no por malware sino por proceso.** El código nuevo está limpio y es coherente con el proyecto; no hay backdoor ni exfiltración. Los motivos para no promover `main` ahora son:

1. **No hay tag nuevo**: `main` es una rama móvil y el proceso del fork (SDD §7) exige promover un tag auditado, no un commit de rama.
2. **El auto-update sigue apuntando a `depot.sfg545.dev`** (§4.5). Hasta aplicar HU-ORCH-02 y HU-ORCH-03, incorporar `main` conservaría la descarga/instalación sin confirmación.
3. **Revisión de UI parcial** (§4.2): los cambios grandes de QML/Kotlin se revisaron por muestreo; conviene una pasada completa sobre el tag estable.

Acción recomendada cuando el upstream publique un tag estable: repetir esta revisión sobre el tag, reaplicar los commits `audit-fork:` (UPSTREAM.md), ejecutar HU-ORCH-02/03 y proceder con HU-ORCH-05. **La relicencia a PolyForm Shield desbloquea la publicación de binarios con el motor adaptativo** (L1 resuelto), siempre conservando el `Required Notice` y sin posicionar el fork como producto competidor.

## 5. Contra-auditoría de red del árbol `audited` (2026-10-16)

- **Commit revisado:** `304e63e` (rama `audited`, árbol limpio).
- **Método:** inventario de todos los hosts `http(s)/ws(s)/stun/turn` en el código no-test (`git grep`, excluye `*.md`, `vendor/`, `third_party/`, tests), lectura de cada coincidencia relevante y búsqueda de SDK de telemetría y dependencias Gradle.
- **No verificado:** tráfico real en ejecución (sin captura de red), contenido de binarios precompilados (`discord_partner_sdk.aar`, Google Play Services Cast), defaults de opt-in de cada integración en desktop (solo se revisaron puntualmente).

### 5.1 Infraestructura del autor

| Ref | Ubicación | Estado |
| --- | --- | --- |
| `*.sfg545.dev` | Solo en comentarios (`discord_artwork.cpp:83`, `lastfm.cpp:39`) y un test Android | ✅ Sin llamadas activas |
| `huggingface.co/SFG545/orchard-lyric-translation` | `core/native/translation/translation_pack_table.cpp:25`, 15 packs (`jpn-eng` y todos los `*-high`) | ✅ Retirados (H12, §5.6) |
| Discord application ID `1531666622312353803` | `app/src/integrations/discord.cpp:34`, `discord_sdk_bridge.cpp:38`, `DiscordArtwork.kt:24` | ✅ Eliminado; ID propio por `ORCHARD_DISCORD_APP_ID` / `orchardDiscordAppId` (H14, §5.6) |
| `github.com/SFG5453/...` | User-Agent ListenBrainz (`ListenBrainzRepository.kt:228`), `Cargo.toml`, comentario de workflow | Info: sin tráfico al autor |

### 5.2 Integraciones de terceros confirmadas

| Grupo | Hosts | Código |
| --- | --- | --- |
| Proveedores | `music.youtube.com`, `www.youtube.com`, `i.ytimg.com`, `accounts.google.com`, `oauth2.googleapis.com`; `www.qobuz.com`, `play.qobuz.com` | `providers/youtube`, `providers/qobuz` |
| Metadata/imagen | `www.theaudiodb.com`, `itunes.apple.com`, `artwork.m8tec.top`, `artwork.boidu.dev` | `album_controller.cpp`, `animated_artwork_service.cpp`, `ArtworkRepository.kt` |
| Letras | `lrclib.net`, `lyrics-api.boidu.dev`, `lyrics-api.binimum.org`, `lyricsplus.binimum.org`, `lyricsplus.prjktla.workers.dev`, `lyricsplus-seven.vercel.app`, `lyrics-plus-backend.vercel.app` | `lyricsResolver.js`, `betterLyricsProvider.js`, `LyricsRepository.kt` |
| Reproducción | `sponsor.ajay.app` | `providers/youtube/src/integrations/sponsorblock.js:28` |
| Social | Discord IPC/Social SDK; `spclient.wg.spotify.com`, `api-partner.spotify.com`, `clienttoken.spotify.com`, `api.spotify.com`, `accounts.spotify.com`; `api.listenbrainz.org` | `spotify_canvas_api.cpp`, `SpotifyCanvasRepository.kt`, `ListenBrainzRepository.kt` |
| STUN/TURN | `stun:stun.cloudflare.com:3478`, `rtc.live.cloudflare.com` (credenciales TURN, lado Worker) | `services/account/src/connect.js:36,124`, `party.js:31` |
| Enlaces | `song.link`, `album.link` (solo construcción de URL compartible) | `MediaMenu.qml`, `SongLinksRepository.kt` |
| Cuenta/Connect | `connect.kasper-cloud.com` (Worker propio, HU-ORCH-06) | `orchard_account.cpp:43`, `OrchardAccountService.kt`, `ListeningPartyClient.kt` |

**No incluidas en el barrido de DeepSeek:**

| Host | Código | Disparo |
| --- | --- | --- |
| `www.gstatic.com/generate_204`, `www.youtube.com/generate_204` | `app/src/offline/connectivity_monitor.cpp:33` | ✅ Sustituido por `connect.kasper-cloud.com/.well-known/jwks.json` (H13, §5.6) |
| Google Cast (Play Services) | `MainActivity.kt:54`, `OrchardPlaybackService.kt:193`, `OrchardCastOptionsProvider.kt`, manifest | ✅ Sin inicialización de `CastContext` ni `ChromecastPlayback` (H15, §5.6) |
| `api.openai.com`, `api.anthropic.com`, `generativelanguage.googleapis.com` | `lyrics_translator.cpp:47-49`, `RemoteLyricTranslation.kt:90-92` | Opt-in, con clave del usuario |
| `huggingface.co/Helsinki-NLP` | `translation_pack_table.cpp:23` | Bajo demanda, commit fijado + SHA-256 |
| `api.github.com/repos/jucsp/orchard-audited` | `UpdateManager.kt:53` | Releases del fork |
| `ws.audioscrobbler.com` | `services/lastfm` (Worker, no cliente) | Cliente desactivado (`lastfm.cpp` URL vacía) |
| `jitpack.io`, `codeload.github.com` | `settings.gradle:19`, `scripts/build-qt-webview.py` | Solo en build |

### 5.3 Telemetría

- ✅ Sin SDK de telemetría/crash reporting en desktop, Android ni providers (Sentry, Firebase, Crashlytics, Bugsnag, AppCenter, Mixpanel, Amplitude, PostHog, Datadog, OpenTelemetry, Google Analytics): 0 coincidencias en el código y en `build.gradle`.
- ⚠️ Binarios cerrados con canal propio posible: Google Play Services Cast (`media3-cast`) y `discord_partner_sdk.aar`. No auditables estáticamente.
- Worker `services/account`: `[observability]` de Cloudflare es del backend propio, no del cliente.

### 5.4 Hallazgos nuevos

| ID | Severidad | Hallazgo | Estado | Acción propuesta |
| --- | --- | --- | --- | --- |
| H12 | Media | 15 packs de traducción offline se descargan del repo HuggingFace del autor (`SFG545/orchard-lyric-translation`). Integridad cubierta por commit fijado + SHA-256, pero viola "sin hosts del autor en rutas de descarga" e INV-3; el autor ve IP y momento de descarga | Resuelto (§5.6) | DeepSeek: re-alojar los packs en un repo HF propio (mismos hashes) o desactivar esos 15 packs |
| H13 | Baja | Sonda de conectividad a Google/YouTube al arrancar y cada 15 s sin interacción del usuario (INV-6) | Resuelto (§5.6) | Probar contra el propio Worker (`connect.kasper-cloud.com`) o solo bajo demanda tras un fallo |
| H14 | Baja | Discord Rich Presence usa la aplicación de Discord del autor; el autor controla nombre/assets y Discord le atribuye la actividad | Resuelto (§5.6) | Registrar una aplicación propia y sustituir el ID en los 3 puntos |
| H15 | Baja | Cast sigue activo en Android: `CastContext` y `ChromecastPlayback` se inicializan al arrancar, con descubrimiento de dispositivos en la LAN vía Play Services, aunque HU-ORCH-08 retiró la UI | Resuelto (§5.6) | Quitar la inicialización o la dependencia `media3-cast` (completar HU-ORCH-08) |

### 5.5 Conclusión QA

- ✅ Ninguna llamada activa a `*.sfg545.dev`; cuenta, Connect y party apuntan al Worker propio; Last.fm desactivado; updater Android hacia releases del fork.
- ✅ La lista de terceros de DeepSeek es correcta en lo que cubre.
- ❌ Incompleta: faltan la descarga desde HuggingFace del autor (H12), la sonda periódica a Google (H13), Cast activo en Android (H15) y la traducción con LLM (opt-in).
- ✅ Sin telemetría por SDK en el código abierto; ⚠️ quedan dos binarios cerrados sin auditar.
- **Veredicto (revisión inicial):** privacidad aceptable para uso personal; no cumplía INV-3/INV-6 en sentido estricto hasta cerrar H12 y H13. Las correcciones están en §5.6.

### 5.6 Remediación H12 a H15 (2026-10-16)

- **Base:** `304e63e` + cambios sin commit en el árbol de trabajo. Aplicados por Claude (PM/QA) por instrucción directa del propietario.

| ID | Archivo | Cambio |
| --- | --- | --- |
| H12 | `core/native/translation/translation_pack_table.cpp` | Retirados los 15 packs `ORCHARD_HOSTED` y la macro. Quedan 12 packs `standard` de `Helsinki-NLP`. Con calidad `high`, `resolve()` usa el pack `standard`; japonés queda sin traducción offline |
| H13 | `app/src/offline/connectivity_monitor.cpp` | `kProbeUrls` con una sola URL: `https://connect.kasper-cloud.com/.well-known/jwks.json` (endpoint público del Worker propio) |
| H14 | `discord.cpp`, `discord_sdk_bridge.cpp`, `DiscordArtwork.kt`, `DiscordPresenceCoordinator.kt`, `mobile/android/app/build.gradle` | Sin ID por defecto. Escritorio: `ORCHARD_DISCORD_APP_ID`; sin la variable no se crea el handle RPC. Android: propiedad Gradle `orchardDiscordAppId` → `BuildConfig.DISCORD_APPLICATION_ID`; con `0` la presencia queda `Unavailable` |
| H15 | `MainActivity.kt`, `OrchardPlaybackService.kt` | Quitados `CastContext.getSharedInstance`, `ChromecastStreamServer`, `ChromecastPlayback` y su listener |

**Verificación:**

- ✅ `git grep` sin `SFG545/orchard-lyric-translation`, `generate_204` ni `1531666622312353803` en código no-doc.
- ✅ `npm test` en `services/account`: 76/76.
- ✅ `g++ -fsyntax-only` de `translation_pack_table.cpp`, `connectivity_monitor.cpp` y `discord.cpp`.
- ❌ Sin verificar: suite C++ (`meson` no instalado), build Android (sin Gradle/SDK), `discord_sdk_bridge.cpp` (necesita `discordpp`). `tests/lyrics_translation_test.cpp::qualityPicksPack` debería seguir pasando por el fallback a `standard`, pero no se ejecutó.

**Residual:**

- Dependencia `media3-cast`, `OrchardCastOptionsProvider`, meta-data del manifest y `MediaTransferReceiver` siguen en el APK sin inicializarse. Quitarlos completa HU-ORCH-08 y requiere build Android.
- `scripts/translation/emit_packs.py` sigue apuntando al repo del autor (solo herramienta de desarrollo, no se ejecuta en runtime).
- La sonda de conectividad expone IP y frecuencia al Worker propio cada 15 s. Si cae `connect.kasper-cloud.com`, la app se muestra offline aunque haya red.
