# Contrato SDD — Fork de auditoría de Orchard

- **Versión del contrato:** 1.0 · 2026-10-15
- **Propietario:** juanca (GitHub personal)
- **Upstream:** https://github.com/sfg5453/orchard
- **Línea base auditada:** `1.0.0-canary.9` (tag correspondiente en upstream). El fork parte del **tag auditado**, no de `main`.
- **Ejecutor:** equipo Swarm local. **Revisor/auditor:** Claude (sesión de auditoría), bajo solicitud del propietario.

---

## 1. Intención (lo único que este fork es)

Este fork existe para **mantener una auditoría de seguridad controlada** sobre Orchard y **desconectar toda automatización de actualización que no pase por una decisión explícita del usuario**. Las actualizaciones se vigilan, pero nunca se descargan ni instalan solas, y nunca vienen del repositorio/servidor original, sino **del fork auditado**.

**No es** un proyecto de funcionalidades nuevas. Cualquier cambio que no sirva a (a) la auditoría o (b) el mecanismo de actualización por confirmación está **fuera de alcance**.

## 2. Invariantes (no negociables)

- **INV-1** Ninguna descarga de actualización ocurre sin un "Sí" explícito del usuario en esa sesión.
- **INV-2** Ninguna instalación ocurre sin un segundo paso explícito tras la descarga y la verificación (puede ser el mismo diálogo si el usuario ya confirmó descargar *e instalar*; ver REQ-U4).
- **INV-3** Toda actualización se descarga **solo desde el fork** del propietario. Ninguna URL del upstream, de `depot.sfg545.dev` ni de otro host del autor original puede ser origen de binarios ejecutables.
- **INV-4** Todo artefacto se verifica (firma + SHA-256) **antes** de instalarse. Fallo de verificación = no instalar, informar al usuario.
- **INV-5** La clave privada de firma del fork **nunca** entra al repositorio ni a CI en texto plano.
- **INV-6** No se agrega telemetría nueva. Si el upstream ya la tiene, se documenta y se deja desactivada por defecto cuando sea viable sin romper la app.
- **INV-7** Diferencia con upstream mínima y trazable: cada cambio del fork vive en commits propios, etiquetados `audit-fork:`.

## 3. Alcance

### 3.1 Dentro
1. Crear y publicar el fork (sección 4).
2. Reemplazar el mecanismo de actualización por uno **con confirmación** apuntando al fork (sección 5).
3. Monitor de releases upstream que **solo alerta** (sección 6).
4. Proceso de revisión de versiones nuevas y promoción al fork (sección 7).
5. Compilaciones de escritorio (Linux, y Windows si el upstream la ofrece) desde el fork.
6. **Pipeline de GitHub Actions** de build y release del fork (sección 8).
7. **Android / Android TV:** ver sección 9 (decisión abierta, no se ejecuta sin confirmación del propietario).

### 3.2 Fuera
Funcionalidades nuevas, integraciones nuevas (p. ej. Spotify), rediseños, cambios de UI no requeridos por el diálogo de actualización, cualquier cambio de proveedores de contenido.

## 4. Repositorio y README

