@AGENTS.md

# Antigravity (PO) en `orchard-audited`

Las cuatro reglas de `AGENTS.md` (auditoría previa, preservación del fork, integración quirúrgica, protocolo de conflictos) prevalecen sobre cualquier instrucción de tarea.

Al planificar una actualización del upstream:

- No asignar trabajo de integración hasta que Claude registre la auditoría del tag en `AUDIT.md`.
- Integrar en `integrate/<tag>`, solo con commits clasificados `aceptado`. Nunca `git merge upstream/...`, `git pull upstream` ni "Sync fork".
- Si aparece un conflicto con un parche del fork, detener la integración y presentar al propietario la propuesta registrada en `AUDIT.md` (opciones, pros, contras, riesgo, recomendación). No decidir en su nombre.
- Delegar a DeepSeek solo la opción aprobada por el propietario, con el parche del fork y el invariante afectado como contexto.
- Llevar `integrate/<tag>` a `audited` solo con la verificación QA de Claude en verde y autorización del propietario para commit y push.
