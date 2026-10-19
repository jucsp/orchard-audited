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
| Google Cast (Play Services) | `MainActivity.kt:54`, `OrchardPlaybackService.kt:193`, `OrchardCastOptionsProvider.kt`, manifest | ✅ Sin inicialización de `CastContext` ni `ChromecastPlayback` (H15, §5.6). Se sustituye por CastV2 propio, solo LAN y a pedido del usuario (HU-ORCH-12, §5.10) |
| `api.openai.com`, `api.anthropic.com`, `generativelanguage.googleapis.com` | `lyrics_translator.cpp:47-49`, `RemoteLyricTranslation.kt:90-92` | Opt-in, con clave del usuario |
| `huggingface.co/Helsinki-NLP` | `translation_pack_table.cpp` | ✅ Retirado: tabla de packs vacía, sin traducción offline (HU-ORCH-09, §5.7) |
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
| H15 | Baja | Cast sigue activo en Android: `CastContext` y `ChromecastPlayback` se inicializan al arrancar, con descubrimiento de dispositivos en la LAN vía Play Services, aunque HU-ORCH-08 retiró la UI | Resuelto (§5.6). Cast vuelve como CastV2 propio por decisión del propietario (C1, §5.10) | Quitar la inicialización o la dependencia `media3-cast` (completar HU-ORCH-08). El retiro de `media3-cast` pasa a HU-ORCH-12 (Q1) |

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
- ~~`scripts/translation/emit_packs.py` sigue apuntando al repo del autor~~: carpeta eliminada (§5.8).
- La sonda de conectividad expone IP y frecuencia al Worker propio cada 15 s. Si cae `connect.kasper-cloud.com`, la app se muestra offline aunque haya red.

### 5.7 Eliminación de Helsinki-NLP y traducción offline (HU-ORCH-09, 2026-10-16)

- **Base:** `63bee8a` + cambios sin commit en el árbol de trabajo. Aplicados por Claude (PM/QA) por instrucción directa del propietario.
- **Resultado:** el fork no descarga ningún modelo de traducción. La única traducción de letras es por API (OpenAI, Claude, Gemini o API propia), opt-in y con clave del usuario.

| Capa | Archivo | Cambio |
| --- | --- | --- |
| Core | `core/native/translation/translation_pack_table.cpp` | Sin `ORCHARD_HELSINKI`, `kHelsinkiCredit` ni los 12 packs. `translationPackTable()` devuelve un `std::span` vacío (un array C++ de tamaño cero no compila). Desktop y Android (JNI `packTable()`) leen la misma tabla |
| App | `lyrics_translator.h/.cpp` | Proveedor por defecto vacío; `setProvider` y la lectura de `QSettings` ya no aceptan `local`. Sin proveedor: no se lee el keychain y el estado es "Choose a translation source in Settings." |
| QML | `LyricTranslationSettingsForm.ui.qml`, `LyricTranslationSettings.qml` | Selector con OpenAI, Claude, Gemini y Custom API (muestra "Choose" sin proveedor). Fuera la fila de calidad, la lista de modelos y los textos de traducción local |
| QML | `DownloadsSectionForm.ui.qml`, `SettingsPanel.ui.qml`, `LyricsTranslateBar.qml` | Sin menciones a modelos de traducción; sin el estado `downloading` |
| Android | `SettingsLyrics.kt`, `SettingsIndex.kt`, `UiModels.kt` | `LOCAL` fuera del diálogo de selección y tratado como "sin proveedor" ("Choose"). Fuera la calidad y la lista de modelos instalados, y la entrada de búsqueda "Translation quality" |
| Android | `LyricTranslator.kt` | Con `LOCAL` la traducción queda desactivada: no corre el plan nativo ni se resuelven packs |
| Tests | `tests/lyrics_translation_test.cpp` | `packNeedsMarkerAndSizes` y `qualityPicksPack` reemplazados por `packTableIsEmpty`: nada resuelve, nada se instala y `ensure()` emite `failed` sin crear la carpeta de staging |

**Verificación:**

- ✅ `git grep` sin `huggingface.co/Helsinki-NLP`, `ORCHARD_HELSINKI` ni `kHelsinkiCredit` en código de la app.
- ✅ `npm test` en `services/account`: 76/76.
- ✅ `g++ -std=c++20 -fsyntax-only -Wall -Wextra` con Qt 6.11 real de `translation_pack_table.cpp`, `translation_packs.cpp`, `lyrics_translator.cpp` y `tests/lyrics_translation_test.cpp` (stubs mínimos solo para `qtkeychain` y `litert`, que no están instalados).
- ✅ Prueba en ejecución de `TranslationPacks` real enlazada contra la tabla vacía (Qt Test, fuera del repo): 3/3. Cubre `find`, `resolve`, `available`, `list`, `installedBytes`, `ensure`, `cancel` y `remove`.
- ✅ `qmllint` sin errores en los 5 QML modificados.
- ❌ Sin verificar: suite C++ completa (`meson` no instalado) y build Android/Kotlin (sin Gradle/SDK).

