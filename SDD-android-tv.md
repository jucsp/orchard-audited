# Contrato SDD — Android TV / Google TV, Opción A "Glass Rail" (HU-ORCH-11)

- **Versión del contrato:** 1.0 · 2026-10-18 · borrador para revisión del PO
- **Propietario:** juanca
- **Decisión de origen:** el propietario aprobó la Opción A "Glass Rail" (paquete de diseño del 2026-10-18; la dirección B "Escenario" queda descartada). Fuente: `orchard-tv-opcionA.zip` (SHA-256 `2a7936bc7a4e4ec7223505305b8c7bf0fad12ee6ffab50b37b3ac9514f885780`), cuyo `README.md` (SHA-256 `cba85394ee82152ef18e1db1e0c3d9612b89920d5260a7fe0f353116b88b90b7`) es la especificación de diseño. En este contrato se cita como "Spec §n".
- **Contrato padre:** `SDD-orchard-audit-fork.md`. Sus invariantes INV-1 a INV-7 siguen vigentes, igual que `CAST-INV-1` a `CAST-INV-9` de `SDD-chromecast-castv2.md`. Este documento agrega los invariantes `TV-INV-*`.
- **Base de código:** `audited` en `37a40ad` (rama de trabajo `integrate/hu-orch-11`).

---

## 1. Propósito

Que la app Android del fork se instale, aparezca en el launcher y se use cómodamente en Android TV y Google TV con el control remoto, sin tocar la lógica de reproducción, red ni datos:

- **Solo capa de presentación:** layouts TV nuevos, sistema de foco y navegación con D-pad. `OrchardViewModel` y los controladores (`SearchController`, `DetailController`, `AutoplayController`, cola, transporte, cuentas, Connect) se reutilizan sin cambios.
- **Sin dependencias propietarias ni telemetría:** nada de Google Play Services, Firebase ni librerías de analítica; ningún host de red nuevo.
- **Distribución por el fork:** el APK de TV se compila y publica en los GitHub Releases del fork, con la misma firma que el APK de teléfono.

Es una excepción del propietario a SDD §3.2 ("sin rediseños") y cierra para Android TV la decisión abierta de SDD §9 (opción B: mismo fork). La constancia en `AUDIT.md` queda pendiente (Q4).

## 2. Alcance

### 2.1 Dentro

1. Detección de TV en tiempo de ejecución (`isTvLayout()`) y árbol raíz `TvOrchardApp` con rail lateral contraído/expandido (Spec §3, §6).
2. Pantallas TV: Inicio (A1/A2), Reproduciendo con panel Letras/Cola/oculto (A3/A3b/A3c), Detalle de colección (A4), Buscar (A5) y Biblioteca (A6) (Spec §7).
3. `Modifier.tvFocus` con seis estilos, `focusRestorer`, foco inicial por pantalla y scroll que sigue al foco (Spec §5).
4. Mapa global del control remoto: D-pad, OK, mantener OK, Atrás y teclas multimedia vía la `MediaSession` existente (Spec §8).
5. Manifest leanback, banner del launcher y APK de TV en `.github/workflows/release.yml` (Spec §9, sección 5 de este contrato).
6. Adaptación mínima, sin rediseño, de Ajustes, Welcome, Login y diálogos del actualizador para que sean operables con D-pad (Spec §13).
7. Reproducción en TV sincronizada con Orchard Connect (`connect.kasper-cloud.com`) usando el cliente existente, sin cambios de protocolo (criterio de `backlog.md`, HU-ORCH-11).

### 2.2 Fuera

- Dirección B "Escenario".
- Funcionalidades nuevas, cambios de backend, del motor de audio, del modelo de datos o de los proveedores de contenido.
- Rediseño de Ajustes, Login, Welcome y video musical: se adaptan con el sistema de foco (Spec §13).
- Login por QR o código en otro dispositivo: requiere decisión de producto (D2).
- `androidx.leanback`, las listas `Tv*` de `androidx.tv:tv-foundation` (deprecadas) y `androidx.tv:tv-material` salvo justificación aprobada (TV-INV-4).
- Publicación en Google Play u otra tienda. La distribución es por GitHub Releases del fork.

