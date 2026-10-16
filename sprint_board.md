# Sprint Board — Orchard Audited Fork

**Sprint Goal:** Establecer la línea base auditada en el fork `orchard-audited`, desmantelar el actualizador no consentido hacia `depot.sfg545.dev` reemplazándolo por clave y repositorio propio, documentar auditoría y habilitar el pipeline controlado con soporte RPM.

---

## 📋 To Do
- [ ] **HU-ORCH-01: Documentación y Trazabilidad del Fork**
  - [Responsable: Claude PM/QA] Redactar bloque obligatorio en `README.md`, estructurar `AUDIT.md` para `1.0.0-canary.9` y preparar `UPSTREAM.md`.

- [ ] **HU-ORCH-02: Generación de Claves Propias y Desconexión Upstream**
  - [Responsable: DeepSeek Dev] Generación de par ECDSA P-256, parche en `bootstrapper/src/security/TrustedKeys.cpp`, redirección de endpoints al fork.
  - [Responsable: Claude PM/QA] Auditoría de cadenas en código fuente (`depot.sfg545.dev`), verificación de envelope `orchard-signed-v1`.

- [ ] **HU-ORCH-03: Actualizador con Confirmación Explícita**
  - [Responsable: DeepSeek Dev] Implementación de máquina de estados del updater (comprobación -> diálogo descarga -> verificación firma/sha256 -> diálogo instalación).
  - [Responsable: Claude PM/QA] Tests negativos de descarga no autorizada y firma corrupta.

- [ ] **HU-ORCH-04: Workflow Monitor del Upstream**
  - [Responsable: Claude PM/QA] Crear `.github/workflows/monitor-upstream.yml` fijado por SHA, permisos mínimos y creación de issue con diff.

- [ ] **HU-ORCH-05: Pipeline de Build, Release y Empaquetado RPM**
  - [Responsable: Claude PM/QA] Replicar workflows para AppImage y Windows en `.github/workflows/release.yml`.
  - [Responsable: DeepSeek Dev] Spec RPM para Fedora/KDE e integración de firma GPG.

---

## 🏗️ In Progress

---

## 🧪 In Review / QA

---

## ✅ Done
- [x] Fork creado en GitHub (`jucsp/orchard-audited`) `[Responsable: Antigravity PO]`
- [x] Rama por defecto `audited` fijada en tag `v1.0.0-canary.9` `[Responsable: Antigravity PO]`
- [x] Repositorio clonado localmente y remoto upstream vinculado `[Responsable: Antigravity PO]`
- [x] Contrato SDD importado y Product Backlog inicializado `[Responsable: Antigravity PO]`
- [x] **HU-ORCH-01: Documentación y Trazabilidad del Fork** `[Responsable: Claude PM/QA]` (README con REQ-R3, AUDIT.md y UPSTREAM.md)