- **REQ-R1** El Swarm crea un fork en la cuenta personal de GitHub del propietario, **público**, con nombre sugerido `orchard-audited` (renombrable por el propietario). Verificar antes que la licencia del upstream permite redistribución y respetarla (conservar `LICENSE`, avisos de copyright y atribución).
- **REQ-R2** La rama por defecto es `audited`, creada desde el tag `1.0.0-canary.9`. `main` del fork **no** sigue automáticamente al upstream.
- **REQ-R3** El `README.md` principal empieza con un bloque (traducible) que diga, en lenguaje llano:
  > **Este es un fork de auditoría de [sfg5453/orchard](https://github.com/sfg5453/orchard). No agrega funcionalidades nuevas.** Su único propósito es aumentar la seguridad: el código se revisa versión por versión antes de incorporarse, y la aplicación **no se actualiza sola**: te avisa cuando hay una versión nueva y solo la descarga si tú lo confirmas. Las descargas provienen de este repositorio, no del original.
  Debe incluir además: versión auditada actual, fecha de la auditoría, enlace a `AUDIT.md`, cómo verificar una descarga (firma/SHA-256), y crédito al autor original.
- **REQ-R4** `AUDIT.md` en la raíz: historial de versiones revisadas (versión upstream, commit, fecha, resultado, hallazgos, qué se verificó y qué **no** se pudo verificar). Parte con el informe de `1.0.0-canary.9` entregado por Claude.
- **REQ-R5** `UPSTREAM.md`: mapa de qué cambió el fork respecto al upstream (lista de commits `audit-fork:`), para poder rebasar cada versión nueva.

## 5. Actualizador con confirmación

Contexto técnico del upstream (según la auditoría): el instalador/bootstrapper `orchard` consulta `releases/<canal>.json` y `manifests/<versión>-<plataforma>.json` en `depot.sfg545.dev`, ambos en sobre `orchard-signed-v1` (ECDSA P-256/SHA-256), con la clave pública embebida en `bootstrapper/src/security/TrustedKeys.cpp` (`orchard-release-1`).

- **REQ-U1 Clave propia.** Generar un par ECDSA P-256 nuevo para el fork (`orchard-fork-1`). Sustituir la clave de confianza en `TrustedKeys.cpp` por la pública del fork. El fork **no confía** en la clave del autor original. La privada se guarda fuera del repo (offline o gestor de secretos del propietario).
- **REQ-U2 Origen.** Cambiar el servidor base del canal/manifiestos al fork (GitHub Releases o GitHub Pages del fork; decisión del Swarm, documentada). Mantener el formato `orchard-signed-v1` para minimizar el diff.
- **REQ-U3 Comprobación.** Al iniciar la app (escritorio/móvil/TV) se consulta **solo metadatos** (JSON firmado) del fork. Sin descargar binarios. Si no hay red, la app arranca normal sin error bloqueante.
- **REQ-U4 Confirmación.** Si hay versión mayor que la instalada, se muestra un diálogo: versión actual → nueva, notas de la versión, enlace al `AUDIT.md`, y dos botones **Sí (descargar) / No (ahora no)**. Tras descargar y verificar, un segundo diálogo **Instalar ahora / Más tarde**. "No" no vuelve a preguntar por esa misma versión durante N días configurable (por defecto 7) o hasta que el usuario pida buscar manualmente.
- **REQ-U5 Sin automatismos.** Eliminar o desactivar por defecto cualquier descarga en segundo plano, actualización silenciosa, instalación al salir, o canal "forzado". No debe existir ruta de código que salte la confirmación. Añadir una prueba que falle si se detecta una descarga sin confirmación.
- **REQ-U6 Verificación.** Antes de instalar: validar firma del sobre, hash del manifiesto, y SHA-256 de cada archivo/artefacto. Rechazar versión menor o igual (anti-rollback) usando el campo `sequence` ya existente.
- **REQ-U7 Canal.** Un solo canal, el auditado. Si el upstream tiene `canary`/`stable`, el fork publica únicamente lo que Claude haya aprobado.
- **REQ-U8 Transparencia.** El diálogo muestra el host de origen (el del fork) para que el usuario vea de dónde viene.

## 6. Monitor del upstream (solo alertar)

- **REQ-M1** Un workflow programado (GitHub Actions, cada 6–12 h) consulta releases/tags de `sfg5453/orchard` y los compara con el último tag registrado en `AUDIT.md`.
- **REQ-M2** Ante versión nueva **solo** abre un issue en el fork (título `Nueva versión upstream X.Y.Z`) y, si el propietario lo configura, envía una notificación. **Nunca** descarga, compila ni publica nada automáticamente.
- **REQ-M3** El issue incluye el diff de tags (`compare`), la lista de archivos cambiados y una casilla "Pendiente de auditoría".
- **REQ-M4** También vigilar cambios en el servidor/clave del upstream (`TrustedKeys.cpp`, URLs del updater) y marcarlos como alta prioridad.

## 7. Proceso de promoción de una versión nueva

1. Llega el issue del monitor.
2. El propietario pide a Claude la revisión; Claude audita fuente + compilaciones (mismo método que `1.0.0-canary.9`: reconstrucción, hashes, YARA, análisis estático).
3. Si hay **hallazgos críticos**: no se promueve; se documenta en `AUDIT.md`.
4. Si **aprobada**: el Swarm hace merge/rebase sobre `audited` reaplicando los commits `audit-fork:`, compila, firma con la clave del fork, publica Release + manifiestos firmados y actualiza `AUDIT.md`.
5. Solo entonces los clientes verán la versión nueva (y deberán confirmarla).

## 8. Pipeline de GitHub Actions (build y release automáticos del fork)

Este pipeline es **distinto del monitor** (sección 6): el monitor solo avisa de versiones nuevas del upstream y no construye ni publica nada. El pipeline del fork sí compila y publica, pero **solo se dispara cuando el propietario aprueba una versión para promover** (paso 4 de la sección 7). Nunca se activa por la detección de una versión nueva del upstream.

- **REQ-A1 Revisión previa.** Antes de escribir workflows, el Swarm revisa los workflows de CI/CD del repositorio original (se espera `.github/workflows/manual-desktop-build.yml` y `ci-cd.yml`, que compilan y publican Windows y Linux; macOS va por separado) y deja en `AUDIT.md` un inventario: qué plataformas construyen, con qué runners, qué secretos usan y qué artefactos publican. Los nombres y alcances aquí citados son lo observado al redactar este contrato; el Swarm debe **confirmarlos contra el código real** y registrar cualquier diferencia.
- **REQ-A2 Réplica fiel.** Replicar esos workflows en el fork para generar releases en los **mismos formatos y plataformas** que definió el autor (según lo observado: AppImage Linux x86_64, Linux ARM64, Windows; macOS aparte), **más un paquete `.rpm` para Linux (Fedora/KDE)**, que es lo único que se suma a los formatos del autor. El upstream no genera `.rpm`, así que el Swarm diseña ese empaquetado (spec RPM propio, mismos archivos que el AppImage, sin cambios de código) y documenta en `AUDIT.md` que es un formato agregado por el fork. Si el upstream no tiene workflow para alguna plataforma que sí distribuye, el Swarm diseña uno propio que reproduzca los mismos nombres de artefacto y la misma estructura de manifiestos.
- **REQ-A3 Destino.** Los artefactos se publican en los Releases del **repositorio del fork**, nunca en el original ni en `depot.sfg545.dev`. Los manifiestos y el canal (`releases/<canal>.json`, `manifests/<v>-<plataforma>.json`) se generan y firman con la clave del fork definida en REQ-U1.
- **REQ-A4 Disparo controlado.** El workflow de release se activa solo por: (a) `workflow_dispatch` manual del propietario, o (b) un tag `vX.Y.Z-audited` creado por el propietario tras aprobar la auditoría. Se exige un *environment* de GitHub (`release`) con aprobación manual requerida del propietario antes de acceder a secretos de firma.
- **REQ-A5 Firma sin exponer la clave.** La clave privada de firma (INV-5) no vive en el repositorio. Si el pipeline debe firmar en CI, se usa un secreto de *environment* con revisor requerido, o bien el pipeline publica los artefactos sin firmar y el propietario firma los manifiestos fuera de CI. Documentar la opción elegida en `AUDIT.md`.
- **REQ-A6 Seguridad del propio pipeline.** Acciones de terceros fijadas por **SHA de commit** (no por tag móvil); `permissions:` mínimos por job (`contents: write` solo en el job de publicación); sin `pull_request_target` con código no confiable; dependencias instaladas desde lockfile; sin descargar herramientas "latest" sin verificar hash.
- **REQ-A7 Limitaciones documentadas.** Si una plataforma no se puede replicar completa (p. ej. macOS con firma y notarización que dependen de certificados del autor original), el Swarm lo registra como **limitación conocida** en `AUDIT.md` y, si es razonable, publica esa plataforma **sin firmar** (indicándolo en el nombre del release y en las notas) o la deja **fuera del fork** con el motivo explicado. No se permite fallar en silencio ni omitir una plataforma sin dejar constancia.
- **REQ-A8 Reproducibilidad.** Cada release registra en sus notas: commit del fork, tag base del upstream, versiones de toolchain y SHA-256 de cada artefacto. Si el build del fork difiere en bytes de lo que habría producido el upstream, es esperable (clave y rutas distintas); lo importante es que sea reproducible desde el fork.
- **REQ-A10 Formato `.rpm`.** El `.rpm` se firma con una clave GPG propia del fork (la pública se publica en el repo y se documenta cómo importarla), con las mismas reglas de custodia de INV-5. El actualizador, tras el "Sí" del usuario, descarga y verifica el `.rpm`, pero **no instala por su cuenta con privilegios**: entrega el paquete verificado y pide una segunda confirmación que lo abra con el instalador del sistema (p. ej. `pkexec dnf install` con diálogo de autenticación o el gestor de software). La compilación debe validar el paquete (`rpm -K`, instalación de prueba en contenedor Fedora).
- **REQ-A9 Consistencia con el actualizador.** Los artefactos y manifiestos publicados deben ser exactamente lo que el actualizador (sección 5) espera consumir, incluyendo `sequence` creciente.

## 9. Plataformas (decisión abierta, requiere confirmación del propietario)

- Escritorio (Linux AppImage + instalador): **dentro de alcance**.
- **APK Android y Android TV:** el objetivo familiar los requiere, pero si Orchard no los trae, desarrollarlos **sí sería funcionalidad nueva** y contradice la identidad "fork sin funcionalidades nuevas" del README. Opciones: (A) repositorio hermano separado (`orchard-android`) con su propio README honesto; (B) mismo fork, README aclarando que Android/TV son *empaquetado* y no cambian el comportamiento. **El Swarm no ejecuta esta fase hasta que el propietario elija.**
- Si se hace en Android: actualizador in-app con las mismas reglas (REQ-U3 a U8); descarga solo tras "Sí"; verificación de firma y SHA-256 antes de lanzar el instalador del sistema; firma de APK con keystore propio del propietario; en TV, el diálogo debe ser operable con D-pad.

## 10. Criterios de aceptación

- [ ] Fork público, licencia respetada, README con el bloque de REQ-R3, `AUDIT.md` y `UPSTREAM.md`.
- [ ] `grep` en el fork no encuentra `depot.sfg545.dev` ni `sfg545` en rutas de descarga/ejecución (solo menciones de atribución en docs).
- [ ] Con una versión nueva publicada en el fork, la app muestra el diálogo, **no descarga nada hasta pulsar Sí**, y verifica firma/hash antes de instalar.
- [ ] Pulsar "No" no descarga nada y la app funciona normalmente.
- [ ] Firma inválida o hash alterado → la instalación se rechaza (prueba negativa incluida).
- [ ] Versión menor/igual → no se ofrece (anti-rollback).
- [ ] El monitor abre un issue cuando el upstream publica un tag nuevo y no hace nada más.
- [ ] La clave privada no está en el repo ni en el historial.
- [ ] El release del fork incluye el `.rpm` firmado y se instala en Fedora (prueba en contenedor).
- [ ] **El pipeline de GitHub Actions del fork compiló y publicó correctamente al menos una vez**, generando en los Releases del fork artefactos en los mismos formatos y para las mismas plataformas que el upstream (o con cada excepción documentada como limitación conocida en `AUDIT.md`), antes de dar por cumplido el contrato.
- [ ] El pipeline solo se dispara por `workflow_dispatch` o tag `-audited` del propietario con aprobación del *environment* `release`; el monitor del upstream no lo activa.
- [ ] `AUDIT.md` contiene el inventario de los workflows originales y las limitaciones de plataforma (firma/notarización, iOS u otras).
- [ ] Diff contra el tag base limitado a: README/AUDIT/UPSTREAM, actualizador, clave de confianza, workflows, y lo mínimo para compilar.

## 11. Riesgos y notas

- La compilación de QtWebEngine/Chromium y partes del build C++ no se pudieron reproducir en la auditoría original; el fork reutiliza dependencias de la misma forma que el upstream. Documentarlo en `AUDIT.md`.
- Un upstream anónimo puede cambiar de dirección: la protección real es que **nada del upstream llega al fork sin revisión**.
- Este contrato no autoriza publicar secretos, ni modificar el upstream, ni abrir PRs contra él.
