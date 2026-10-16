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
| `bootstrapper/src/security/TrustedKeys.cpp` | Solo la clave `orchard-fork-1` es de confianza | INV-3, REQ-U1 | _prevista_ HU-ORCH-02 |
| `bootstrapper/src/update/` | Origen en el fork, verificación firma/SHA-256, anti-rollback, sin descarga sin confirmación | INV-1–4, REQ-U2–U8 | _prevista_ HU-ORCH-02/03 |
| `bootstrapper/src/app/Bootstrapper.cpp` | Sin lanzamiento automático de `--background-update` | INV-1, REQ-U5 | _prevista_ HU-ORCH-03 |
| `app/src/update/`, QML de ajustes de actualización | Diálogos Sí/No e Instalar/Más tarde, canal único | INV-1–2, REQ-U4, U7 | _prevista_ HU-ORCH-03 |
| `mobile/android/app/src/main/java/dev/sfg/orchard/mobile/UpdateManager.kt` | Updater Android hacia el fork | INV-3 | _prevista_ SDD §9 |
| Telemetría o red en segundo plano desactivada por el fork (H7) | Valores por defecto del fork | INV-6 | _prevista_ |
| `.github/workflows/` | Monitor solo alerta, release solo hacia el fork, acciones por SHA | REQ-M1–M4, REQ-A1–A10 | _prevista_ HU-ORCH-04/05 |
| `scripts/release/`, `packaging/rpm/` | Publicación y firma del fork | INV-5, REQ-A3, A10 | _prevista_ HU-ORCH-05 |
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
| 6 | _pendiente_ | docs | Gobernanza de actualizaciones del upstream | INV-7, REQ-R5 | — | `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `UPSTREAM.md`, `technical_memory.md` | — |

Los commits marcados ❌/⚠️ no cumplen la convención. Corregirlos implica reescribir historial publicado (`git rebase` con reword y push forzado), así que queda a decisión del propietario; hasta entonces, `git log --grep '^audit-fork:'` no los lista y deben revisarse a mano en cada rebase.

## Commits importados del upstream

Commits del upstream integrados por `cherry-pick -x` fuera de un tag completo. Vacío mientras la base sea un tag sin selección.

| Commit upstream | Commit en `audited` | Tag de origen | Clasificación en `AUDIT.md` |
| --- | --- | --- | --- |

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
