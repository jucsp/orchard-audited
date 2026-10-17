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

### HU-ORCH-03: Actualizador con Confirmación del Usuario
- **ID:** HU-ORCH-03
- **Prioridad:** Alta
- **Estimación:** 3 SP
- **Descripción:** Reemplazar el flujo de actualización automática silenciosa por una consulta simple al usuario: comprobar si existe una nueva versión disponible en el repositorio del fork y, de haberla, preguntar al usuario si desea actualizar o no. Eliminar descargas e instalaciones automáticas sin consentimiento, sin requerir doble confirmación ni validación de hash adicional en la aplicación.
- **Criterios de Aceptación:**
  - Notificación al detectar una nueva versión en el repositorio consultando al usuario si desea actualizar o no.
  - Cero descargas o actualizaciones si el usuario rechaza la actualización.

### HU-ORCH-04: Workflow Monitor del Upstream (Solo Alerta)
- **ID:** HU-ORCH-04
- **Prioridad:** Media
- **Estimación:** 2 SP
- **Descripción:** Implementar workflow de GitHub Actions programado cada 6–12 h que consulte releases/tags de `sfg5453/orchard`. Ante nueva versión, abrir un issue en el fork con compare y checklist pendiente de auditoría (REQ-M1 a M3). Sin descargas ni builds automáticos (REQ-M2).
- **Criterios de Aceptación:**
  - Workflow no realiza builds ni commits directos.
  - Alerta especial si cambian `TrustedKeys.cpp` o archivos del updater en upstream.

### HU-ORCH-05: Pipeline Automatizado de Build, Release y Empaquetado RPM
- **ID:** HU-ORCH-05
- **Prioridad:** Alta
- **Estimación:** 5 SP
- **Descripción:** Replicar workflows de CI/CD para compilar y generar releases automáticamente en el fork (Linux AppImage, Windows) y agregar empaquetado `.rpm` para Fedora/KDE (REQ-A2, REQ-A10). Firma del RPM y generación de manifiestos firmados. Disparo 100% automatizado cada vez que se haga push/merge a la rama principal del fork (`main` o `audited`). La única acción manual será la decisión del usuario en la interfaz de la aplicación de descargar e instalar.
- **Criterios de Aceptación:**
  - Publicación automatizada en GitHub Releases del fork ante push en `main` / `audited`.
  - Generación de artefactos AppImage, Windows y `.rpm`.
  - RPM instalable y verificado en Fedora.

### HU-ORCH-06: Desacople de Servicios de Terceros y Soberanía Local
- **ID:** HU-ORCH-06
- **Prioridad:** Alta
- **Estimación:** 3 SP
- **Descripción:** Purgar el proveedor innecesario `providers/apple-music/` eliminando dependencias de tokens de Apple. Desconectar la traducción en la nube con Cloudflare AI (`/lyrics/translate`) y fijar la traducción nativa con Gemini (BYOK con clave personal del usuario en `generativelanguage.googleapis.com`), desactivada por defecto. Preservar Orchard Connect (Cloudflare Worker) con soporte de URL parametrizable para autoalojamiento personal (`ORCHARD_ACCOUNT_URL`).
- **Criterios de Aceptación:**
  - Eliminado el directorio `providers/apple-music/` y referencias en compilación.
  - Traducción por defecto desactivada y exclusivamente vía API Key de Gemini del usuario.
  - Cero llamadas a `account.sfg545.dev/lyrics/translate`.

### HU-ORCH-11: Soporte y Compilación para Android TV (D-Pad & Leanback)
- **ID:** HU-ORCH-11
- **Prioridad:** Alta
- **Estimación:** 8 SP
- **Descripción:** Adaptar la versión móvil de Android para ejecución en Android TV / Google TV. Habilitar `android.software.leanback` en el manifiesto, diseñar el banner para el lanzador de TV y adaptar la navegación mediante control remoto (D-Pad focus traversal). Generar el APK optimizado para TV en el pipeline de GitHub Actions.
- **Criterios de Aceptación:**
  - APK instalable y visible en el lanzador de Android TV.
  - Navegación fluida y foco visible con D-Pad (flechas del mando a distancia).
  - Reproducción continua en TV sincronizada con Orchard Connect.

### HU-ORCH-12: Re-implementación de Soporte para Chromecast
- **ID:** HU-ORCH-12
- **Prioridad:** Alta
- **Estimación:** 5 SP (fase 1, Android). Fase 2 (escritorio) se estima al abrirla.
- **Contrato:** `SDD-chromecast-castv2.md` (invariantes `CAST-INV-1` a `CAST-INV-9`). Decisión del propietario: Opción B aprobada el 2026-10-17 (`AUDIT.md` §5.10, C1).
- **Descripción:** Transmitir audio a Chromecast, Google TV y parlantes Google Home/Nest (incluidos grupos) con un cliente CastV2 propio: descubrimiento mDNS `_googlecast._tcp`, TLS al puerto anunciado, `CastMessage` codificado sin librería de protobuf, Default Media Receiver `CC1AD845` y `ChromecastStreamServer` local. Sin Google Play Services (`media3-cast` se retira), sin App ID registrado, sin costos y sin servidores externos. Nada se activa hasta que el usuario busca dispositivos.
- **Criterios de Aceptación:**
  - Los de `SDD-chromecast-castv2.md` §6.
  - Árbol de dependencias de release sin `gms`, `play-services` ni `mediarouter`.
  - Descubrimiento, conexión, reproducción, pausa, seek, volumen del dispositivo y avance de cola en un Chromecast/Google TV y un Nest Mini reales.
  - Sin tráfico de cast al arrancar la app (H15).