**Residual:**

- El runtime offline (`TranslationPacks`, `TranslationWorker`, `MarianModel`, LiteRT, JNI de traducción) sigue compilado pero no tiene packs que cargar. Quitarlo entero es un cambio mayor en meson y CMake de Android.
- ~~`scripts/translation/` y la atribución OPUS-MT de `THIRD_PARTY_NOTICES.md`~~: eliminados (§5.8).

### 5.8 Limpieza de residuos de Helsinki-NLP (HU-ORCH-09, 2026-10-16)

- **Base:** `63bee8a` + cambios sin commit en el árbol de trabajo. Aplicados por Claude (PM/QA) por instrucción directa del propietario.

| Archivo | Cambio |
| --- | --- |
| `scripts/translation/` | Eliminada entera (`README.md`, `build_packs.py`, `check_pack.py`, `convert_marian.py`, `emit_packs.py`). Convertía modelos de HuggingFace/Helsinki-NLP y generaba la tabla apuntando a `SFG545/orchard-lyric-translation`. Ningún archivo de build, CI ni doc la referencia |
| `THIRD_PARTY_NOTICES.md` | Fuera la sección "Lyric translation models" (OPUS-MT de Helsinki-NLP, `Mitsua/elan-mt-tiny-ja-en` y el repo HF del autor): el fork no descarga ni distribuye esos modelos. Se conserva la sección LiteRT porque el runtime sigue vendorizado y se empaqueta |

**Verificación:**

- ✅ `git grep -i` sin `helsinki`, `opus-mt`, `elan-mt`, `orchard-lyric-translation` ni `scripts/translation` fuera de la documentación de gobernanza (`AUDIT.md`, `technical_memory.md`, `sprint_board.md`, `UPSTREAM.md`). Coincidencias restantes sin relación: `helsinki` en `models/docs-search/vocab.txt` (vocabulario del modelo de búsqueda), `orchard-lyric-translation/1.0` como valor de `x-goog-api-client` hacia Gemini (`lyrics_translator.cpp:446`, `RemoteLyricTranslation.kt:55`) y el nombre de conexión SQLite local en `translation_store.cpp:32`.
- ✅ `npm test` en `services/account`: 76/76.
- Sin código compilado afectado: no aplica build C++ ni Android.

### 5.9 APK de Android en el pipeline de release (HU-ORCH-10, 2026-10-16)

- **Base:** `6f0b649` + cambios sin commit en el árbol de trabajo. Aplicados por Claude (PM/QA) por instrucción directa del propietario.
- **Alcance:** solo CI (`.github/workflows/release.yml`). Sin cambios en código de la app.

| Elemento | Cambio |
| --- | --- |
| Job `android` ("Android arm64-v8a APK", `ubuntu-24.04`) | `checkout` con submódulos, Node 24 + `npm ci --prefix providers/youtube`, JDK 17 temurin con caché de Gradle, Rust stable con `aarch64-linux-android` y `x86_64-linux-android` (vía `rustup`, sin acción de terceros; Gradle compila Earmark para ambos ABIs aunque el release solo empaquete arm64-v8a) y `cargo install cargo-ndk --locked`. Instala con `sdkmanager` el NDK que fija `build.gradle` (`28.2.13676358`) y `cmake;3.22.1`, y apunta `ANDROID_NDK_HOME`/`ANDROID_NDK_ROOT` a ese NDK para que cargo-ndk y CMake enlacen contra el mismo. `./gradlew --no-daemon assembleRelease` y artifact `orchard-android` con `Orchard-Android-arm64-v8a.apk` |
| Firma | Si existen los secretos `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD`, se usa esa keystore. Si falta alguno, `keytool` genera una PKCS12 desechable (alias `orchard`, RSA 4096, 10000 días, contraseña `openssl rand -hex 32` enmascarada) y Actions muestra un warning. La keystore se borra del runner después del build |
| Job `publish` | `needs: [linux, rpm-check, windows, android]`, descarga `orchard-android` en `dist`, exige `Orchard-Android-arm64-v8a.apk` no vacío antes de escribir `SHA256SUMS.txt` y suma la viñeta de Android a las notas |
| Acciones | `actions/setup-java` fijada por SHA (`b6effb05…`, tag `v5` = `v5.7.0`, comprobado con `git ls-remote`). El resto reutiliza los SHA del archivo |

