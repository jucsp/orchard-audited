# Contrato SDD — Android Auto: apertura, árbol de navegación y reproducción (HU-ORCH-19)

- **Versión del contrato:** 1.0 · 2026-10-26 · APROBADO
- **Propietario:** juanca
- **Autor:** Claude (PM/QA)
- **Contratos padre:** `SDD-orchard-audit-fork.md` (INV-1 a INV-7) y `SDD-chromecast-castv2.md` (CAST-INV-1 a CAST-INV-9). Siguen vigentes. Este documento agrega los invariantes `AUTO-INV-*` y aclara la verificación de CAST-INV-1 (D4, aplicada en `SDD-chromecast-castv2.md`).
- **Base de código:** `audited` en `0c4a59b`. Rama de trabajo: `integrate/hu-orch-19-20-21` (compartida con HU-ORCH-20 y HU-ORCH-21).
- **Aprobación:** el propietario aprobó D1, D2, D3 y D4 el 2026-10-26, con V0 como verificación posterior al build (sección 11).
- **Backlog:** HU-ORCH-19 en `backlog.md`.

---

## 1. Contexto de negocio

Al conectar el teléfono al auto, Android Auto muestra la tarjeta de "reproduciendo ahora" de Orchard si ya sonaba música, pero al tocar el ícono de Orchard en el lanzador del vehículo aparece *"Orchard no está disponible para Android Auto"*. El propietario quiere abrir Orchard en la pantalla del auto, navegar Inicio, Biblioteca (Me gusta, Playlists, Álbumes, Podcasts) y Recientes, buscar, tocar una pista o colección y controlar todo desde la pantalla o el volante, sin tocar el teléfono y sin agregar dependencias privativas.

## 2. Diagnóstico técnico de causa raíz

### 2.1 Lo que ya existe (verificado en código)

El código **sí** implementa un servicio de biblioteca de Media3. La hipótesis del backlog ("`OrchardPlaybackService` es `MediaSessionService` o le falta el descriptor") no se cumple:

| Pieza | Evidencia |
| --- | --- |
| Descriptor automotriz | `mobile/android/app/src/main/res/xml/automotive_app_desc.xml:3-5` declara `<uses name="media"/>` |
| Meta-data del manifiesto | `mobile/android/app/src/main/AndroidManifest.xml:54-56` (`com.google.android.gms.car.application` → `@xml/automotive_app_desc`). Es solo una clave de manifiesto; no incluye código de Play Services |
| Servicio exportado con los tres intent-filter | `AndroidManifest.xml:102-112`: `androidx.media3.session.MediaLibraryService`, `MediaSessionService` y `android.media.browse.MediaBrowserService` |
| Clase del servicio | `playback/OrchardPlaybackService.kt:64`: `class OrchardPlaybackService : MediaLibraryService()`; sesión `MediaLibrarySession` en `:152-164`; `onGetSession` en `:245` |
| Árbol de navegación | `playback/OrchardMediaLibrary.kt:45-67` (raíz con Liked, Playlists, Recent), `:82-96` (`item`), `:102-108` (expansión a cola) |
| Callbacks | `playback/OrchardSessionCallback.kt:80-89` `onGetLibraryRoot`, `:91-109` `onGetChildren` (paginado), `:111-121` `onGetItem`, `:123-151` `onConnect` (concede `COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT`), `:205-240` `onSetMediaItems` (búsqueda por voz y expansión), `:242-249` `onAddMediaItems`, `:251-265` resolución `orchard://stream/<id>` |

El código vino del upstream (`23764a5 release orchard 4.2.0 and orchard mobile 1.0.0`) y el fork no lo tocó.

### 2.2 Causa raíz principal: política del host Android Auto sobre el origen de instalación

