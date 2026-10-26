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

### HU-ORCH-13: Login por Código QR y Vinculación Remota de Dispositivos (Android TV & Móvil)
- **ID:** HU-ORCH-13
- **Prioridad:** Alta
- **Estimación:** 5 SP
- **Contrato:** `SDD-tv-qr-pairing.md` (invariantes `PAIR-INV-1` a `PAIR-INV-8`).
- **Contexto de Negocio:**
  En Android TV, el inicio de sesión web en WebView resulta inutilizable con control remoto (D-pad) al solicitar verificación en 2 pasos de Google (2FA, passkeys, llaves de seguridad). Para resolverlo definitivamente, se implementa vinculación remota: el televisor despliega un código QR y código SAS de 6 dígitos; la app móvil de Orchard (o escaneo de QR) transfiere la sesión activa de forma instantánea, cifrada de extremo a extremo (ECDH P-256 + AES-256-GCM) y con confirmación explícita del usuario.
- **Criterios de Aceptación:**
  - `TvQrLoginScreen` en Android TV con código QR de alto contraste (zxing core), código SAS legible, temporizador y opción de fallback a WebView (`LOGIN_WEB`).
  - Servidor de emparejamiento efímero local (`TvPairingServer`) en la TV con cifrado simétrico AES-256-GCM, clave efímera acordada por ECDH P-256 autenticada por huella SHA-256 en el QR y token de 128 bits.
  - Anuncio mDNS / NSD `_orchard-pair._tcp` en red local para detección automática desde la app móvil.
  - Opción "Vincular con Android TV" en `SettingsScreen` móvil con confirmación explícita del usuario antes de transferir `YouTubeSession` (y Spotify opcional).
  - Cero dependencias privativas (sin Google Play Services / Firebase), apagado automático del servidor local tras completar o cancelar, y tests unitarios de emparejamiento en verde.