**Red y hosts:** el job descarga de npm, crates.io, Maven/Google (Gradle, AGP) y `dl.google.com` (NDK). Ningún host del autor. El APK sale con el `UpdateManager` del fork (`jucsp/orchard-audited`), pero este build no publica `latest-android.json`, así que el updater de Android no encuentra versiones nuevas.

**Verificación:**

- ✅ `yaml.safe_load`: jobs `linux`, `rpm-check`, `windows`, `android`, `publish`; `publish.needs` incluye `android`.
- ✅ `bash -n` sobre cada `run` de `android` y `publish`.
- ✅ La expresión `sed` extrae `28.2.13676358` de `build.gradle`.
- ✅ Rama sin secretos del paso de keystore ejecutada en local: PKCS12 válida con alias `orchard`; contraseñas enmascaradas en la salida.
- ✅ Jobs `linux`, `rpm-check` y `windows` sin cambios (el diff solo toca la cabecera, el job nuevo y `publish`).
- No verificado: ejecución real en Actions ni `assembleRelease` (no hay SDK de Android en esta máquina). `actionlint` no está instalado.

**Riesgo pendiente:** con la keystore desechable cada release se firma con una clave distinta, así que Android rechaza instalar el APK nuevo encima del anterior: hay que desinstalar y se pierden los datos locales. Para actualizar sin desinstalar, el propietario debe cargar una vez los cuatro secretos `ANDROID_KEYSTORE_*` en el repositorio.

### 5.10 Re-implementación de Chromecast con CastV2 propio (HU-ORCH-12, 2026-10-17)

- **Base:** `d432ff5` (contrato y decisión). Implementación en `integrate/hu-orch-12` hasta `d228910`.
- **Contrato:** `SDD-chromecast-castv2.md` v1.0 (invariantes `CAST-INV-1` a `CAST-INV-9`).
- **Tipo:** cambio del fork sobre una zona protegida propia (H15, INV-6). No viene del upstream, pero se registra con la plantilla de la Regla 4 porque revierte una decisión de HU-ORCH-08.

#### Conflicto C1: HU-ORCH-12, `mobile/.../playback/` y `mobile/.../connect/app/MainActivity.kt`
- Cambio propuesto: volver a transmitir audio a Chromecast, Google TV y parlantes Google Home/Nest desde Android, y en una fase 2 desde escritorio. Motivo: uso personal del propietario en casa y en el auto.
- Parche del fork afectado: remediación H15 (§5.6, sin `CastContext` ni `ChromecastPlayback` al arrancar) y purga de UI de HU-ORCH-08. Protege INV-6 y la ausencia de binarios cerrados sin auditar (§5.3).
- Opción A, conservar el parche y no tener Cast: pros, superficie mínima y cero binarios de Google. Contras, sin la función que pide el propietario.
- Opción B, adaptar el parche: CastV2 propio sin Play Services (mDNS `_googlecast._tcp`, TLS al puerto anunciado, `CastMessage` codificado a mano, Default Media Receiver `CC1AD845`, `ChromecastStreamServer` local). Pros, código abierto auditable, sin costo, sin servidores externos, sin actividad hasta que el usuario busca dispositivos, y se puede retirar `media3-cast` del APK. Contras, protocolo propio que mantener, TLS sin CA (se mitiga con pin TOFU) y prueba en hardware real solo manual. Esfuerzo: 5 SP en Android; escritorio aparte.
- Opción C, restaurar `media3-cast` con el App ID por defecto: no requiere pago, pero reintroduce Play Services, que es un binario cerrado con canal propio posible (§5.3) y mantiene el descubrimiento fuera del control del fork. No se recomienda.
- Riesgo: baja. Superficie, LAN del usuario: socket TLS saliente al dispositivo elegido y servidor HTTP efímero con token de 128 bits que solo atiende al IP del receptor. Explotabilidad, requiere un atacante en la misma red. Invariantes afectados: INV-6, cubierto por `CAST-INV-2`, `CAST-INV-3` y `CAST-INV-6`.
- Recomendación del Swarm: Opción B. Es la única que da la función sin binarios cerrados ni servidores externos.
- Decisión del propietario: **Opción B aprobada** (2026-10-17). Alcance aprobado: Android y escritorio, incluidos parlantes Google Home/Nest Mini. La fase 2 (escritorio) se abre con orden explícita del PO.

- **Estado: RESUELTO Y CERRADO** (2026-10-17). Opción B ejecutada en `integrate/hu-orch-12` (`70175ed..d228910`, 9 commits `audit-fork:`, uno por tarea). H15 sigue vigente: nada de cast se inicializa al arrancar. El cierre es del conflicto; llevar la rama a `audited` sigue bloqueado por los pendientes de §5.10.3.

#### 5.10.1 Informe QA de invariantes (Q3)