Android Auto solo habilita apps de medios instaladas desde Google Play. Una app instalada desde otro origen se oculta o se bloquea en el lanzador del auto, salvo que el usuario active **Ajustes de Android Auto → Configuración para desarrolladores → Fuentes desconocidas**. Orchard del fork se distribuye como APK firmado en GitHub Releases y se actualiza con `UpdateManager.kt:311-320` (`DownloadManager` + instalador del sistema). El instalador registrado es el del sistema, no `com.android.vending`. El síntoma coincide: la tarjeta de medios se muestra porque el sistema publica cualquier `MediaSession`, pero el lanzador del auto se niega a abrir la app.

Esto no se corrige con código de la app. Falsificar el instalador (`setInstallerPackageName("com.android.vending")` vía ADB o root) queda prohibido por AUTO-INV-3. La salida es:

1. Que el usuario active "Fuentes desconocidas" en Android Auto (operación única, documentada en la app).
2. Que la app detecte el origen de instalación y muestre esas instrucciones (sección 5.5).

**Verificación en vehículo real (V0, posterior al build, no bloquea T1..T4 ni Q1..Q2):** `adb shell pm list packages -i dev.sfg.orchard.mobile` debe mostrar un instalador distinto de `com.android.vending`; con "Fuentes desconocidas" activado y el teléfono reconectado, Orchard debe abrirse en el auto sin el mensaje. Las brechas G1..G8 existen con independencia de la causa de apertura, así que se implementan en paralelo. Si con la opción activa el mensaje persiste, se reabre el diagnóstico de apertura en una tarea aparte antes de cerrar la HU.

### 2.3 Brechas de código frente a los criterios de aceptación

Aunque se destrabe la apertura, el código actual no cumple la HU:

| # | Brecha | Ubicación |
| --- | --- | --- |
| G1 | La raíz solo tiene tres nodos (Liked, Playlists, Recent). Faltan Inicio, Álbumes y Podcasts | `OrchardMediaLibrary.kt:48-52` |
| G2 | No hay búsqueda desde la pantalla del auto: `OrchardSessionCallback` no sobrescribe `onSearch` ni `onGetSearchResult`; solo la voz entra por `onSetMediaItems` | `OrchardSessionCallback.kt:205-224` |
| G3 | `onGetLibraryRoot` ignora `params.isRecent`. La IU del sistema pide una raíz de "recientes" para reanudar reproducción y recibe el árbol completo | `OrchardSessionCallback.kt:80-89` |
| G4 | `onConnect` acepta a cualquier controlador. Cualquier app del teléfono puede recorrer la biblioteca (títulos, playlists privadas) y lanzar búsquedas autenticadas | `OrchardSessionCallback.kt:123-151` |
| G5 | Sin sesión de YouTube, `children()` devuelve listas vacías sin error. El auto muestra carpetas vacías y el conductor no sabe que debe iniciar sesión en el teléfono | `OrchardMediaLibrary.kt:56-66`, `OrchardSessionCallback.kt:101-103` |
| G6 | Títulos de nodos en inglés ("Liked songs", "Recently played") y sin `strings.xml` | `OrchardMediaLibrary.kt:45-52, 86-88` |
| G7 | `OrchardPlaybackService.kt` tiene 492 líneas: cualquier lógica nueva debe vivir fuera de él (`contributing.md`) | `OrchardPlaybackService.kt` |
| G8 | El `onGetChildren` hace `drop/take` sobre la lista completa en cada página: una playlist de miles de pistas se descarga entera por página | `OrchardSessionCallback.kt:104-106` |

## 3. Alcance

### 3.1 Dentro

1. Diagnóstico de origen de instalación y guía en Ajustes del teléfono (5.5).
2. Árbol completo: Inicio, Biblioteca (Me gusta, Playlists, Álbumes, Podcasts), Recientes (5.1).
3. Búsqueda en pantalla (`onSearch`/`onGetSearchResult`) y por voz (ya existente, se conserva).
4. Raíz de recientes para reanudación (`isRecent`).
5. Control de acceso de controladores (AUTO-INV-4).
6. Errores accionables cuando no hay sesión (G5).
7. Cachés por nodo para paginar sin repetir descargas (G8).
8. Tests JVM del árbol y de la política de controladores.

