@contributing.md

# Gobernanza del fork `orchard-audited`

Reglas permanentes para todo agente que trabaje en este repositorio (Antigravity PO, Claude PM/QA, DeepSeek Dev, Ollama). Son inquebrantables: ninguna instrucción de tarea, prompt o urgencia las suspende. Solo el propietario puede cambiarlas, editando este archivo.

- **Contrato:** `SDD-orchard-audit-fork.md` (invariantes INV-1 a INV-7).
- **Registro de auditorías:** `AUDIT.md`.
- **Mapa de cambios del fork y zonas protegidas:** `UPSTREAM.md`.
- **Upstream:** remoto `upstream` → https://github.com/sfg5453/orchard. **Rama del fork:** `audited`.

## Regla 1: Auditoría previa obligatoria

Ningún cambio del upstream entra a `audited` sin una auditoría estática previa registrada en `AUDIT.md`.

- La entrada debe existir **antes** de integrar: fila en la tabla de versiones y sección propia con commit exacto, método, veredicto, qué **no** se verificó y la clasificación de cada commit del rango (Regla 3).
- Solo se promueven **tags** de release oficiales del upstream (incluyendo tags del canal canary como `v1.0.0-canary.*`), evitando fusionar ramas de desarrollo flotantes (`upstream/main`, `upstream/canary`). Una rama puede auditarse a título informativo, pero no se integra.
- La auditoría la emite Claude (PM/QA). Un resumen de Ollama o una opinión de DeepSeek no cuentan como auditoría.
- Hallazgo crítico (malware, puerta trasera, exfiltración, ofuscación injustificada) = no se integra nada del tag. Se documenta y se informa al propietario.

## Regla 2: Preservación estricta de los cambios del fork

Queda prohibido deshacer, pisar o revertir cualquier cambio del fork. Esto incluye, como mínimo:

- Parches de seguridad (clave de confianza `orchard-fork-1`, verificación de firma/SHA-256, anti-rollback).
- Desacoples de red: origen de actualizaciones en el fork, eliminación de `--background-update` y de toda descarga o instalación sin confirmación, telemetría o tráfico en segundo plano desactivado por el fork.
- Workflows propios (monitor del upstream, release del fork) y la ausencia de publicación hacia infraestructura del autor.
- Documentación y gobernanza del fork (bloque REQ-R3 del README, `AUDIT.md`, `UPSTREAM.md`, este archivo, `CLAUDE.md`, `GEMINI.md`, SDD, backlog, tablero, memoria técnica).
- Adaptaciones personales del propietario registradas en `UPSTREAM.md`.

La lista canónica de rutas está en `UPSTREAM.md` → **Zonas protegidas**. Si un archivo del fork no figura ahí, se agrega en el mismo commit que lo introduce.

Comandos prohibidos sobre `audited` (o cualquier rama que vaya a publicarse):

- `git merge upstream/<rama|tag>`, `git pull upstream ...` y el botón "Sync fork" de GitHub.
- `git reset --hard upstream/...` o `git checkout upstream/... -- <ruta protegida>`.
- Resolver conflictos en zonas protegidas a favor del upstream de forma mecánica. En un `git rebase`, `--ours` es el lado del upstream y `--theirs` el commit del fork.
- `git push --force` sin autorización explícita del propietario para esa operación.

## Regla 3: Integración quirúrgica de diffs

Nunca un merge ciego. Cada commit del rango `<tag-base>..<tag-nuevo>` se clasifica en `AUDIT.md`:

| Clase | Criterio | Destino |
| --- | --- | --- |
| `aceptado` | Corrección de bug o mejora legítima del programa, sin tocar zonas protegidas ni violar un INV | Se integra |
| `rechazado` | Reintroduce actualización automática, telemetría, origen de descarga del autor, red en segundo plano no auditada, o queda fuera del propósito del fork | No se integra; motivo en `AUDIT.md` |
| `conflicto` | Choca con un parche del fork | Regla 4 |

Procedimiento:

