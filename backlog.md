# Product Backlog — Orchard Audited Fork

## Visión del Producto
Fork de auditoría de seguridad sobre `sfg5453/orchard` para mantener un control absoluto del código, deshabilitar actualizaciones automáticas sin consentimiento explícito del usuario y redirigir todo el ciclo de actualización hacia el repositorio auditado del fork en lugar de los servidores del autor original (`depot.sfg545.dev`).

---

## Historias de Usuario

### HU-ORCH-01: Documentación y Trazabilidad del Fork
- **ID:** HU-ORCH-01
- **Prioridad:** Alta
- **Estimación:** 2 SP
- **Descripción:** Adecuar `README.md` con el bloque explícito de aviso de auditoría (REQ-R3), crear `AUDIT.md` con el reporte base de `1.0.0-canary.9` (REQ-R4), y crear `UPSTREAM.md` para mapear los commits del fork con prefijo `audit-fork:` (REQ-R5). Conservar licencias y atribuciones originales.
- **Criterios de Aceptación:**
  - Bloque aclaratorio en README visible en la raíz.
  - `AUDIT.md` documenta la versión auditada actual, métodos y limitaciones conocidas.
  - `UPSTREAM.md` preparado para trazabilidad de rebases.

### HU-ORCH-02: Generación de Claves Propias y Desconexión del Servidor Upstream
- **ID:** HU-ORCH-02
- **Prioridad:** Alta
- **Estimación:** 3 SP
- **Descripción:** Generar par de claves ECDSA P-256 para el fork (`orchard-fork-1`). Reemplazar la clave pública de confianza en `bootstrapper/src/security/TrustedKeys.cpp` (REQ-U1). Desconectar todas las referencias a `depot.sfg545.dev` y apuntar el resolvedor de canal/manifiestos al fork (GitHub Releases) manteniendo el envelope `orchard-signed-v1` (REQ-U2).
- **Criterios de Aceptación:**
  - `grep` en el fork no encuentra referencias de descarga a `depot.sfg545.dev` (REQ-U2).
  - La clave privada nunca entra al repositorio (INV-5).
  - Verificación de envelope `orchard-signed-v1` con la clave del fork.

### HU-ORCH-03: Actualizador con Confirmación Explícita (2 Pasos)
- **ID:** HU-ORCH-03
- **Prioridad:** Alta
- **Estimación:** 5 SP
- **Descripción:** Reemplazar el flujo de actualización automática por un flujo de dos pasos con diálogo: Comprobación solo de metadatos al abrir (REQ-U3). Si hay nueva versión aprobada en el fork, diálogo con "¿Descargar ahora?" (REQ-U4). Tras descarga y verificación de firma/hash (REQ-U6), segundo diálogo "¿Instalar ahora?". Eliminar descargas en segundo plano y auto-instalación (REQ-U5). Test negativo automatizado (REQ-U5).
- **Criterios de Aceptación:**
  - Cero descargas si el usuario selecciona "No".
  - Rechazo de paquetes alterados o con versión menor/igual (anti-rollback).
  - Test unitario/suite que falle si existe ruta de descarga sin confirmación.

### HU-ORCH-04: Workflow Monitor del Upstream (Solo Alerta)
- **ID:** HU-ORCH-04
- **Prioridad:** Media
- **Estimación:** 2 SP
- **Descripción:** Implementar workflow de GitHub Actions programado cada 6–12 h que consulte releases/tags de `sfg5453/orchard`. Ante nueva versión, abrir un issue en el fork con compare y checklist pendiente de auditoría (REQ-M1 a M3). Sin descargas ni builds automáticos (REQ-M2).
- **Criterios de Aceptación:**
  - Workflow no realiza builds ni commits directos.
  - Alerta especial si cambian `TrustedKeys.cpp` o archivos del updater en upstream.

### HU-ORCH-05: Pipeline de Build, Release y Empaquetado RPM
- **ID:** HU-ORCH-05
- **Prioridad:** Alta
- **Estimación:** 5 SP
- **Descripción:** Replicar workflows de CI/CD para generar releases en el fork (Linux AppImage, Windows, Linux ARM64) y agregar empaquetado `.rpm` para Fedora/KDE (REQ-A2, REQ-A10). Firma GPG del RPM y entrega al instalador del sistema (`pkexec dnf`). Disparo estrictamente controlado por `workflow_dispatch` o tag `-audited` con environment `release` (REQ-A4).
- **Criterios de Aceptación:**
  - Publicación exclusiva en releases del fork.
  - Secretos de firma resguardados en environment con aprobación manual.
  - RPM instalable y verificado en Fedora.