### 3.2 Fuera

- Publicación en Google Play.
- Android Automotive OS (app instalada en el auto). Requiere otro APK y otra revisión.
- Video, letras y Listening Party en la pantalla del auto.
- Car App Library (`androidx.car.app`): no hace falta para medios y suma superficie.
- Cambios en el motor de reproducción, crossfade o Chromecast.

## 4. Invariantes

| ID | Invariante | Cómo se verifica |
| --- | --- | --- |
| AUTO-INV-1 | **Cero SDK propietarios.** Solo `androidx.media3:media3-session` (ya presente). Sin `play-services-*`, `com.google.android.gms` como dependencia, `androidx.car.app` ni analítica. La clave `com.google.android.gms.car.application` del manifiesto es solo un nombre de meta-data | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` sin `gms`, `play-services`, `car.app`; diff de `build.gradle` sin dependencias nuevas |
| AUTO-INV-2 | **Sin red nueva.** El árbol solo usa repositorios existentes (`graph.library`, `graph.catalog`) y hosts ya inventariados en `AUDIT.md` §5.2. No se agrega ningún host, endpoint ni telemetría | `git grep -nE 'https?://' playback/` revisado; revisión QA |
| AUTO-INV-3 | **Sin evasión de la política del host.** Prohibido falsificar el instalador, escribir en ajustes de Android Auto, usar APIs ocultas o reflexión sobre `gearhead`. La app solo lee su propio origen de instalación e informa | `git grep -nE 'setInstallerPackageName\|com.android.vending\|gearhead' mobile/` solo en la lista de controladores permitidos y en la detección de origen |
| AUTO-INV-4 | **Controladores de confianza.** La navegación (`onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onSearch`) solo se sirve a: la propia app, controladores que Media3 marca `isTrusted` (sistema o misma firma) y la lista fija `com.google.android.projection.gearhead`, `com.android.systemui`, `com.google.android.carassistant`. Los demás reciben `LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)` y solo comandos de transporte | Test JVM de `AutoControllerPolicy` |
| AUTO-INV-5 | **Seguridad de conducción.** Sin video ni teclado propio. Profundidad máxima 4 niveles bajo la raíz. Raíz con 4 pestañas como máximo. Páginas de 100 ítems como máximo por respuesta | Tests del árbol; revisión QA |
| AUTO-INV-6 | **Sin secretos en logs.** Logs sin cookies, tokens, consultas de búsqueda completas ni ids de cuenta. Los `Log.d` existentes con `rawIds` y URIs (`OrchardSessionCallback.kt:212-213, 238`) se reducen a conteos | `git grep -n 'Log\.' playback/` revisado línea a línea |
| AUTO-INV-7 | **Una sola cola autoritativa.** Lo que se toca en el auto entra por `onSetMediaItems`/`onAddMediaItems` y pasa por `resolveMediaItem` al mismo `Player` que usa el teléfono, Chromecast incluido | Test de `onSetMediaItems` con controlador externo |
| AUTO-INV-8 | Archivos de menos de 500 líneas, cabecera AGPL-3.0-or-later en archivos nuevos, sin narración histórica (`contributing.md`). `OrchardPlaybackService.kt` no crece más de 5 líneas | `wc -l`; revisión QA |

Una implementación que viole un `AUTO-INV`, un `CAST-INV` o un `INV` del contrato padre no se integra.

## 5. Diseño

### 5.1 Árbol de navegación

```
orchard://root                        (rootExtras: grid para browsables, list para playables)
├── orchard://home                    Inicio      → secciones de catalog.home(); cada sección es carpeta
│   └── orchard://home/<sectionId>    pistas o colecciones de esa sección
├── orchard://library                 Biblioteca
│   ├── orchard://liked               Me gusta
│   ├── orchard://playlists           Playlists   → orchard://playlist/<id>
│   ├── orchard://albums              Álbumes     → orchard://album/<id>
│   └── orchard://podcasts            Podcasts    → orchard://podcast/<id> (episodios)
└── orchard://recent                  Recientes
```

- Ids existentes (`ROOT`, `LIKED`, `PLAYLISTS`, `RECENT`, `PLAYLIST_PREFIX`, `SEARCH_PREFIX`) se conservan para no romper `queueFor` ni reanudaciones guardadas.
- Fuente de datos: `graph.library.library.value` (`savedAlbums`, `savedPlaylists`, `savedPodcasts`, `likedTracks`, `recentlyPlayed`; `model/UiModels.kt:46-51`) y `graph.catalog.home()` (`catalog/CatalogRepository.kt:53`), `graph.catalog.browse(id)` para el detalle.
- `MediaMetadata.mediaType`: `MEDIA_TYPE_FOLDER_PLAYLISTS`, `FOLDER_ALBUMS`, `FOLDER_PODCASTS`, `ALBUM`, `PLAYLIST`, `PODCAST`, `MUSIC`, `PODCAST_EPISODE`.
- Pestañas de raíz: `Inicio`, `Biblioteca`, `Recientes` (3 ≤ 4, AUTO-INV-5). Títulos en `strings.xml` (G6).
- Al tocar una colección completa se usa `queueFor` (ya existente) para cargar la lista entera desde la pista tocada.

### 5.2 Raíz de recientes (G3)

Si `params?.isRecent == true`: responder una raíz `orchard://recent-root` cuyo único hijo es la pista actual restaurada desde `PlaybackStateStore`. Sin estado guardado: `LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)`. Así la IU del sistema muestra la tarjeta de reanudación sin cargar el árbol.

