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

## 4. Estado de auditoría
- **Base integrada:** tag `v1.0.0-canary.9` (`aa900002d054`). Informe en `AUDIT.md` §1.
- **`upstream/main` `3c8acc1e1e39`** (30 commits sobre el tag): revisión estática sin malware, no promovida por ser rama y porque el updater sigue en `depot.sfg545.dev` (`AUDIT.md` §4). `bootstrapper/` sin cambios en ese rango.
- **Licencia del motor adaptativo:** OOL-1.0 en el tag (bloqueo L1); `upstream/main` lo relicencia a PolyForm Shield 1.0.0, lo que desbloquea L1 al promover un tag que lo incluya, conservando el `Required Notice`.
- **HU-ORCH-02 aplicada:** clave `orchard-fork-1`, servidor del fork y fin del auto-update en segundo plano. `grep` sin `depot.sfg545.dev`/`downloads.sfg545.dev` en `bootstrapper/` y `mobile/`.

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