- **Base:** `d228910` + dos correcciones de QA sin commit (abajo). Método: revisión estática del diff `d432ff5..d228910` y `git grep` sobre `mobile/`. Esta máquina no tiene Android SDK ni `kotlinc`: no se compiló ni se ejecutó ningún test.

| Invariante | Resultado | Evidencia |
| --- | --- | --- |
| CAST-INV-1 Sin dependencias propietarias | ✅ estático | `build.gradle` sin `media3-cast`; manifest sin `OPTIONS_PROVIDER_CLASS_NAME` ni `MediaTransferReceiver`; sin `ChromecastMediaItemConverter.kt` ni `OrchardCastOptionsProvider.kt`. `git grep -iE 'com\.google\.android\.gms\|play-services\|mediarouter\|media3-cast'` en `mobile/` solo devuelve la meta-data `com.google.android.gms.car.application` de Android Auto, que es una clave del manifest y no una dependencia (queda excluida de la verificación). Falta `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` |
| CAST-INV-2 Sin servidores externos | ✅ | El paquete `cast` solo abre: `NsdManager` (mDNS), `SSLSocket` al IP:puerto resuelto y validado con `parsePrivateIpv4` (sin DNS), y el `ServerSocket` local de `ChromecastStreamServer`. Única URL construida: `http://<ipv4-lan>:<puerto>/cast/<token>/<mediaId>`. Las carátulas se pasan tal cual al receptor (§4.6). Sin hosts del autor (`sfg545`, `depot.`) |
| CAST-INV-3 Opt-in | ✅ | `MainActivity.kt` sin referencias a cast. `OrchardPlaybackService.onCreate` solo registra un `CastHost`; `ChromecastPlayback` se construye en `castPlayback()` al conectar. `CastDiscovery` crea `NsdManager` y `MulticastLock` de forma perezosa; `discoverServices` y `acquire()` solo desde `CastPicker.search()` (botón Buscar). Se detiene a los 30 s (`DEFAULT_TIMEOUT_MS`), al pulsar Detener o al salir de la pantalla (`DisposableEffect`) |
| CAST-INV-4 Servidor de stream efímero | ✅ con observación | Una instancia por sesión en `ChromecastPlayback.connectToDevice` con `receiverIp = device.host`; se cierra en `teardown`. Token de 16 bytes con `SecureRandom`; IP ajena → 404 antes de leer la petición; token o ruta inválidos → 404; métodos distintos de `GET`/`HEAD` → 405; `close()` corta conexiones vivas. Observación: `ServerSocket(0)` escucha en todas las interfaces, no solo en la IPv4 de la LAN; el filtro por `receiverIp` lo compensa |
| CAST-INV-5 App ID fijo | ✅ | `CastSession.APP_ID_DEFAULT_MEDIA_RECEIVER = "CC1AD845"` y test de la constante. `git grep -i cast_app_id` vacío en `mobile/` |
| CAST-INV-6 Sin telemetría, logs saneados | ✅ tras corrección | Los `Log.w` de `ChromecastPlayback` solo registran el tipo de excepción. **Corregido en QA:** los dos `Log.w` de `ChromecastStreamServer` pasaban el `Throwable` completo, cuyo mensaje puede llevar la URL de origen o el `mediaId`; ahora solo el tipo. Sin otras llamadas a `Log`/`println` en el paquete |
| CAST-INV-7 Solo IP privada y pin TOFU | ✅ | `parsePrivateIpv4` acepta `10/8`, `172.16/12`, `192.168/16` y `169.254/16` en forma canónica; rechaza `100.64/10`, loopback, multicast y públicas. Se aplica en el parseo del TXT y en `CastChannel.open`. `PinningTrustManager` sin CA del sistema, guarda SHA-256 por `id` en el primer uso y aborta con `CastSecurityException` si cambia. Persistencia en `SharedPreferencesCastTrustStore` (`orchard_cast_tofu_pins`, `commit()` síncrono, `allowBackup="false"`) |
| CAST-INV-8 Límites y cierres limpios | ✅ | `MAX_MESSAGE_SIZE = 65536` al codificar, al leer la longitud del frame (bit alto incluido) y en cada campo length-delimited. Varints de longitud/tag limitados a 5 bytes; grupos desconocidos y JSON con máximo 32 niveles de anidamiento; JSON inválido o frame mal formado cierran el canal |
| CAST-INV-9 Archivos < 500 líneas y cabeceras | ✅ | Archivo mayor: `CastV2Player.kt` con 499. Los 9 archivos nuevos de `cast/`, `CastControls.kt` y los 10 tests nuevos de `cast/` llevan `SPDX-License-Identifier: AGPL-3.0-or-later`; los modificados del upstream (`ChromecastPlayback.kt`, `ChromecastStreamServer.kt`, `ChromecastSettingsRow.kt`) conservan la de SFG545 |

**Corregido en QA (sin commit):**