### 5.3 Búsqueda (G2)

- `onSearch(query)`: lanza `browseTree.search(query)` en `browseScope`, guarda el resultado bajo `orchard://search/<query>` y llama `session.notifySearchResultChanged(browser, query, count, params)`. Devuelve `LibraryResult.ofVoid()`.
- `onGetSearchResult(query, page, pageSize)`: sirve la página desde la caché.
- Consulta acotada a 200 caracteres; vacía = Me gusta (comportamiento actual de `search`).

### 5.4 Errores accionables (G5)

Sin sesión de YouTube, `onGetChildren` de nodos que lo requieren devuelve `LibraryResult.ofError(SessionError(ERROR_SESSION_AUTHENTICATION_EXPIRED, "Inicia sesión en Orchard desde el teléfono"))`. Android Auto muestra el mensaje en pantalla. Fallos de red: `ERROR_IO` con mensaje corto.

### 5.5 Diagnóstico de origen de instalación (causa raíz 2.2)

- `AutoInstallSource.kt` (nuevo, `playback/auto/`): lee el instalador con `PackageManager.getInstallSourceInfo(packageName).installingPackageName` en API ≥ 30 y `getInstallerPackageName` en 28-29. Solo lectura local, sin red.
- Ajustes del teléfono → Conexiones → fila "Android Auto": si el instalador no es `com.android.vending`, muestra las instrucciones (activar modo desarrollador de Android Auto tocando 10 veces "Versión", menú ⋮ → Configuración para desarrolladores → Fuentes desconocidas, reconectar el teléfono, y si no aparece, "Personalizar lanzador"). Si es Play, muestra "Lista para Android Auto".
- No se muestra en TV (`isTvLayout()`).

### 5.6 Componentes

