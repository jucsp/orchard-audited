# Sprint Board — Orchard Audited Fork

**Sprint Goal:** Establecer la línea base auditada en el fork `orchard-audited`, desmantelar el actualizador no consentido hacia `depot.sfg545.dev` reemplazándolo por clave y repositorio propio, documentar auditoría y habilitar el pipeline controlado con soporte RPM.

---

## 📋 To Do
- [ ] **HU-ORCH-03: Actualizador con Confirmación Explícita**
  - [Responsable: DeepSeek Dev] Implementación de máquina de estados del updater (comprobación -> diálogo descarga -> verificación firma/sha256 -> diálogo instalación).
  - [Responsable: Claude PM/QA] Tests negativos de descarga no autorizada y firma corrupta.

- [ ] **HU-ORCH-04: Workflow Monitor del Upstream**
  - [Responsable: Claude PM/QA] Crear `.github/workflows/monitor-upstream.yml` fijado por SHA, permisos mínimos y creación de issue con diff.

---

## 🏗️ In Progress
_(vacío)_

---

## 🧪 In Review / QA
- [ ] **HU-ORCH-05: Pipeline de Build, Release Automatizado y Empaquetado RPM** `[Responsable: DeepSeek Dev (backup de Claude PM/QA)]`
  - Hecho: `.github/workflows/release.yml` (Linux AppImage + RPM, prueba de instalación en Fedora, Windows 7z + setup, `SHA256SUMS.txt`, publicación en GitHub Releases del fork), `scripts/package-linux-rpm.sh`, `packaging/rpm/orchard.spec`. Eliminado `manual-desktop-build.yml` (publicaba en R2).
  - Backup de rol: Claude (PM/QA) alcanzó su rate limit (resets 6:20pm); DeepSeek Dev asumió las funciones de PM/QA para implementar y verificar esta HU.
  - Publicación directa: el job `publish` corre sin variable ni *environment* de aprobación cuando `github.repository == 'jucsp/orchard-audited'`.
  - Pendiente: primera ejecución en Actions, firma GPG del RPM y manifiestos firmados compatibles con el bootstrapper (REQ-A9, A10).

- [ ] **HU-ORCH-06: Soberanía de Servicios y Orchard Connect Propio** `[Responsable: DeepSeek Dev]`
  - Hecho (código): `services/account/wrangler.production.jsonc` para `orchard-account` en `connect.kasper-cloud.com`; cuenta/Connect en escritorio y Android apuntan al dominio propio; auto-update off por defecto y sin sondeo periódico; `artwork.sfg545.dev` y `lastfm.sfg545.dev` neutralizados en escritorio y Android; `providers/apple-music/` no existe en este tag (N/A).
  - Pendiente de ejecución por shell (no disponible en este agente): migraciones remotas de D1, carga de secrets `GOOGLE_CLIENT_SECRET` y `SIGNING_KEY`, y `wrangler deploy`.
  - Fuera de alcance de esta HU (anotado): `party.sfg545.dev` (`ListeningPartyClient.kt`) y enlaces informativos `sfg545.dev/orchard`.

---

## ✅ Done
- [x] Fork creado en GitHub (`jucsp/orchard-audited`) `[Responsable: Antigravity PO]`
- [x] Rama por defecto `audited` fijada en tag `v1.0.0-canary.9` `[Responsable: Antigravity PO]`
- [x] Repositorio clonado localmente y remoto upstream vinculado `[Responsable: Antigravity PO]`
- [x] Contrato SDD importado y Product Backlog inicializado `[Responsable: Antigravity PO]`
- [x] **HU-ORCH-01: Documentación y Trazabilidad del Fork** `[Responsable: Claude PM/QA]` (README con REQ-R3, AUDIT.md y UPSTREAM.md)
- [x] **HU-ORCH-02: Generación de Claves Propias y Desconexión Upstream** `[Responsable: DeepSeek Dev]` (clave `orchard-fork-1`, servidor del fork y fin del auto-update en segundo plano)
- [x] **Gobernanza y Reglas del Swarm:** `AGENTS.md`, `CLAUDE.md`, `GEMINI.md` establecidos y versionados `[Responsable: Claude PM/QA]`