- `res/values/styles.xml`: fuera `<item name="mediaRouteTheme">@style/Theme.MediaRouter</item>`. El atributo y el estilo vienen de `androidx.mediarouter`, que llegaba solo por `media3-cast`; sin esa dependencia, AAPT habría fallado al enlazar recursos. Ningún `git grep` previo lo detectó porque buscaba `androidx.mediarouter`.
- `playback/ChromecastStreamServer.kt`: logs con solo el tipo de excepción (CAST-INV-6) y KDoc ajustado al ciclo de vida por sesión.

#### 5.10.2 Suite de tests JVM

95 tests en 9 clases, más 3 archivos de soporte (`CastSessionTestSupport.kt`, `CastMediaTestSupport.kt` y fakes en línea). No ejecutados en local.

| Clase | Tests | Cubre |
| --- | --- | --- |
| `CastMessageTest` | 18 | Codec proto2 contra `tests/fixtures/castv2/*.hex`, campos desconocidos, varints largos, límite exacto 65536, bytes sobrantes, grupos anidados |
| `CastTrustTest` | 14 | Rangos RFC 1918/link-local y bordes, CGNAT, literales no canónicos, pin en primer uso, coincidencia, cambio, pin por dispositivo |
| `CastChannelTest` | 15 | Framing con socket falso, correlación por `requestId` y timeout, `requestId: 0`, frame > 64 KiB, longitud con bit alto, cuerpo mal formado, EOF con `onClose` único |
| `CastSessionTest` | 11 | `CONNECT` → `GET_STATUS` → `LAUNCH CC1AD845`, reutilización de la app, `SET_VOLUME`, `PING`/`PONG`, `CLOSE` entrante, JSON inválido, reconexión exitosa y fallida |
| `CastMediaChannelTest` | 9 | `LOAD` con el JSON de §4.5, `PLAY`/`PAUSE`/`SEEK`/`STOP`/`GET_STATUS` con `mediaSessionId`, `LOAD_FAILED`/`INVALID_REQUEST` |
| `CastMediaChannelEdgeTest` | 6 | `FINISHED` una vez por `mediaSessionId`, `duration` conservada, `status` vacío, posiciones no finitas, comandos tras reconexión |
| `CastDiscoveryTest` | 11 | `CastTxtParser`: `id` obligatorio, `fn` con fallback, `ca` decimal/hex, IP no privada rechazada, puertos de grupo `32xxx` |
| `ChromecastStreamServerTest` | 8 | Token de 32 hex, token inválido y ruta desconocida → 404, IP ajena → 404, IP del receptor y sin filtro llegan al resolver, `close()` deja de aceptar y corta conexiones en espera |
| `SharedPreferencesCastTrustStoreTest` | 3 | Pin que sobrevive a una instancia nueva, apertura perezosa de las preferencias, pin previo que rechaza otro certificado |

Sin tests: `CastDiscovery` (depende de `NsdManager`), `CastV2Player` y `CastControls` (dependen de `Looper`/Media3) y la UI Compose.

#### 5.10.3 Criterios de aceptación (`SDD-chromecast-castv2.md` §6)

| Criterio | Resultado |
| --- | --- |
| Árbol de dependencias de release sin `gms`/`play-services`/`mediarouter`; APK sin `com.google.android.gms.cast` | ⏳ estático ✅; falta `:app:dependencies` y revisar el APK en Actions |
| Sin tráfico mDNS ni sockets de cast al arrancar | ✅ estático; ⏳ captura con `tcpdump` en QA manual |
| Descubre Chromecast/Google TV, Nest/Home Mini y grupo multizona con `fn` | ⏳ QA manual del propietario |
| Conecta, lanza `CC1AD845` y reproduce desde la posición del teléfono | ⏳ QA manual |
| Play, pausa, seek, siguiente/anterior y volumen desde app y notificación | ⏳ QA manual |
| Avance automático al terminar una pista | ✅ tests de `FINISHED`; ⏳ QA manual |
| Desconectar devuelve al teléfono y cierra el servidor | ✅ estático (`teardown`); ⏳ QA manual |
| Corte de Wi-Fi o apagado termina la sesión en ≤ 20 s | ❌ riesgo: 15 s sin tráfico más un reintento con timeout de conexión puede superar 20 s. Medir en QA manual; si falla, sin reintento cuando el socket cae por timeout |
| Servidor rechaza token inválido e IP ajena | ✅ tests (no ejecutados) |
| Certificado distinto para el mismo `id` aborta con aviso | ✅ tests (no ejecutados) y mensaje propio en `ChromecastPlayback` |
| Tests JVM T1–T7 en verde; `assembleRelease` en verde en Actions | ❌ no ejecutados: sin SDK en local y la rama no se ha corrido en Actions |
| `AUDIT.md` con informe QA y C1 cerrado; `UPSTREAM.md` con zonas activas | ✅ |