### HU-ORCH-19: Soporte de Navegación y Reproducción Completa en Android Auto
- **ID:** HU-ORCH-19
- **Prioridad:** Alta
- **Estimación:** 5 SP
- **Contrato SDD:** `SDD-android-auto.md` · 1.0 APROBADO 2026-10-26
- **Issue GitHub:** [#1](https://github.com/jucsp/orchard-audited/issues/1)
- **Contexto de Negocio:**
  Cuando el usuario conecta su teléfono Android al sistema de infoentretenimiento de su vehículo (Android Auto), el reproductor en curso de Orchard se visualiza en el widget resumido de medios si ya había música sonando, pero al tocar el ícono de Orchard o intentar abrir la aplicación desde el lanzador del automóvil, la pantalla del vehículo bloquea el acceso mostrando el mensaje: *"Orchard no está disponible para Android Auto"*.
  El motivo del desarrollo es permitir una experiencia de conducción 100% funcional y segura desde la pantalla del vehículo: abrir Orchard directamente en Android Auto, navegar por las secciones de Inicio, Biblioteca, Playlists, Álbumes y Podcasts, seleccionar pistas o colecciones, y controlar la reproducción desde la pantalla táctil del auto o los mandos del volante sin tocar el teléfono.
- **Escenarios de Usuario y Reglas de Negocio:**
  1. **Apertura sin reproducción previa:** El usuario sube al vehículo, abre Android Auto y selecciona Orchard en el cajón de aplicaciones. La app se abre mostrando las pestañas/nodos de navegación raíz sin error de disponibilidad.
  2. **Exploración de catálogo y biblioteca:** El usuario navega por sus listas guardadas, canciones favoritas, álbumes, podcasts y recomendaciones de inicio, e inicia la reproducción con un toque.
  3. **Soberanía y privacidad (Invariantes del Fork):** Debe implementarse exclusivamente con las APIs estándar de AndroidX Media3 (`MediaLibraryService` / `MediaLibrarySession`) y declaraciones de manifiesto (`com.google.android.gms.car.application` / `automotive_app_desc.xml`), sin incorporar SDKs privativos de Google Play Services ni telemetría externa.
- 📁 **Repositorio:** `orchard-audited` (`/home/juancarlos/Proyectos/Personales/orchard-audited`)
- 🎯 **Capa a trabajar:** Android Mobile (`mobile/android/app` — Manifiesto, recursos XML automotrices y `OrchardPlaybackService` / `MediaLibraryService`).
- 📌 **Objetivo técnico:**
  Investigar por qué `OrchardPlaybackService` (actualmente `MediaSessionService` o sin descriptor de `MediaBrowserService` / `MediaLibraryService` completo) es rechazado al abrir la interfaz de exploración en Android Auto, y especificar el contrato SDD para migrar/exponer un `MediaLibraryService` con árbol de navegación jerárquico (`onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onAddMediaItems` / `onSetMediaItems` con resolución de stream), controles de transporte y validación de paquete del host automotriz.
- **Criterios de Aceptación:**
  - Orchard aparece como aplicación de música habilitada y abrible en el lanzador de Android Auto sin mostrar el bloqueo *"no está disponible para Android Auto"*.
  - El árbol de navegación en pantalla del vehículo permite explorar Inicio, Biblioteca (Playlists, Canciones, Álbumes, Podcasts) y Cola, e iniciar la reproducción de cualquier ítem.
  - Los controles de transporte (play/pause, siguiente, anterior, seek, shuffle/repeat) responden desde el volante y la pantalla del vehículo.
  - Cero dependencias de `com.google.android.gms:play-services-*`.

### HU-ORCH-20: Gestión de Rotación de Certificados TLS y Credenciales TOFU en CastV2 (Google Nest Mini)
- **ID:** HU-ORCH-20
- **Prioridad:** Alta
- **Estimación:** 3 SP
- **Contrato SDD:** `SDD-castv2-tofu-rotation.md` (enmienda a `SDD-chromecast-castv2.md`) · 1.0 APROBADO 2026-10-26
- **Issue GitHub:** [#2](https://github.com/jucsp/orchard-audited/issues/2)
- **Contexto de Negocio:**
  Tras implementar el cliente CastV2 propio sin Google Play Services (`HU-ORCH-12`), el envío de canciones hacia un parlante **Google Nest Mini** funcionaba correctamente. Sin embargo, posteriormente al intentar enviar una canción al mismo Nest Mini, la aplicación rechaza la operación indicando que *"no poseen las mismas credenciales o certificaciones"* (o fallo de verificación de certificado/credencial del receptor).
  El problema original radica en el ciclo de vida criptográfico de los receptores Google Cast (especialmente parlantes Nest Mini / Google Home): a diferencia de un servidor web con certificado fijo de larga duración, los dispositivos Cast generan y rotan periódicamente su certificado TLS de transporte autofirmado (p. ej., cada 24–48 horas o al reiniciar el parlante), mientras su clave de dispositivo subyacente (`Cast Device ICA`) o su UUID mDNS (`id` en el registro TXT `_googlecast._tcp`) permanece igual. Con un modelo TOFU estricto (`PinningTrustManager` en `CastTrust.kt` + `SharedPreferencesCastTrustStore.kt`) que clava perpetuamente el hash SHA-256 del primer certificado TLS efímero observado sin mecanismo de rotación, renovación o verificación de cadena/re-vinculación, el parlante queda bloqueado en cuanto rota su certificado TLS o cambia de sesión en la LAN.
- **Escenarios de Usuario y Reglas de Negocio:**
  1. **Reconexión transparente o asistida tras rotación TLS del Nest Mini:** El usuario selecciona su Nest Mini en la hoja de dispositivos. Aunque el parlante haya reiniciado o rotado su certificado TLS efímero desde la última sesión, el sistema valida o actualiza las credenciales de forma segura y reproduce la canción sin bloquearse permanentemente.
  2. **Autorización del Stream Server local (`ChromecastStreamServer`):** Al cargar el audio (`LOAD`), el Nest Mini realiza la petición HTTP al teléfono en la LAN y el servidor local valida el token y la IP del receptor sin rechazar las credenciales de la sesión activa.
  3. **Respeto a las invariantes `CAST-INV-1` a `CAST-INV-9`:** Sin Google Play Services, comunicación 100% LAN (RFC 1918), sin servidores externos.
- 📁 **Repositorio:** `orchard-audited` (`/home/juancarlos/Proyectos/Personales/orchard-audited`)
- 🎯 **Capa a trabajar:** Android CastV2 (`mobile/android/app/src/main/java/dev/sfg/orchard/mobile/cast/` y `playback/` — `CastTrust.kt`, `SharedPreferencesCastTrustStore.kt`, `CastChannel.kt`, `CastSession.kt`, `ChromecastPlayback.kt`, `ChromecastStreamServer.kt`).
- 📌 **Objetivo técnico:**
  Investigar en el código actual de CastV2 y reproducción dónde se origina exactamente el mensaje/error de discrepancia de credenciales o certificaciones al transmitir hacia un Nest Mini (tanto en `PinningTrustManager` / `CastTrust` por rotación de certificado TLS del receptor, como en cualquier validación de cuenta/DRM/credencial del stream o token en `ChromecastPlayback` / `ChromecastStreamServer`), y definir en un contrato SDD el mecanismo seguro de manejo de rotación de certificados/credenciales y recuperación automática o con consentimiento.
- **Criterios de Aceptación:**
  - Causa raíz exacta del mensaje de incompatibilidad de credenciales/certificaciones documentada y cubierta en el contrato SDD.
  - La conexión y envío de pistas hacia un Google Nest Mini funciona de manera sostenida a lo largo de los días, incluso tras reinicios del parlante o rotación de su certificado TLS autofirmado.
  - Si el certificado TLS efímero de un receptor Cast cambia en la LAN, el flujo de confianza (`CastTrust`) gestiona la rotación/actualización sin dejar el dispositivo bloqueado con pin obsoleto.
  - Suite de pruebas unitarias JVM en verde validando el escenario de rotación de certificado y reconexión.

### HU-ORCH-21: Propagación Integral de Credenciales (Orchard Connect e Integraciones) vía Login QR en Android TV
- **ID:** HU-ORCH-21
- **Prioridad:** Alta
- **Estimación:** 3 SP
- **Contrato SDD:** `SDD-tv-qr-pairing-connect.md` (extensión de `SDD-tv-qr-pairing.md`) · 1.0 APROBADO 2026-10-26
- **Issue GitHub:** [#3](https://github.com/jucsp/orchard-audited/issues/3)
- **Contexto de Negocio:**
  Actualmente en Android TV el usuario no puede iniciar sesión en **Orchard Connect** (`connect.kasper-cloud.com`), lo que impide sincronizar en tiempo real la cola de reproducción, el estado del reproductor, la presencia entre dispositivos y las salas de Listening Party con el televisor. Además, el login por código QR (`HU-ORCH-13`) fue concebido inicialmente pensando en destrabar el login de YouTube Music (y opcionalmente la cookie `sp_dc` de Spotify), dejando fuera a Orchard Connect y otras credenciales de integración configuradas en el teléfono.
  El cliente requiere que el escaneo del código QR desde el teléfono actúe como un **aprovisionamiento integral de sesión e integraciones**: al vincular el móvil con Android TV mediante QR, se deben propagar automáticamente todas las credenciales activas en el teléfono (cuenta y tokens de **Orchard Connect**, **YouTube Music**, **Spotify**, **Qobuz**, y claves/configuraciones de integración pertinentes), dejando el televisor autenticado en Orchard Connect y en todos los proveedores sin requerir inicios de sesión adicionales en la TV.
- **Escenarios de Usuario y Reglas de Negocio:**
  1. **Vinculación inicial o desde Ajustes en TV:** Cuando el usuario escanea el QR de Android TV con su teléfono (donde ya tiene iniciada su sesión de Orchard Connect y sus proveedores de música), el diálogo de confirmación muestra claramente todas las credenciales/integraciones que se van a transferir.
  2. **Activación inmediata de Orchard Connect en TV:** Apenas finaliza la transferencia cifrada, Android TV persiste el token/sesión de Orchard Connect (`OrchardAccountService`), conecta el WebSocket de `ConnectHub` en `connect.kasper-cloud.com` y queda visible como dispositivo conectado para control remoto y continuidad de reproducción.
  3. **Propagación de cualquier credencial de integración activa:** Además de YouTube Music y Orchard Connect, si el móvil posee credenciales válidas de Spotify, Qobuz o clave de traducción BYOK (Gemini), se propagan en el mismo sobre cifrado E2EE (`orchard-pair-v1`).
  4. **Seguridad y retrocompatibilidad (`PAIR-INV-1` a `PAIR-INV-8`):** Todo viaja exclusivamente por la LAN local dentro del sobre cifrado `ECDH P-256 + HKDF-SHA256 + AES-256-GCM`, sin tocar servidores intermedios durante el emparejamiento y limpiando buffers sensibles tras aplicar las sesiones.
- 📁 **Repositorio:** `orchard-audited` (`/home/juancarlos/Proyectos/Personales/orchard-audited`)
- 🎯 **Capa a trabajar:** Android Emparejamiento QR, Cuenta Connect y Autenticación (`mobile/android/app/src/main/java/dev/sfg/orchard/mobile/pairing/`, `account/OrchardAccountService.kt`, `auth/`, `ui/tv/settings/` y `app/OrchardViewModel.kt`).
- 📌 **Objetivo técnico:**
  Investigar cómo almacena y restaura su sesión `OrchardAccountService` en Android y cómo opera actualmente `PairingMessages.kt` / `PairingClient.kt` / `TvPairingServer.kt`, para diseñar en un contrato SDD la extensión del esquema de payload cifrado de emparejamiento QR y la hidratación inmediata de `OrchardAccountService` + repositorios de autenticación en Android TV (además de exponer el estado de Orchard Connect en los ajustes de Android TV).
- **Criterios de Aceptación:**
  - Al escanear el código QR de Android TV desde el teléfono, se transfieren e inicializan automáticamente las credenciales de **Orchard Connect** junto con **YouTube Music** y cualquier otra integración activa en el teléfono (Spotify, Qobuz, etc.).
  - Android TV inicia sesión automáticamente en Orchard Connect (`connect.kasper-cloud.com`) tras el emparejamiento QR y muestra su estado conectado en Ajustes de TV.
  - El diálogo de confirmación en el móvil detalla explícitamente la lista de servicios/credenciales detectados que serán propagados al televisor.
  - Cumplimiento estricto de las invariantes `PAIR-INV-1` a `PAIR-INV-8` y suite de tests unitarios JVM de emparejamiento actualizada en verde.