## 3. Invariantes

| ID | Invariante | Cómo se verifica |
| --- | --- | --- |
| TV-INV-1 | **100 % D-pad.** Toda pantalla, flujo y diálogo alcanzable desde el launcher se recorre y opera solo con D-pad, OK, mantener OK, Atrás y teclas multimedia. Ninguna acción exige touch, gesto, arrastre, mouse ni teclado físico. El manifest declara `android.hardware.touchscreen` con `required="false"`. Atrás nunca cierra la app desde una pantalla interna | Smoke test de UI en emulador Android TV API 34 1080p (T12); checklist de la sección 7 en emulador y dispositivo real; recorrido independiente de T13 |
| TV-INV-2 | **Zona segura 48/27 dp.** Ningún texto, control ni elemento enfocable queda a menos de 48 dp de los bordes laterales ni de 27 dp de los bordes superior e inferior. Solo fondos, degradados y portadas decorativas pueden llegar al borde. El contenido empieza en x = 92 dp y el rail expandido se superpone sin redimensionarlo | `TvTokens` es la única fuente de los márgenes; test de UI que compara los límites de los nodos enfocables de cada pantalla con la zona segura; capturas lado a lado con los PNG (`S1-sistema-tv`) |
| TV-INV-3 | **Foco visible continuo y restaurable.** Con el árbol TV en pantalla siempre hay exactamente un elemento enfocado y su estado de foco se ve, también durante cargas (placeholders enfocables) y en diálogos (foco atrapado). Cada pantalla define su foco inicial. Cada estante, lista y grilla usa `focusRestorer()`. El estilo de foco sale solo de `Modifier.tvFocus` (`Card`, `Button`, `Row`, `Key`, `NavItem`, `Transport`); la escala se aplica con `graphicsLayer` y sube `zIndex`, sin cambiar el layout | Tests de UI de foco inicial y restauración por pantalla; `git grep` sin `onFocusChanged` ni bordes de foco propios fuera de `ui/tv/focus/`; revisión QA |
| TV-INV-4 | **Cero telemetría, cero dependencias propietarias, cero hosts nuevos.** Sin `com.google.android.gms`, `play-services-*`, Firebase ni SDK de analítica o crash reporting. Toda dependencia nueva lleva justificación en el PR, versión exacta fijada y aprobación de QA. La UI TV no abre conexiones propias: usa los repositorios existentes. La búsqueda por voz solo usa el `RecognizerIntent` del sistema, únicamente al pulsar el botón, y el botón no se muestra si ninguna actividad resuelve el intent. No se agrega el permiso `RECORD_AUDIO` | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` comparado con la base; `git grep` de `gms`, `firebase`, `analytics` en `mobile/`; revisión de URLs y sockets en `ui/tv/`; manifest fusionado sin `RECORD_AUDIO` |
| TV-INV-5 | **Gama baja y amplia compatibilidad.** Funciona en el dispositivo de referencia (Chromecast con Google TV de 2 GB de RAM) y Smart TVs con Android TV 9, 10 y 11 (como Philips PUD8507/43), con `minSdk` 28 (Android 9.0 Pie). El APK de TV incluye las ABI que ese dispositivo reporta (sección 5.2). En TV, `KawarpArtworkBackdrop` usa `renderScale = 0.5f` y `fullFrameRate = false`; el vidrio usa las variantes sólidas de `GlassFallback` si `ActivityManager.isLowRamDevice()` es verdadero, y nunca hay blur en vivo detrás de listas con scroll. Portadas a ~512 px en tarjetas y ~1024 px en hero y reproductor. Solo la barra de progreso y las letras leen `LocalPlayerClock` | `unzip -l` del APK de TV lista `lib/<abi>/` para cada ABI requerida; instalación y arranque en el dispositivo de referencia; métricas de jank del scroll de estantes (T12) |
| TV-INV-6 | **APK de TV en el pipeline del fork.** `.github/workflows/release.yml` genera `Orchard-AndroidTV-arm.apk` en cada ejecución, con el mismo keystore que el APK de teléfono. El job `publish` falla si falta, lo incluye en `SHA256SUMS.txt` y lo sube solo a los Releases de `jucsp/orchard-audited`. Acciones fijadas por SHA y `permissions` mínimos (REQ-A3, A6, A8) | Lista de comprobación de artefactos del job `publish`; un release real del fork con ambos APK y sus SHA-256 |
| TV-INV-7 | **El teléfono no cambia.** Con `isTvLayout()` en `false` se ejecuta el árbol actual sin cambios de comportamiento. El APK de teléfono `Orchard-Android-arm64-v8a.apk` conserva nombre, ABI y contenido funcional. No se modifican el motor de audio, la red, el modelo de datos, `OrchardViewModel` ni los controladores, salvo un cambio mínimo aprobado por QA y registrado en `technical_memory.md`. Las zonas protegidas de `UPSTREAM.md` quedan intactas, con la única excepción acotada de `UpdateManager.kt` en la sección 5.3 | `git diff --stat 37a40ad integrate/hu-orch-11` limitado a las rutas de la sección 4.6; tests JVM existentes en verde; `assembleRelease` del teléfono sin cambios de ABI |
| TV-INV-8 | **Actualizador con confirmación también en TV.** `UpdateAvailableDialog` y `UpdateProgressDialogs` se operan con D-pad, con foco inicial en "No". En TV se ofrece solo el APK de TV del release, elegido por nombre exacto, y se verifica su SHA-256 antes del instalador del sistema. Siguen vigentes INV-1 a INV-4 | Test JVM de selección de asset (teléfono → `Orchard-Android-arm64-v8a.apk`, TV → `Orchard-AndroidTV-arm.apk`, release sin el asset → sin oferta); prueba manual en emulador TV |
| TV-INV-9 | Archivos de menos de 500 líneas (`contributing.md`). Los archivos nuevos llevan cabecera AGPL-3.0-or-later; los del upstream que se modifican conservan la de SFG545. Comentarios cortos y sin narración histórica | Revisión QA |

Una implementación que viole un `TV-INV`, un `CAST-INV` o un `INV` del contrato padre no se integra.

## 4. Arquitectura

### 4.1 Decisiones de T0 (ADR)

| ID | Decisión | Motivo | Alternativa descartada |
| --- | --- | --- | --- |
| ADR-1 | **Un solo módulo y un solo código fuente**, con rama TV decidida en tiempo de ejecución por `isTvLayout()` dentro de `OrchardApp` | `mobile/android/settings.gradle` incluye solo `:app`; `app/build.gradle` no tiene `productFlavors`, solo los build types `debug`, `release`, `canary` y `canaryX86`. Es el mismo patrón que `isFoldableOrWideLayout()` (`ui/foldable/`). El cambio es de presentación y comparte ViewModel y controladores | Flavor `tv`: duplica las variantes (4 build types × 2), separa la UI TV del árbol que comparte estado y no aporta aislamiento real, porque la lógica es la misma |
| ADR-2 | **Un segundo artefacto de build, no un flavor**: el APK de TV sale del mismo código con otra lista de ABI (sección 5.2). `applicationId`, `versionCode`, `versionName` y firma iguales al APK de teléfono | El APK actual es solo `arm64-v8a` (`orchardReleaseAbis`). El Chromecast con Google TV, según reportes públicos, ejecuta un userspace de 32 bits (`armeabi-v7a`): en ese caso el APK de teléfono no se instala ahí. Con el mismo `applicationId` y la misma firma, cualquiera de los dos APK actualiza al otro | Agregar `armeabi-v7a` al APK de teléfono: aumenta su tamaño para todos los usuarios y rompe TV-INV-7 |
| ADR-3 | El manifest no fija orientación. `MainActivity` es compartida, y fijar `screenOrientation` afectaría al teléfono. En TV la pantalla ya es horizontal | Spec §9 pide "landscape fija en TV"; se cumple sin tocar el manifest | `screenOrientation="landscape"` en `MainActivity` |
| ADR-4 | Buscar es un destino de navegación propio en TV (`TvRoutes.SEARCH`), separado de `Routes.SEARCH` del teléfono, que hoy solo marca el overlay | Spec §6 | Reutilizar el overlay `SearchOverlay` |
| ADR-5 | Los mockups (PNG, HTML y `*.dc.html`) **no se versionan** en el repositorio. Las HTML cargan `fonts.googleapis.com`; se usan solo como referencia local para medir. Los PNG se adjuntan a los PR como comparación | Evita recursos con red externa en el árbol del fork | Copiar `mockups/` a `mobile/docs/` |

### 4.2 Detección

```kotlin
@Composable
fun isTvLayout(): Boolean {
    val ctx = LocalContext.current
    return remember {
        val ui = ctx.getSystemService(UiModeManager::class.java)
        ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
```

Se evalúa una vez por composición raíz. Sin override manual en ajustes (no está diseñado).

### 4.3 Paquete nuevo

`dev.sfg.orchard.mobile.ui.tv`, con la estructura de Spec §3.2 (`TvOrchardApp.kt`, `TvDetection.kt`, `theme/TvTokens.kt`, `focus/TvFocus.kt`, `rail/`, `home/`, `player/`, `detail/`, `search/`, `library/`). Ningún archivo supera 500 líneas (TV-INV-9).

### 4.4 Reutilización verificada contra el código

La spec nombra algunos símbolos que no existen o se llaman distinto en `37a40ad`. Rigen los nombres reales:

| Spec | En el código | Acción |
| --- | --- | --- |
| `mobile-compose/` | `mobile/android/app/` | Usar la ruta real |
| `ArtworkAmbience` | No existe; la extracción de paleta está en `ui/components/HomeBackdrop.kt` | Reutilizar `HomeBackdrop` |
| `LyricTranslationUi` | No existe; la lógica está en `lyrics/translation/LyricTranslator.kt` y la UI en `ui/screens/SettingsLyrics.kt` | Reutilizar sin tocar las zonas protegidas de HU-ORCH-09 |
| `R.font.inter_extrabold` | No incluido (`res/font/` tiene regular, medium, semibold y bold) | Usar `inter_bold`; no se agrega fuente |
| `GlassFallback` | `ui/glass/GlassFallback.kt` | Reutilizar |
| Manejo de teclas D-pad | No hay `onKeyEvent` ni `Key.Direction*` en la app; solo foco implícito por `clickable` (p. ej. `ChromecastSettingsRow.kt`) | Todo el manejo de teclas es nuevo y vive en `ui/tv/` |

Existen y se reutilizan sin cambios: `CanopyColors`, `LocalAccent`, `KawarpArtworkBackdrop` (ya acepta `renderScale` y `fullFrameRate`), `TrackActionsPopup`, `LyricPeek`, `LocalPlayerClock`, `PlayingBars`, `LosslessBadge`, `SmartCrossfadeBadge`, `QueueTrackRow`, `HomeSectionConfig`, `LibraryFilter`, `UpdateAvailableDialog` y `OrchardNavigationRail` (referencia de animación).

### 4.5 Manifest y recursos

- `<category android:name="android.intent.category.LEANBACK_LAUNCHER"/>` en el `intent-filter` MAIN de `.app.MainActivity`, junto al `LAUNCHER` existente.
- `<uses-feature>` con `required="false"` para `android.software.leanback`, `android.hardware.touchscreen` y los features implícitos de permisos ya declarados (`android.hardware.wifi` por `CHANGE_WIFI_MULTICAST_STATE`, `android.hardware.bluetooth` por `BLUETOOTH_CONNECT`), para no excluir equipos con solo Ethernet o sin Bluetooth.
- `android:banner="@drawable/tv_banner"` en `<application>`: 320×180 dp (640×360 px en `drawable-xhdpi`), según Spec §9. Recurso propio, sin texto adicional.
- No se tocan el permiso `CHANGE_WIFI_MULTICAST_STATE` ni los componentes de cast (zona protegida de HU-ORCH-12).

### 4.6 Superficie permitida del diff

`mobile/.../ui/tv/**`, el punto de bifurcación en `app/OrchardApp.kt`, `AndroidManifest.xml` (sección 4.5), `res/drawable-xhdpi/tv_banner.png`, `res/values*/strings.xml` (textos TV y `contentDescription`), `app/build.gradle` (solo ABI de la sección 5.2), `UpdateManager.kt` (solo sección 5.3), `.github/workflows/release.yml` (sección 5), tests nuevos y documentos de gobernanza. Cualquier otra ruta requiere aprobación de QA antes del commit.

## 5. Build y CI/CD

### 5.1 Job `android` de `release.yml`

Después de `assembleRelease` del teléfono, el mismo job compila el APK de TV con la propiedad Gradle `-PorchardTvBuild=true`, que solo cambia `abiFilters` del build type `release`. Mismo keystore preparado en el paso "Prepare release keystore", mismo `versionCode`. El paso "Collect APK" copia ambos:

- `Orchard-Android-arm64-v8a.apk` (sin cambios)
- `Orchard-AndroidTV-arm.apk` (nuevo)

Se sube un solo artefacto `orchard-android` con los dos archivos. El keystore temporal se borra después del segundo build, no antes.

### 5.2 ABI del APK de TV

- `Orchard-AndroidTV-arm.apk` incluye `armeabi-v7a` y `arm64-v8a`, para cubrir equipos de 32 y 64 bits con un solo archivo.
- T0 confirma la lista con `adb shell getprop ro.product.cpu.abilist` en el dispositivo de referencia y la registra en `technical_memory.md`. Si el dispositivo reporta `arm64-v8a`, el APK de TV mantiene las dos ABI igual.
- Agregar `armeabi-v7a` exige: entrada `'armeabi-v7a': 'arm-linux-androideabi'` en `orchardStlTriples`, target Rust `armv7-linux-androideabi` en el paso "Install Rust Android targets and cargo-ndk", y verificar que cada librería nativa tenga esa ABI. Confirmado: el AAR de Discord trae `jni/armeabi-v7a/`. Pendiente de verificar en T0: Earmark (Rust), el módulo CMake, `kopus` y `onnxruntime-android`.
- Si alguna librería nativa no compila para `armeabi-v7a`, se detiene la tarea y se eleva al PO. Deshabilitar la función nativa en TV es una decisión de producto, no de implementación.

### 5.3 Selección del APK en el actualizador

`UpdateManager.parseGitHubReleasesMetadata` toma hoy el **primer** asset que termina en `.apk`. Con dos APK por release, un televisor podría recibir el APK solo `arm64-v8a` y el instalador fallaría. Es una zona protegida del fork (INV-3), así que el cambio queda acotado a:

- Elegir el asset por nombre exacto: `Orchard-AndroidTV-arm.apk` si `isTvDevice` (misma condición de la sección 4.2, sin Compose), o `Orchard-Android-arm64-v8a.apk` si no.
- Si el release no trae el asset del tipo de equipo, no se ofrece actualización.
- No se cambian el origen (repo del fork), la confirmación, la verificación SHA-256 ni el anti-rollback.

Lo implementa DeepSeek (T14) con test JVM, y QA lo audita línea a línea antes de integrar.

### 5.4 Job `publish`

- Agregar `Orchard-AndroidTV-arm.apk` a la lista de archivos exigidos en "Check artifacts and write SHA256SUMS.txt".
- Agregar una línea a las notas: `Android TV: armeabi-v7a + arm64-v8a APK para Android TV / Google TV`.
- Sin jobs ni permisos nuevos.

### 5.5 Smoke test de UI

Un job aparte `android-tv-smoke` (no bloquea `publish`; se ejecuta en `push` y `workflow_dispatch`), con emulador Android TV API 34 1080p usando una acción fijada por SHA y `permissions: contents: read`. Verifica que la app arranca en layout TV, el foco inicial está en "Reproducir" del hero y el rail se abre con ◀. Si el emulador TV no es estable en `ubuntu-24.04`, se registra como limitación conocida (REQ-A7) y el smoke test queda manual.

## 6. Desglose para el Swarm

Basado en Spec §12, con responsables del Swarm.

| ID | Tarea | Responsable | Entrega | Depende de |
| --- | --- | --- | --- | --- |
| T0 | Confirmar ADR-1 a ADR-5, ABI del dispositivo de referencia, librerías nativas en `armeabi-v7a`, store de preferencias para `PanelState` y `MediaSession` para teclas globales | Claude PM/QA | ADR en `technical_memory.md`; sin cambios funcionales | — |
| T1 | `isTvLayout()`, `TvOrchardApp` vacío, manifest leanback y banner | DeepSeek Dev | La app aparece en el launcher TV y abre fondo `Chrome` | T0 |
| T2 | `TvTokens` y `Modifier.tvFocus` con los seis estilos, más pantalla de prueba | DeepSeek Dev | Captura contra `S1-sistema-tv.png` | T1 |
| T3 | `TvNavigationRail` y NavHost TV (Inicio, Buscar, Biblioteca, Ajustes, Detalle, Reproduciendo) | DeepSeek Dev | A1/A2; reglas de Spec §6 | T2 |
| T4 | Inicio: hero, estantes, tarjetas, fondo ambiental | DeepSeek Dev | A1 | T3 |
| T5 | Reproductor: columna, transporte, barra de progreso enfocable | DeepSeek Dev | A3b sin panel | T3 |
| T6 | Panel Letras/Cola, pestañas de borde, máquina de estados y persistencia | DeepSeek Dev | A3, A3b, A3c; tabla D-pad de Spec §7.2 | T5 |
| T7 | Detalle de colección | DeepSeek Dev | A4 | T3 |
| T8 | Buscar: teclado, voz (TV-INV-4), resultados | DeepSeek Dev | A5 | T3 |
| T9 | Biblioteca: filtros, grilla, vista de canciones | DeepSeek Dev | A6 | T3 |
| T10 | Teclas multimedia globales, mantener OK, Atrás global, diálogos operables con D-pad | DeepSeek Dev | Spec §8; TV-INV-1, TV-INV-8 | T4–T9 |
| T11 | Ajustes, Login y Welcome con el sistema de foco, sin rediseño | DeepSeek Dev | Todas las opciones alcanzables con D-pad | T2 |
| T14 | Selección de asset por tipo de equipo en `UpdateManager` (sección 5.3) | DeepSeek Dev | Cambio acotado + test JVM | T0 |
| Q1 | `release.yml`: segundo APK, ABI, `publish` y smoke test (sección 5) | Claude PM/QA | Workflow | T1, T14 |
| Q2 | Rendimiento (TV-INV-5) y métricas de jank | Claude PM/QA | Informe | T4–T10 |
| Q3 | QA: verificación de TV-INV-1..9, tests y checklist de la sección 7 | Claude PM/QA | Informe en `AUDIT.md` | Q1, Q2 |
| Q4 | Auditoría: excepción a SDD §3.2 y cierre de §9 en `AUDIT.md`; zonas protegidas nuevas en `UPSTREAM.md`; `technical_memory.md` | Claude PM/QA | Docs | Q3 |
| T13 | Revisión independiente: comparar cada pantalla con su PNG y recorrer los flujos solo con D-pad | Agente que no participó en T1–T11 | Informe de diferencias sin bloqueantes | todo |
| I1 | Orden de commits `audit-fork:` (uno por tarea) en `integrate/hu-orch-11` | Antigravity PO | Rama | T1..Q4 |

Cada tarea de DeepSeek se entrega con capturas del emulador Android TV 1080p junto al PNG correspondiente y se valida por Claude antes de la siguiente. DeepSeek no toca `build.gradle`, `release.yml` ni el manifest fuera de T1. Ninguna tarea toca el motor de audio, la red ni el modelo de datos sin aprobación de QA.

## 7. Criterios de aceptación

- [ ] TV-INV-1: toda la app navegable solo con D-pad, OK y Atrás en emulador Android TV 1080p (API 34) y en el dispositivo de referencia.
- [ ] La app aparece en el launcher de Android TV / Google TV con su banner.
- [ ] TV-INV-2: ningún contenido crítico fuera de la zona segura de 48/27 dp.
- [ ] TV-INV-3: foco visible en todo momento; `focusRestorer` funciona en todos los estantes, listas y grillas; foco inicial correcto en cada pantalla.
- [ ] Panel del reproductor: los tres estados y todas las transiciones de Spec §7.2, con la preferencia persistida tras reiniciar la app.
- [ ] Teclas multimedia del control funcionan fuera del reproductor (FF/RW = ±10 s).
- [ ] El acento respeta "colores del sistema" (`LocalAccent`).
- [ ] Reproducción continua en TV sincronizada con Orchard Connect.
- [ ] TV-INV-4: árbol de dependencias sin `gms`, `play-services`, Firebase ni analítica; sin hosts nuevos; sin `RECORD_AUDIO`.
- [ ] TV-INV-5: el APK de TV se instala y arranca en el dispositivo de referencia; scroll de estantes sin jank visible.
- [ ] TV-INV-6: release del fork con `Orchard-Android-arm64-v8a.apk` y `Orchard-AndroidTV-arm.apk` firmados y listados en `SHA256SUMS.txt`.
- [ ] TV-INV-7: el APK de teléfono mantiene nombre y ABI; tests JVM existentes en verde.
- [ ] TV-INV-8: diálogo de actualización operable con D-pad, foco inicial en "No", y en TV se ofrece solo el APK de TV.
- [ ] Capturas lado a lado con los PNG adjuntas a cada PR; informe T13 sin bloqueantes.
- [ ] `AUDIT.md`, `UPSTREAM.md` y `technical_memory.md` actualizados (Q4).

## 8. Decisiones abiertas del propietario

| ID | Decisión | Propuesta del Swarm |
| --- | --- | --- |
| D1 | Biblioteca "Local" en TV (Spec §14.1) | Ocultar `LOCAL` en TV |
| D2 | Login en TV: WebView actual (`NativeLoginScreen.kt`, `QobuzLoginScreen.kt`, `SpotifyLoginScreen.kt`) o login por QR/código (Spec §14.2) | Aceptar el WebView en esta HU y abrir una HU aparte para QR si resulta inusable con D-pad |
| D3 | Idioma de la UI (Spec §14.3) | Seguir la localización actual del fork; los mockups en español son referencia visual |
| D4 | Búsqueda por voz: el reconocedor del sistema en Google TV envía el audio a Google. Es una acción del usuario y no hay código de Google en el APK | Mantener el botón solo al pulsarlo (TV-INV-4) y documentarlo en el README del fork |

Ninguna de estas decisiones bloquea T0–T3. D1 bloquea T9; D2 bloquea T11.

## 9. Riesgos

| Riesgo | Mitigación |
| --- | --- |
| El Chromecast con Google TV usa userspace de 32 bits y una librería nativa no compila para `armeabi-v7a` | Verificación en T0 (sección 5.2); si falla, se eleva al PO antes de implementar |
| Con dos APK por release, el actualizador ofrece el equivocado | Selección por nombre exacto (sección 5.3) y test JVM |
| WebView de login incómodo o inoperable con D-pad | D2; reportar con captura sin rediseñar |
| Jank en el dispositivo de 2 GB por Kawarp, vidrio o portadas grandes | TV-INV-5; Q2 mide antes de cerrar |
| Pantallas no diseñadas (video musical, Connect, Party, ecualizador, estados vacíos) se rompen a 960 dp | Reutilizar con `tvFocus` y reportar con captura (Spec §13) |
| El emulador Android TV no corre estable en Actions | Smoke test manual documentado como limitación conocida (REQ-A7) |
| `release.yml` se dispara también por `push` a `audited` y `main`, más amplio que REQ-A4 | Observación previa a esta HU, fuera de su alcance; queda registrada para que el PO decida aparte |
| Ningún dispositivo de referencia en CI | La prueba en hardware real es manual y la registra el propietario |