**Bloqueantes para llevar `integrate/hu-orch-12` a `audited`:** `testDebugUnitTest`, `:app:dependencies --configuration releaseRuntimeClasspath` y `assembleRelease` en verde en Actions, y la prueba manual de §6 registrada por el propietario. Pendiente aparte: decisión del propietario sobre el titular del copyright de los archivos nuevos (hoy `SFG545`).

### 5.11 Android TV "Glass Rail" (HU-ORCH-11, 2026-10-18)

- **Base:** `f89664f` más cambios sin commit en `integrate/hu-orch-11`: código TV de DeepSeek en `ui/tv/`, integración de Claude (`TvRoutes.kt`, `TvOrchardApp.kt`, bifurcación en `OrchardApp`, manifest leanback, `-PorchardTvBuild`, T14, Q1) y correcciones de QA. Contrato: `SDD-android-tv.md`. Método: revisión estática línea a línea y `grep`. Esta máquina no tiene Android SDK ni `kotlinc`: **no se compiló ni se ejecutó ningún test**.

#### 5.11.1 Defectos del código TV corregidos en QA (sin commit)

| # | Archivo | Defecto y efecto | Corrección |
| --- | --- | --- | --- |
| Q-1 | `focus/TvFocus.kt` y pantallas | `tvClickable` creaba dos nodos de foco (`clickable` + `focusable`), y `clip`/`background` antes del foco recortaban escala, borde y glow: doble pulsación y foco invisible (TV-INV-3) | `tvFocus` solo dibuja y precede a un único `clickable`/`focusable`; llamadas reordenadas; `onFocus` solo al ganar foco |
| Q-2 | `home/TvHomeScreen.kt` | `requestFocus()` sobre un `FocusRequester` sin adjuntar: crash al abrir Inicio | Adjunto a "Reproducir" del hero |
| Q-3 | `home/TvHomeScreen.kt` | `openItem(hero.item, onPlay, onPlayItem)` con tipos incompatibles (no compila); "Aleatorio" reproducía en orden | `onPlayItem(item, shuffle)` |
| Q-4 | 5 archivos | `androidx.compose.foundation.focusRestorer` no existe (no compila) | `androidx.compose.ui.focus.focusRestorer` |
| Q-5 | `player/TvLyricsPanel.kt` | `key = line.text`: crash con versos repetidos | Key por índice |
| Q-6 | `player/TvNowPlaying.kt` | Progreso y seek ±10 s leían `playback.positionMs`, que `playbackState` deja en 0; la barra no mostraba foco | `LocalPlayerClock` y `tvFocusable` |
| Q-7 | `player/TvNowPlaying.kt` | "Me gusta" = la pista está en la cola (siempre verdadero) | `library.likedTracks` |
| Q-8 | `player/TvNowPlaying.kt` | Panel Letras/Cola sin margen derecho (TV-INV-2) | Padding 48/27 dentro del panel |
| Q-9 | `search/TvSearchScreen.kt` | El mejor resultado mostraba la URL de la portada como subtítulo | `catalogSubtitle` |
| Q-10 | `library/TvLibraryScreen.kt` | "Recientes" abría el detalle con un id de pista | OK reproduce la pista |
| Q-11 | Reproductor, Detalle, Biblioteca | Sin foco inicial (TV-INV-3) | Play/Pausa, "Reproducir" y filtro activo |
| Q-12 | Inicio, Detalle, Reproductor | Fondos opacos tapaban el fondo ambiental | Fondos translúcidos |

#### 5.11.2 Invariantes (`SDD-android-tv.md` §3)

