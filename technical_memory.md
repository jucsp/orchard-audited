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
- `bootstrapper/src/security/TrustedKeys.cpp`: Reemplazo de la clave ECDSA P-256 de confianza por la clave pública del fork.
- Configuración de URLs de descarga en el bootstrapper y app para apuntar a GitHub Releases del fork.
- `.github/workflows/`: Workflows para el monitor de upstream (alerta) y el release controlado (con soporte RPM).