| Archivo | Cambio | `android.*` |
| --- | --- | --- |
| `playback/OrchardMediaLibrary.kt` | Árbol de 5.1, ids nuevos, cachés por nodo con TTL de 5 min, errores de 5.4. Si supera 400 líneas, se divide en `playback/auto/AutoBrowseNodes.kt` | Media3 |
| `playback/auto/AutoControllerPolicy.kt` (nuevo) | Decide si un `ControllerInfo` puede navegar (AUTO-INV-4). Función pura sobre `packageName` e `isTrusted` | No |
| `playback/auto/AutoInstallSource.kt` (nuevo) | Origen de instalación (5.5) | Sí |
| `playback/OrchardSessionCallback.kt` | `isRecent`, `onSearch`, `onGetSearchResult`, filtro de controladores, logs (AUTO-INV-6) | Media3 |
| `ui/screens/SettingsConnections.kt` | Fila "Android Auto" | Compose |
| `res/values/strings.xml` | Títulos de nodos y mensajes | — |
| `AndroidManifest.xml`, `automotive_app_desc.xml` | **Sin cambios** (ya correctos) | — |

## 6. Decisiones (ADR)

**D1. No se publica en Google Play.** Rompería INV del fork (distribución propia, sin cuenta de desarrollador). El costo para el usuario es activar una opción una vez.

**D2. Media3 `MediaLibraryService` y no Car App Library.** Ya está implementado, cubre medios y no agrega dependencias.

**D3. Lista fija de controladores.** Más estricta que aceptar a todos y sin depender de firmas de Google en el código. Si un host legítimo nuevo aparece (p. ej. otro paquete de Android Auto), se agrega con una línea y un test.

**D4. Aclaración de CAST-INV-1 (aprobada, aplicada en `SDD-chromecast-castv2.md`).** La verificación de CAST-INV-1 decía "`git grep com.google.android.gms` vacío en `mobile/`", pero `AndroidManifest.xml:55` contiene la clave `com.google.android.gms.car.application` del upstream. Texto vigente: "vacío en código y dependencias; se exceptúa la clave de meta-data `com.google.android.gms.car.application` del manifiesto, que no es una dependencia GMS (HU-ORCH-19)".

## 7. Desglose de tareas

| ID | Tarea | Responsable | Entrega | Depende de |
| --- | --- | --- | --- | --- |
| V0 | Confirmar 2.2 en vehículo real con el APK de la rama: instalador con `pm list packages -i`, prueba con "Fuentes desconocidas" | Propietario + Claude PM/QA | Resultado en `AUDIT.md` | Build de la rama (no bloquea T1..Q2) |
| T1 | `AutoControllerPolicy` + integración en `onConnect`/`onGetLibraryRoot`/`onGetChildren`/`onGetItem` | DeepSeek Dev | Código + `AutoControllerPolicyTest` | — |
| T2 | Árbol de 5.1 con cachés por nodo y paginación sin recarga (G1, G8) | DeepSeek Dev | `OrchardMediaLibrary.kt` + `OrchardMediaLibraryTest` | — |
| T3 | `isRecent`, `onSearch`, `onGetSearchResult`, errores de 5.4 | DeepSeek Dev | `OrchardSessionCallback.kt` + tests | T2 |
| T4 | `AutoInstallSource` | DeepSeek Dev | Código + test con `PackageManager` simulado | — |
| Q1 | Fila de Ajustes "Android Auto" y strings | Claude PM/QA | Compose + `strings.xml` | T4 |
| Q2 | Logs sin datos sensibles (AUTO-INV-6), `wc -l`, dependencias (AUTO-INV-1) | Claude PM/QA | Informe en `AUDIT.md` | T1-T3 |
| Q3 | Prueba en Desktop Head Unit (DHU) y en auto real (sección 8.2), junto con V0 | Claude PM/QA + propietario | Informe | Q1, Q2, V0 |
| Q4 | `UPSTREAM.md` (zonas protegidas), `technical_memory.md` | Claude PM/QA | Docs | Q3 |
| I1 | Rama `integrate/hu-orch-19-20-21`, commits `audit-fork:` uno por tarea | Antigravity PO | Rama | T1..Q4 |

## 8. Plan de verificación