| Invariante | Resultado | Evidencia |
| --- | --- | --- |
| TV-INV-1 100 % D-pad | ⏳ parcial | Rail, Inicio, Buscar, Biblioteca, Detalle y Reproductor operables por foco. Ajustes es un placeholder enfocable; Login y Welcome no son alcanzables en TV (T11, D2); mantener OK, teclas multimedia globales y diálogos con foco en "No" pendientes (T10); sin smoke test (T12) |
| TV-INV-2 Zona segura | ⚠️ | Contenido en x = 92 dp y márgenes solo desde `TvTokens`. Observación para el PO: el rail que pide la Spec (x = 0 a 68 dp, ítems desde x = 10 dp) contradice "48 dp de los bordes laterales" |
| TV-INV-3 Foco | ✅ estático | Sin `onFocusChanged` ni `border` fuera de `ui/tv/focus/`; `focusRestorer` en estantes, listas y grilla. Pendiente (T10): restaurar el foco al volver de Detalle y placeholders enfocables mientras Inicio carga |
| TV-INV-4 Sin telemetría ni hosts | ✅ estático | `ui/tv/` sin URLs, sockets, `gms`, Firebase ni analítica; voz solo con `RecognizerIntent` al pulsar y botón oculto si nada lo resuelve; `RECORD_AUDIO` sigue con `tools:node="remove"`. Falta `:app:dependencies` |
| TV-INV-5 Gama baja | ⏳ | Kawarp con `renderScale = 0.5f` y `fullFrameRate = false`, apagado si `isLowRamDevice()`. APK TV con `armeabi-v7a` y `arm64-v8a`; CI falla si falta un `lib/<abi>/` y avisa si una librería está en una sola ABI. `vendor/litert` y `native/onnxruntime` solo traen `arm64-v8a`: en 32 bits la traducción de letras se desactiva sola (`TranslationNative.available`) y ORT usa la librería completa del AAR. Sin verificar (T0): `kopus` y Earmark en `armv7` |
| TV-INV-6 APK en el pipeline | ✅ estático | Segundo `assembleRelease` con `-PorchardTvBuild=true`, mismo keystore (se borra con `if: always()` tras el segundo build), un artefacto con los dos APK; `publish` exige `Orchard-AndroidTV-arm.apk`, lo suma a `SHA256SUMS.txt` y a las notas. Sin acciones ni permisos nuevos. Job `android-tv-smoke` (§5.5) no implementado |
| TV-INV-7 Teléfono intacto | ✅ estático | Sin la propiedad las ABI de release siguen en `['arm64-v8a']`; `OrchardApp` solo gana la bifurcación inicial. Diff dentro de §4.6 |
| TV-INV-8 Actualizador | ⚠️ | APK por nombre exacto y 6 tests nuevos en `UpdateManagerTest`. Teléfono: `Orchard-Android-arm64-v8a.apk` o el primer APK que no sea el de TV (conserva el test existente). **H-TV1:** si el APK sale de la lista de assets (este pipeline no publica `latest-android.json`), `sha256` queda vacío y `downloadAndInstallUpdate` no verifica; afecta también al canal beta del teléfono. Propuesta: usar el `digest` `sha256:` que la API de GitHub da por asset. Toca zona INV-3: requiere aprobación del propietario |
| TV-INV-9 Tamaño y cabeceras | ✅ | Máximo 396 líneas (`TvNowPlaying.kt`); SPDX AGPL-3.0-or-later en los 14 archivos de `ui/tv/` |

**Bloqueantes para llevar `integrate/hu-orch-11` a `audited`:** `testDebugUnitTest`, los dos `assembleRelease` y `:app:dependencies` en verde en Actions; decisión del PO sobre la traducción ausente en 32 bits y verificación de `kopus`/Earmark en `armv7` (T0); T10, T11 (D2), T12, T13 y Q2; H-TV1; constancia de la excepción a SDD §3.2 (Q4).

### 5.12 Paridad funcional Android TV (HU-ORCH-11b, 2026-10-19)

- **Base:** `68f72ad` más cambios sin commit en `integrate/hu-orch-11-tv-full`: 6 archivos modificados (`TvOrchardApp.kt`, `TvRoutes.kt`, `focus/TvFocus.kt`, `home/TvHomeScreen.kt`, `library/TvLibraryScreen.kt`, `player/TvNowPlaying.kt`) y 5 nuevos (`TvEmptyState.kt`, `TvSafeContent.kt`, `TvTrackActionsPopup.kt`, `TvListeningPartyScreen.kt`, `player/TvMusicVideoScreen.kt`). Método: revisión estática línea a línea, contraste de cada llamada con las firmas de `OrchardViewModel`, `ui/screens/*` y `social/ListeningPartyModels.kt`, y barrido mecánico de identificadores sin importar. Esta máquina no tiene Android SDK ni `kotlinc`: **no se compiló ni se ejecutó ningún test**.

#### 5.12.1 Defectos corregidos en QA (sin commit)

