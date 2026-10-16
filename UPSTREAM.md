# UPSTREAM.md — Diferencias con sfg5453/orchard

Registro de **todos** los cambios del fork respecto del upstream (INV-7). Cada cambio vive en un commit cuyo mensaje empieza por `audit-fork:`, para que al promover una versión nueva (SDD §7) se puedan reaplicar sobre el tag nuevo con un rebase limpio.

- **Upstream:** https://github.com/sfg5453/orchard (remoto `upstream`)
- **Base actual:** tag `v1.0.0-canary.9` → `aa900002d0541ce6bd7beab8d6011a05d181daaa`
- **Rama del fork:** `audited`
- **Licencia:** AGPL-3.0-or-later con el permiso adicional de `LICENSE-EXCEPTION.md`. Los archivos modificados conservan su licencia y avisos de copyright originales (SFG545 y Julian-FF2000); los archivos nuevos del fork se publican bajo AGPL-3.0-or-later. El fork **no modifica** archivos con cabecera OOL-1.0 (ver L1 en `AUDIT.md`).

## Convención de commits

```
audit-fork: <área>: <descripción breve> (<REQ>, <HU>)
```

Áreas: `docs`, `updater`, `trust`, `ci`, `build`. Un commit = un cambio lógico; sin mezclar código del upstream con cambios del fork.

## Rebase sobre un tag nuevo

```sh
git fetch upstream --tags
git log --oneline --grep '^audit-fork:' v1.0.0-canary.9..audited
git rebase --onto <tag-nuevo> v1.0.0-canary.9 audited
```

Tras el rebase, actualizar la base de este archivo y la tabla de `AUDIT.md`. Solo se ejecuta tras aprobar la auditoría de `<tag-nuevo>`.

## Commits aplicados

| # | Commit | Área | Descripción | REQ | HU | Archivos |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `1301e8f` | docs | Contrato SDD, backlog y tablero del Swarm (**sin prefijo** `audit-fork:`; renombrar antes de publicar) | — | — | `SDD-orchard-audit-fork.md`, `backlog.md`, `sprint_board.md`, `technical_memory.md` |
| 2 | _pendiente_ | docs | Aviso de fork en README, informe base `AUDIT.md`, este `UPSTREAM.md` | REQ-R3, R4, R5 | HU-ORCH-01 | `README.md`, `AUDIT.md`, `UPSTREAM.md` |

## Cambios previstos

| Área | Cambio | REQ | HU | Archivos previstos |
| --- | --- | --- | --- | --- |
| trust | Sustituir `orchard-release-1` por la clave pública `orchard-fork-1` (ECDSA P-256) | REQ-U1 | HU-ORCH-02 | `bootstrapper/src/security/TrustedKeys.cpp` |
| updater | `kDefaultServer` hacia GitHub Releases/Pages del fork; formato `orchard-signed-v1` sin cambios | REQ-U2, U8 | HU-ORCH-02 | `bootstrapper/src/update/ReleaseClient.hpp` |
| updater | Quitar el lanzamiento automático de `--background-update`; comprobación solo de metadatos al iniciar | REQ-U3, U5 | HU-ORCH-03 | `bootstrapper/src/app/Bootstrapper.cpp`, `bootstrapper/src/update/UpdateManager.cpp` |
| updater | Diálogo Sí/No y luego Instalar/Más tarde, con versión, notas, host de origen y enlace a `AUDIT.md`; silencio de 7 días | REQ-U4, U8 | HU-ORCH-03 | `app/src/update/bootstrapper_client.cpp`, QML de ajustes de actualización |
| updater | Canal único auditado; desactivar el cambio a canary y el rollback por cambio de canal | REQ-U7 | HU-ORCH-03 | `app/src/update/*`, `bootstrapper/src/update/*` |
| updater | Verificación de firma, hash de manifiesto y SHA-256 por archivo; anti-rollback por `sequence` | REQ-U6 | HU-ORCH-03 | `bootstrapper/src/update/*`, `bootstrapper/src/security/*` |
| updater | Tests negativos: descarga sin confirmación, firma inválida, hash alterado, versión ≤ instalada | REQ-U5, U6 | HU-ORCH-03 | `tests/` |
| docs | Ajustar la guía de actualizaciones al flujo con confirmación | REQ-U4 | HU-ORCH-03 | `docs/app/updates.md`, `docs/BOOTSTRAPPER.md` |
| ci | Monitor del upstream que solo abre issues; alerta alta si cambian `TrustedKeys.cpp` o `bootstrapper/src/update/` | REQ-M1–M4 | HU-ORCH-04 | `.github/workflows/monitor-upstream.yml` |
| ci | Pipeline de release del fork (dispatch o tag `-audited`, environment `release`, acciones por SHA, destino Releases del fork) en lugar de la publicación a R2 | REQ-A1–A9 | HU-ORCH-05 | `.github/workflows/*`, `scripts/release/` |
| build | Spec RPM firmado con GPG del fork y validación en contenedor Fedora | REQ-A10 | HU-ORCH-05 | `packaging/rpm/` (nuevo) |
| updater | Updater Android hacia el fork, sujeto a la decisión de SDD §9 | REQ-U3–U8 | — | `mobile/android/app/src/main/java/dev/sfg/orchard/mobile/UpdateManager.kt` |