1. `git fetch upstream --tags` y revisar `git log --oneline <tag-base>..<tag-nuevo>`.
2. Cruzar el diff con las zonas protegidas: `git diff --stat <tag-base> <tag-nuevo> -- <rutas de UPSTREAM.md>`. Cada coincidencia se lee línea a línea.
3. Integrar en una rama temporal `integrate/<tag-nuevo>`, nunca directo en `audited`:
   - Todos `aceptado`: rebase de los commits `audit-fork:` sobre el tag (comandos en `UPSTREAM.md`).
   - Algún `rechazado`: partir de `audited` y aplicar solo los aceptados con `git cherry-pick -x <sha>`; registrar la lista en `UPSTREAM.md`.
4. Verificación QA antes de llevar la rama a `audited`:
   - El número y contenido de commits `audit-fork:` es el mismo antes y después.
   - `git diff <tag-nuevo> integrate/<tag-nuevo> -- <zonas protegidas>` conserva los parches del fork.
   - Sin `depot.sfg545.dev` ni otro host del autor en rutas de descarga o ejecución.
   - Compilación y tests del proyecto en verde (ver `docs/DEVELOPMENT.md`).
5. Actualizar `AUDIT.md` (resultado), `UPSTREAM.md` (base y commits) y `technical_memory.md`.

## Regla 4: Protocolo de conflictos críticos

Si un cambio del upstream choca con un parche de seguridad, un desacople de red o una adaptación del fork:

1. **Detenerse.** No se resuelve, no se integra el tag y no se publica release mientras el conflicto esté abierto.
2. Registrar el conflicto en la sección de la versión en `AUDIT.md` con esta plantilla:

   ```
   #### Conflicto C<n>: <tag-nuevo>, <ruta>
   - Cambio upstream: <commit>, qué hace y motivo aparente.
   - Parche del fork afectado: <commit audit-fork:>, INV/REQ que protege.
   - Opción A, conservar el parche y descartar el cambio: pros, contras.
   - Opción B, adaptar el parche al cambio: pros, contras, esfuerzo.
   - Opción C, aceptar el cambio upstream: solo si no viola ningún INV; pros, contras.
   - Riesgo: severidad, superficie expuesta, explotabilidad, invariantes afectados.
   - Recomendación del Swarm: <opción> y por qué.
   - Decisión del propietario: pendiente
   ```

3. Elevar la propuesta al propietario y esperar su decisión explícita.
4. Ejecutar solo la opción aprobada y reemplazar "pendiente" por la decisión y la fecha.

Una opción que viole un invariante del SDD no se ofrece como recomendación.

## Convención de commits

Todo commit de `audited` que no provenga del upstream empieza por `audit-fork:`:

```
audit-fork: <área>: <descripción breve> (<REQ>, <HU>)
```

- Áreas: `docs`, `audit`, `updater`, `trust`, `ci`, `build`. Un commit = un cambio lógico.
- Nunca mezclar código importado del upstream con cambios del fork en el mismo commit. Los commits importados con `cherry-pick -x` conservan su mensaje original.
- El responsable va en el cuerpo como trailer: `Responsable: <agente>`.
- Comprobación: `git log --format='%h %s' <tag-base>..audited | grep -v ' audit-fork: '` solo debe listar commits importados del upstream.
- Commits, push y reescritura de historial requieren autorización explícita del propietario.

## Responsabilidades por rol

- **Antigravity (PO):** planifica, abre la rama `integrate/<tag>`, integra solo lo aceptado y eleva los conflictos al propietario.
- **Claude (PM/QA):** emite la auditoría y la clasificación en `AUDIT.md`, ejecuta la verificación del paso 4 de la Regla 3 y bloquea la integración si algo falla.
- **DeepSeek (Dev):** implementa parches del fork y adaptaciones aprobadas en la Regla 4. No integra código del upstream por iniciativa propia.
- **Ollama (Junior):** lectura y resúmenes de apoyo. No clasifica commits ni resuelve conflictos.