### 8.1 JVM (`src/test/java/dev/sfg/orchard/mobile/playback/`)

| Test | Casos mínimos |
| --- | --- |
| `AutoControllerPolicyTest` | Propia app, `gearhead`, `systemui`, `carassistant` y `isTrusted` → permitido; paquete arbitrario → denegado; nombre vacío → denegado |
| `OrchardMediaLibraryTest` | Raíz con 3 pestañas; Biblioteca con 4 hijos; álbum y podcast resuelven hijos; página 2 de 100 no vuelve a llamar al catálogo; sin sesión → error `AUTHENTICATION_EXPIRED`; `queueFor` sigue funcionando con ids antiguos |
| `OrchardSessionCallbackAutoTest` | `isRecent` con estado → un ítem; sin estado → `NOT_SUPPORTED`; `onSearch` notifica y `onGetSearchResult` pagina; controlador no permitido → `PERMISSION_DENIED` en navegación, comandos de transporte sí |

### 8.2 Manual

1. DHU (`desktop-head-unit` del SDK, modo desarrollador de Android Auto): abrir Orchard sin reproducción previa, recorrer los 3 niveles, buscar, tocar pista y álbum, play/pausa/siguiente/anterior/seek/shuffle/repeat.
2. Auto real: lo mismo con mandos del volante; desconectar y reconectar el teléfono y comprobar la tarjeta de reanudación.
3. Instalar desde GitHub Release sin "Fuentes desconocidas": la fila de Ajustes muestra las instrucciones.

## 9. Criterios de aceptación (checklist QA)

- [ ] V0 (post-build, vehículo real) registrado en `AUDIT.md` con el instalador observado y el resultado con "Fuentes desconocidas".
- [ ] Orchard se abre en el lanzador del auto sin el mensaje de no disponible.
- [ ] Inicio, Biblioteca (Me gusta, Playlists, Álbumes, Podcasts) y Recientes navegables; tocar cualquier ítem reproduce.
- [ ] Búsqueda en pantalla y por voz.
- [ ] Transporte completo desde pantalla y volante.
- [ ] Controlador no permitido no puede navegar (AUTO-INV-4).
- [ ] `./gradlew :app:testDebugUnitTest` y `assembleRelease` en verde en CI.
- [ ] Árbol de dependencias sin `gms`, `play-services` ni `car.app`.
- [ ] `AUDIT.md`, `UPSTREAM.md`, `technical_memory.md` actualizados.

## 10. Riesgos

| Riesgo | Mitigación |
| --- | --- |
| Google endurece la política y retira "Fuentes desconocidas" | Fuera del control del fork; se documenta. La tarjeta de medios y los mandos siguen funcionando |
| La causa raíz no es el origen de instalación | V0 en vehículo real tras el build; si falla, se reabre el diagnóstico de apertura en tarea aparte y la HU no se cierra. T1..T4 siguen siendo necesarios (G1..G8) |
| Cold start en el auto sin caché: la Biblioteca tarda | `refreshSignedInLibrary()` ya existe (`OrchardMediaLibrary.kt:113-119`); errores de 5.4 en vez de carpetas vacías |
| Lista fija de controladores deja fuera un host legítimo | Log de paquete rechazado (sin datos sensibles) y alta con test |

## 11. Decisiones del propietario

| ID | Pregunta | Decisión (2026-10-26) |
| --- | --- | --- |
| Q1 | Aceptar que la apertura depende de "Fuentes desconocidas" (D1) | Aprobada |
| Q2 | Media3 `MediaLibraryService` y no Car App Library (D2) | Aprobada |
| Q3 | Lista fija de controladores (D3) | Aprobada |
| Q4 | Aclaración de CAST-INV-1 (D4) | Aprobada y aplicada en `SDD-chromecast-castv2.md` |
| Q5 | V0 | Pasa a verificación en vehículo real posterior al build; no bloquea T1..T4 ni Q1..Q2 |