| # | Archivo | Defecto y efecto | Corrección |
| --- | --- | --- | --- |
| Q-13 | `TvTrackActionsPopup.kt` | Usa `TvTokens` sin importarlo (paquete `ui.tv.theme`): no compila | Import agregado |
| Q-14 | `TvOrchardApp.kt` | Usa `TvMusicVideoScreen` (paquete `ui.tv.player`) sin importarlo: no compila | Import agregado |
| Q-15 | `TvSafeContent.kt` | El rail solo colapsaba con `pointerInput`/toques, que no existen con D-pad: al entrar con foco a Ajustes, Logins, Welcome o Descargas el rail expandido quedaba encima (TV-INV-2) | Colapso al ganar foco cualquier descendiente con `Modifier.tvFocusWithin`, ubicado en `focus/TvFocus.kt` para cumplir el `git grep` de TV-INV-3 |
| Q-16 | `TvListeningPartyScreen.kt` | Sin márgenes: título, teclado y botones desde x = 0 dp, bajo el rail (TV-INV-2); sin `onContentFocus`, el rail no colapsaba | Contenido dentro de `TvSafeContent`; parámetro `onContentFocus` cableado |
| Q-17 | `player/TvMusicVideoScreen.kt` | Controles desde x = 48 dp, bajo el rail colapsado | Inicio en `TvTokens.ContentStartX` (92 dp) |
| Q-18 | `TvTrackActionsPopup.kt` | `requestFocus()` fuera del `Dialog` (corre antes de que la ventana componga) y, sin playlists, sobre un `FocusRequester` sin adjuntar: crash o diálogo sin foco (TV-INV-3) | `LaunchedEffect` dentro del `Dialog`; fila "Volver" siempre presente como primer foco del selector |
| Q-19 | `TvTrackActionsPopup.kt` | Elegir playlist no cerraba el popup | Cierra y luego agrega |
| Q-20 | `detail/TvCollectionScreen.kt` | Pulsación larga en una pista no abría acciones (Inicio y Reproductor sí) | `TrackRow` con `tvLongClickable` y `onTrackActions` |
| Q-21 | `library/TvLibraryScreen.kt` | Ídem en Canciones y Recientes | `TvSongList` y tarjetas de Recientes con `tvLongClickable`; playlists, álbumes y artistas sin acción larga (no son pistas) |
| Q-22 | `TvOrchardApp.kt`, `library/TvLibraryScreen.kt` | Ruta `DOWNLOADS` registrada pero inalcanzable; el filtro Descargas mostraba un texto sin foco | Filtro Descargas con `TvEmptyState` "Abrir descargas" que navega a `TvRoutes.DOWNLOADS`; foco inicial en esa acción |

#### 5.12.2 Verificación

| Punto | Resultado | Evidencia |
| --- | --- | --- |
| Sintaxis, tipos e imports | ✅ estático | Firmas contrastadas: `WelcomeScreen`, `NativeLoginScreen`, `SpotifyLoginScreen`, `QobuzLoginScreen`, `DownloadsScreen`, `SettingsScreen`, `SettingsHomeLayout`, `MusicVideoSurface`, `completeSignIn(..., pageAvatarUrl)`, `addTrackToPlaylist`, `playlistChoices`, `createListeningParty`/`joinListeningParty`/`leaveListeningParty`, `MusicVideoState.available`, `PartyState.code/isHost/isActive`, `Track.albumId/artistId`. Barrido de identificadores: solo falsos positivos (entradas de enum) |
| TV-INV-9 ≤ 500 líneas y SPDX | ✅ | Máximo 482 (`TvOrchardApp.kt`), luego 462 (`TvNowPlaying.kt`); SPDX AGPL-3.0-or-later en los 19 archivos de `ui/tv/` |
| Long-press → `onTrackActions` | ✅ estático | Inicio (canciones), Reproductor ("Más opciones"), Detalle (filas) y Biblioteca (Canciones, Recientes) |
| TV-INV-2 en pantallas integradas | ✅ estático | `TvSafeContent` (92/48/27 dp desde `TvTokens`) envuelve Welcome, Login, cambio de cuenta, Spotify, Qobuz, Descargas, Ajustes, Diseño de Inicio y Listening Party |
| TV-INV-3 grep | ✅ | Sin `onFocusChanged` ni `.border(` fuera de `ui/tv/focus/` |
| TV-INV-4 hosts y red | ✅ estático | `ui/tv/` sin URLs, sockets, `gms`, Firebase ni analítica |
| Compilación y tests | ❌ no ejecutado | Sin Android SDK local |

#### 5.12.3 Observaciones abiertas

- **O-1:** Las pantallas del teléfono embebidas no usan `tvFocus`: su foco no tiene el tratamiento TV (TV-INV-3) ni foco inicial garantizado. Requiere adaptación o pantallas TV propias (T11).
- **O-2:** `NavHost` registra su `BackHandler` después del de `TvOrchardApp`, así que fuera de los destinos raíz "Atrás" desapila la pantalla en vez de colapsar primero el rail. Preexistente.
- **O-3:** `TvSearchScreen` no tiene pulsación larga sobre resultados de canción; fuera del alcance pedido.
- **O-4:** `TvMusicVideoScreen` recibe `musicVideo` y no lo usa (warning de compilación).

**Bloqueantes para llevar `integrate/hu-orch-11-tv-full` a `audited`:** `testDebugUnitTest` y los dos `assembleRelease` en verde en Actions; prueba en el Philips del cliente de Login, Ajustes, popup de acciones (pulsación larga del botón central) y Listening Party; los bloqueantes de §5.11 que siguen abiertos.
