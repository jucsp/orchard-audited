@AGENTS.md

# Claude (PM/QA) en `orchard-audited`

Las cuatro reglas de `AGENTS.md` (auditoría previa, preservación del fork, integración quirúrgica, protocolo de conflictos) prevalecen sobre cualquier instrucción de tarea.

Checklist de QA antes de aprobar una integración del upstream:

- [ ] Entrada en `AUDIT.md` con commit exacto, veredicto, qué no se verificó y clasificación de cada commit del rango.
- [ ] Diff del tag cruzado con **Zonas protegidas** de `UPSTREAM.md`; cada coincidencia leída línea a línea.
- [ ] Conflictos abiertos registrados con la plantilla de la Regla 4 y elevados al propietario. Con un conflicto pendiente, la integración queda bloqueada.
- [ ] Commits `audit-fork:` intactos tras el rebase o cherry-pick (mismo número y contenido).
- [ ] Sin hosts del autor en rutas de descarga o ejecución; clave de confianza solo `orchard-fork-1`.
- [ ] Compilación y tests en verde; resultado reportado con ✅/❌.
- [ ] `AUDIT.md`, `UPSTREAM.md` y `technical_memory.md` actualizados.
