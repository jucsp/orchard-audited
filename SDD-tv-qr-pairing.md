# Contrato SDD — Vinculación TV ↔ teléfono por QR y LAN (HU-ORCH-13)

- **Versión del contrato:** 1.0 · 2026-10-19 · borrador para revisión del propietario
- **Propietario:** juanca
- **Autor:** Claude (PM/QA)
- **Contratos padre:** `SDD-orchard-audit-fork.md` (INV-1 a INV-7), `SDD-android-tv.md` (TV-INV-1 a TV-INV-9) y `SDD-chromecast-castv2.md` (CAST-INV-*). Siguen vigentes. Este documento agrega los invariantes `PAIR-INV-*` y pide dos enmiendas acotadas a TV-INV-4 y TV-INV-7 (sección 4, D6).
- **Base de código:** `integrate/hu-orch-11-tv-full` (HU-ORCH-11b). Rama de trabajo: `integrate/hu-orch-13-tv-qr-login`.
- **Backlog:** HU-ORCH-13 en `backlog.md`.

---

## 1. Propósito

En el televisor de referencia (Philips 65PUD8507/43, Android TV) el login de YouTube en WebView (`NativeLoginScreen`) no se puede completar con el control remoto cuando Google pide verificación en dos pasos, passkey o selección de método: el foco del D-pad no alcanza esos elementos. La TV queda sin cuenta.

La solución es que el teléfono, donde el usuario ya inició sesión con pantalla táctil, **transfiera su sesión a la TV por la red local**, cifrada de extremo a extremo y solo tras confirmación explícita. La TV muestra un QR y un código; el teléfono la encuentra, el usuario confirma y la TV recibe la sesión. El login por WebView queda como alternativa.

Es funcionalidad nueva: requiere la misma excepción del propietario a SDD §3.2 que HU-ORCH-11 (D2 de `SDD-android-tv.md` la dejó pendiente; este contrato la resuelve si se aprueba).

## 2. Alcance

### 2.1 Dentro

1. `TvQrLoginScreen` en la ruta `TvRoutes.LOGIN` de la TV: QR, código de verificación, temporizador, estado y botón "Iniciar sesión con teclado/mouse" que abre `NativeLoginScreen`.
2. `TvPairingServer`: servidor TCP efímero en la TV, solo LAN, de un solo uso.
3. Anuncio NSD `_orchard-pair._tcp` mientras `TvQrLoginScreen` está visible.
4. En el teléfono: fila "Vincular con Android TV" en Ajustes, descubrimiento NSD, entrada por deep link del QR, diálogo de confirmación y envío cifrado.
5. Carga útil: `YouTubeSession` (obligatoria) y `sp_dc` de Spotify (opcional, casilla desmarcada por defecto).
6. Tests JVM de cifrado, serialización, parseo del QR y validación del servidor.

### 2.2 Fuera

- Qobuz, Orchard Connect, Last.fm, ListenBrainz y Discord: no se transfieren.
- Escáner QR dentro de la app (cámara propia, CameraX, permiso `CAMERA`): el QR se lee con la cámara del sistema (D2).
- Vinculación por Internet, relés, servidores de rendezvous o nubes de terceros.
- Transferencia TV → teléfono y sincronización continua de sesión. La transferencia es única y manual.
- Cambios en `NativeYouTubeAuthRepository`, `SecureYouTubeSessionStore` y el motor de reproducción, salvo lo indicado en 6.3.
- Escritorio (Qt/QML).

## 3. Invariantes

| ID | Invariante | Cómo se verifica |
| --- | --- | --- |
| PAIR-INV-1 | **Cero fuga externa.** Cookies, `sp_dc`, claves y tokens solo viajan entre el teléfono y la TV por la LAN, dentro de un sobre AES-256-GCM con clave efímera derivada por ECDH. Ningún host de Internet participa. El teléfono solo se conecta a direcciones IPv4 privadas (RFC 1918) o link-local; rechaza loopback, públicas, multicast e IPv6 global. El QR nunca contiene la clave de cifrado ni datos de la sesión | Test JVM de `PairingUri` con direcciones válidas e inválidas; `git grep` de URLs en `pairing/`; revisión QA del flujo de bytes; captura de tráfico en la prueba de dispositivo (solo payload cifrado en el cable) |
| PAIR-INV-2 | **Consentimiento explícito.** El teléfono no envía nada hasta que el usuario pulsa "Vincular" en un diálogo que muestra el nombre del televisor, la cuenta que se transfiere, la casilla de Spotify y, en modo código, el código de verificación a comparar. La TV no aplica la sesión hasta que el usuario pulsa OK en un diálogo que muestra la cuenta recibida. Foco inicial en "Cancelar" en ambos | Test de UI o de estado: sin pulsar confirmar no hay `POST /v1/session`; revisión QA; prueba manual |
| PAIR-INV-3 | **Cero SDK propietarios.** Sin `com.google.android.gms`, `play-services-*`, ML Kit, Firebase ni analítica. NSD con `android.net.nsd.NsdManager`; criptografía con JCA (`KeyPairGenerator "EC"`, `KeyAgreement "ECDH"`, `Cipher "AES/GCM/NoPadding"`, `Mac "HmacSHA256"`, `SecureRandom`). Única dependencia nueva admisible: el codificador QR de D3, con versión fijada y aprobación del propietario | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` comparado con la base; `git grep -nE 'gms\|firebase\|mlkit\|analytics' mobile/` |
| PAIR-INV-4 | **Efímero y limpio.** El servidor de la TV escucha en un puerto efímero (`port 0`) ligado solo a la IPv4 de la interfaz Wi-Fi/Ethernet activa, nunca a `0.0.0.0`. Usa un token aleatorio de 128 bits (`SecureRandom`) y un par ECDH nuevo por sesión. Vida máxima 5 minutos. Se apaga y retira el anuncio NSD al recibir una sesión válida, al cancelar, al salir de la pantalla (`ON_STOP` o `onDispose`), al expirar o tras 5 fallos. Al regenerar, todo el material criptográfico es nuevo | Tests JVM del ciclo de vida de `TvPairingServer` (expira, se apaga tras éxito, tras 5 fallos, tras `close()`); `ss -ltn` en el dispositivo antes y después |
| PAIR-INV-5 | **Canal autenticado.** El teléfono autentica a la TV antes de enviar: en modo QR, por la huella SHA-256 completa de la clave pública de la TV contenida en el QR; en modo código, por la comparación del código de verificación (SAS) derivado del handshake con compromiso previo. La TV autentica al teléfono por el token (QR) o por la confirmación en pantalla (código). Sin autenticación no hay clave de sesión | Tests JVM: huella distinta → abortar; compromiso que no coincide → abortar; MITM simulado produce SAS distintos |
| PAIR-INV-6 | **Entradas acotadas.** Una petición por conexión, cabeceras ≤ 8 KiB, cuerpo ≤ 64 KiB, timeout de lectura 10 s, máximo 2 conexiones simultáneas. JSON validado con esquema estricto: versión `v=1`, campos y longitudes conocidos, `cookie` aceptada por `YouTubeSessionAuth.loginCookieValue`. Comparaciones de token y compromiso en tiempo constante (`MessageDigest.isEqual`) | Tests JVM con cuerpos sobredimensionados, JSON malformado, versión desconocida, token incorrecto |
| PAIR-INV-7 | **Sin rastro de secretos.** Cookie, `sp_dc`, token, claves, SAS y payload nunca se registran en logcat, `toString()`, excepciones, analítica, portapapeles ni almacenamiento sin cifrar. Las clases de datos con secretos redefinen `toString()`. La sesión recibida solo se persiste por la ruta existente (`completeSignIn` → `SecureYouTubeSessionStore`). Los `ByteArray` de claves se sobrescriben al cerrar | `git grep -n 'Log\.' pairing/` revisado línea a línea; test de `toString()` sin secretos |
| PAIR-INV-8 | Archivos de menos de 500 líneas, cabecera AGPL-3.0-or-later en archivos nuevos, comentarios cortos sin narración histórica (`contributing.md`). En TV, toda la UI cumple TV-INV-1 a TV-INV-3 | Revisión QA |

Una implementación que viole un `PAIR-INV`, un `TV-INV`, un `CAST-INV` o un `INV` del contrato padre no se integra.

## 4. Decisiones (ADR)

**D1. ECDH P-256 en lugar de una clave AES en el QR.** El backlog dice "clave efímera transmitida en el QR". Se cambia por: la TV genera un par ECDH P-256 efímero y el QR lleva solo la huella SHA-256 de su clave pública y el token. Motivos: (a) el modo código no tiene canal óptico y no podría transportar una clave; (b) la app de cámara del sistema (a veces Google Lens) procesa y puede guardar el contenido del QR, así que el QR no debe llevar secretos que descifren la sesión; (c) un solo protocolo para ambos modos. La clave AES-256-GCM sigue siendo efímera, única por sesión y nunca sale de los dos dispositivos. P-256 porque X25519 no está garantizado en JCA con `minSdk 28`.

**D2. QR leído por la cámara del sistema mediante deep link.** El QR codifica `orchard://pair?...`, esquema que el manifest ya declara. El teléfono no pide `CAMERA` ni incluye decodificador. Si el usuario no tiene app de cámara con lector QR, usa el modo código. Un escáner integrado (CameraX + decodificador libre) queda fuera y requiere HU propia.

**D3. Codificador QR (pendiente del propietario).** La TV necesita generar la matriz QR para dibujarla en `Canvas`.
- **Opción A (recomendada):** `com.google.zxing:core:3.5.3`, solo `QRCodeWriter`. Apache-2.0, Java puro, sin red, sin Play Services (el prefijo `com.google` es solo el nombre del grupo Maven). Ampliamente auditada. R8 elimina lo no usado. Se fija la versión exacta y se registra su SHA-256 en `AUDIT.md`.
- **Opción B:** codificador propio en Kotlin (modo byte, corrección M, versiones 1 a 6, ocho máscaras). Cero dependencias, pero unas 400 líneas de lógica propensa a errores y que hay que probar contra vectores conocidos.
Hasta la decisión, DeepSeek implementa detrás de la interfaz `QrMatrixEncoder` para que el cambio de opción sea local.

**D4. Modo código con SAS, no con código tecleado.** La TV no muestra un código para teclear en el teléfono. Un código de 6 caracteres tecleado sin un PAKE se puede romper offline en segundos: un atacante en la LAN que suplanta a la TV recibe datos cifrados con una clave derivada del código y prueba el millón de combinaciones. Esto violaría PAIR-INV-1 y PAIR-INV-5, así que no se ofrece. En su lugar, el modo código usa comparación de código corto autenticado (SAS, como Bluetooth "comparación numérica" o ZRTP): el teléfono elige la TV en la lista NSD, se hace el handshake con compromiso y **ambas pantallas muestran el mismo código `784-291`**. El usuario confirma en el teléfono que coincide. Un MITM tiene una probabilidad de 10⁻⁶ por intento y solo online. El código de la TV cumple así lo pedido ("código legible de 6 caracteres"), pero aparece cuando el teléfono se conecta, no antes.

**D5. Transporte HTTP/1.1 mínimo sobre `ServerSocket`.** Mismo patrón que `ChromecastStreamServer`, sin servidor HTTP de terceros. Cleartext a nivel de transporte; la confidencialidad la da el sobre AES-GCM. En el teléfono se usa un `Socket` directo (o OkHttp con el `usesCleartextTraffic` ya presente), sin ampliar la configuración de red.

**D6. Enmiendas acotadas a `SDD-android-tv.md` (pendiente del propietario).**
- TV-INV-4 dice "la UI TV no abre conexiones propias". `TvPairingServer` abre un socket de escucha LAN. Enmienda propuesta: "salvo `TvPairingServer` (HU-ORCH-13), bajo PAIR-INV-1 y PAIR-INV-4". El código de red vive en `mobile/.../pairing/`, no en `ui/tv/`.
- TV-INV-7 dice "el teléfono no cambia". La fila de Ajustes y el flujo de envío lo cambian. Enmienda: "salvo la vinculación con TV de HU-ORCH-13".

## 5. Protocolo `orchard-pair-v1`

### 5.1 Material por sesión (TV)

| Elemento | Valor |
| --- | --- |
| Par ECDH | P-256, `KeyPairGenerator("EC")` con `ECGenParameterSpec("secp256r1")`. Pública en formato X.509 SubjectPublicKeyInfo (`encoded`) |
| `fp` | SHA-256 de la pública codificada (32 bytes) |
| `T` | Token, 16 bytes de `SecureRandom` |
| `sid` | Id de sesión, 8 bytes de `SecureRandom` |
| `nT` | Nonce de la TV, 16 bytes |
| Puerto | `ServerSocket(0, 2, ipv4Wifi)` |
| Expiración | Creación + 300 s |

Codificación binaria en JSON y URI: base64url sin relleno.

### 5.2 QR y NSD

- **QR:** `orchard://pair?v=1&h=<ipv4>&p=<puerto>&t=<T>&f=<fp>&n=<nombre TV, URL-encoded, ≤ 40 car.>`. Corrección de errores M. Entre 140 y 158 bytes: versión 8 o 9, hasta 61 módulos con la zona silenciosa (unos 5,9 dp por módulo en 360 dp). Lo verifica `PairingUriTest`.
- **NSD:** tipo `_orchard-pair._tcp`, nombre `Orchard TV <modelo> <4 hex aleatorios>`, TXT solo `v=1`. El anuncio NSD **no** lleva `T` ni `fp`. Se registra al mostrarse la pantalla y se retira al apagarse el servidor.

### 5.3 Intercambio

Una petición por conexión. Respuestas `application/json`. `mode` es `qr` o `sas`.

1. **`POST /v1/hello`**: teléfono → `{v:1, mode, t?, c}`, donde `c = SHA-256(phonePub)` (compromiso) y `t` solo en modo `qr`.
   - TV: en `qr`, compara `t` con `T` en tiempo constante; en `sas` no hay token. Si ya hay un teléfono enlazado a la sesión → `409`. Si no, guarda `c` y responde `{v:1, sid, tvPub, nT, name}`.
   - Teléfono, modo `qr`: verifica `SHA-256(tvPub) == f` del QR. Si no coincide, aborta sin enviar nada más.
2. **`POST /v1/key`**: teléfono → `{v:1, sid, phonePub, nP}` (`nP` de 16 bytes).
   - TV verifica `SHA-256(phonePub) == c`. Ambos calculan:
     - `Z = ECDH(priv, pubOtro)`
     - `th = SHA-256("orchard-pair-v1" ‖ mode ‖ tvPub ‖ phonePub ‖ nT ‖ nP ‖ T)` (en `sas`, `T` vacío; cada campo con prefijo de longitud de 2 bytes)
     - `prk = HKDF-Extract(salt = th, IKM = Z)`
     - `k = HKDF-Expand(prk, "orchard-pair-v1 key phone->tv", 32)`
     - `sas = HKDF-Expand(prk, "orchard-pair-v1 sas", 4)` como entero sin signo módulo 10⁶, formato `ddd-ddd`
   - TV responde `{v:1, ok:true}` y, en modo `sas`, muestra el código.
3. **Confirmación en el teléfono** (PAIR-INV-2). En `sas`, el diálogo muestra el código y pregunta si coincide con el de la TV.
4. **`POST /v1/session`**: teléfono → `{v:1, sid, iv, ct}`, con `iv` de 12 bytes aleatorios y `ct = AES-256-GCM(k, iv, payload, AAD = th)`, etiqueta de 128 bits.
   - TV descifra, valida el esquema (6.4) y responde `{v:1, status:"pending_tv_confirm"}`. Apaga el servidor y muestra "¿Iniciar sesión como <displayName>?" con foco en "Cancelar".
   - OK → `completeSignIn(...)` y, si viene, `spotifySpdc`. Cancelar → descarta y regenera la sesión.

HKDF se implementa con `Mac("HmacSHA256")`, reutilizando o extrayendo la lógica de `provider/MediaCrypto.kt` sin cambiar su API pública.

### 5.4 Payload (texto claro antes de cifrar)

```json
{
  "v": 1,
  "youtube": {
    "cookie": "…",
    "visitorData": "…",
    "dataSyncId": "…",
    "accountIndex": 0,
    "displayName": "…",
    "avatarUrl": "…"
  },
  "spotify": { "spDc": "…" }
}
```

`spotify` se omite si la casilla no está marcada. Límites: `cookie` ≤ 16 KiB, `sp_dc` ≤ 1 KiB, demás campos ≤ 512 bytes, payload ≤ 32 KiB. Campos desconocidos → rechazo.

### 5.5 Errores y límites

| Condición | Respuesta TV | Cuenta como fallo |
| --- | --- | --- |
| Token incorrecto, compromiso que no coincide, `sid` desconocido, GCM inválido, esquema inválido | `403` o `400`, cuerpo `{v:1, error:"<código>"}` sin detalles | Sí |
| Segundo teléfono con sesión ya enlazada | `409` | No |
| Sesión expirada o servidor apagándose | conexión cerrada | No |
| Cuerpo o cabeceras sobre el límite, timeout | conexión cerrada | Sí |

Con 5 fallos el servidor se apaga y `TvQrLoginScreen` regenera todo el material (nuevo QR). Un fallo de GCM o compromiso aborta la sesión enlazada de inmediato.

## 6. Arquitectura

### 6.1 Paquete `dev.sfg.orchard.mobile.pairing` (común, JVM-testeable)

| Archivo | Responsabilidad |
| --- | --- |
| `PairingCrypto.kt` | Generación P-256, ECDH, HKDF, `th`, `k`, SAS, sellado/apertura AES-GCM. Sin dependencias de Android |
| `PairingMessages.kt` | Modelos de `hello`, `key`, `session` y payload; serialización y validación estricta con `org.json`; `toString()` sin secretos |
| `PairingUri.kt` | Construcción y parseo de `orchard://pair`; validación de IPv4 privada, puerto 1024–65535, longitudes |
| `TvPairingServer.kt` | `ServerSocket`, máquina de estados (`Waiting → Bound → Received → Closed`), límites, contador de fallos, expiración, `close()` idempotente. Entrega el payload por callback; no toca auth |
| `PairingClient.kt` | Cliente del teléfono: `hello`, verificación de huella, `key`, cálculo de SAS, `session` |
| `PairingAdvertiser.kt` | Registro y retiro NSD en la TV |
| `PairingDiscovery.kt` | Descubrimiento y resolución NSD en el teléfono (patrón de `cast/CastDiscovery.kt`, resoluciones en serie, `MulticastLock` solo mientras busca) |
| `QrMatrixEncoder.kt` | Interfaz que devuelve una matriz booleana; implementación según D3 |

### 6.2 Android TV (`ui/tv/pairing/`)

- **`TvQrLoginScreen.kt`**: QR dibujado en `Canvas` (módulos negros sobre blanco, zona silenciosa de 4 módulos, ≥ 360 dp de lado), instrucciones, nombre del TV, temporizador `mm:ss`, estado ("Esperando teléfono", "Compara el código 784-291 en tu teléfono", "Sesión recibida"). Botones enfocables: "Generar código nuevo" e "Iniciar sesión con teclado/mouse" (navega a `TvRoutes.LOGIN_WEB`). `DisposableEffect` y observador de ciclo de vida que llaman `server.close()`.
- **`TvPairingConfirmDialog.kt`**: "¿Iniciar sesión como <cuenta>?" con "Cancelar" (foco inicial) e "Iniciar sesión". Estilos de foco solo de `Modifier.tvFocus`.
- **`TvRoutes`**: `LOGIN` apunta a `TvQrLoginScreen`; se agrega `LOGIN_WEB` con el `NativeLoginScreen` actual. `ACCOUNT_SWITCH` no cambia.
- Sin Wi-Fi/Ethernet con IPv4: la pantalla lo indica y ofrece solo el login web.

### 6.3 Teléfono

- **Ajustes:** fila "Vincular con Android TV" en la página de conexiones (`SettingsConnections.kt` / `SettingsPages.kt`), visible solo con sesión de YouTube iniciada.
- **`PairWithTvScreen.kt`** (`ui/screens/`): lista NSD con estado de búsqueda; al elegir una TV → modo `sas`. Botón de ayuda: "O escanea el QR de la TV con la cámara".
- **`PairConfirmDialog.kt`**: TV, cuenta, casilla "Incluir Spotify" (desmarcada, oculta sin `spotifySpdc`), código SAS en modo `sas`; "Cancelar" con foco inicial.
- **Deep link:** `MainActivity` enruta `orchard://pair` a `PairWithTvScreen` en modo `qr` con la URI ya validada por `PairingUri`. En TV (`isTvLayout()`) el deep link se ignora.
- La sesión se lee de `NativeYouTubeAuthRepository.session()` y `sp_dc` de `SettingsRepository`. No se modifican esas clases.

### 6.4 Aplicación de la sesión en la TV

`viewModel.completeSignIn(cookie, visitorData, dataSyncId, accountIndex, pageAvatarUrl = avatarUrl)` (misma llamada que `NativeLoginScreen`) y, si hay `spDc`, `viewModel.updateSettings(settings.copy(spotifySpdc = spDc))` (misma ruta que `OrchardNavigation.kt`). Luego `nav.popBackStack(TvRoutes.LOGIN, inclusive = true)`.

### 6.5 Rutas tocadas

`mobile/android/app/src/main/java/dev/sfg/orchard/mobile/pairing/` (nuevo), `ui/tv/pairing/` (nuevo), `ui/tv/TvRoutes.kt`, `ui/tv/TvOrchardApp.kt`, `ui/screens/PairWithTvScreen.kt` y `PairConfirmDialog.kt` (nuevos), una fila en `SettingsConnections.kt` o `SettingsPages.kt`, ruta en `app/OrchardNavigation.kt`, enrutado del deep link en `dev/sfg/orchard/connect/app/MainActivity.kt` (zona protegida: cambio aditivo, revisión línea a línea), `build.gradle` solo si D3 = A, tests en `src/test/.../pairing/`. Las rutas nuevas se agregan a **Zonas protegidas** de `UPSTREAM.md` en el mismo commit.

## 7. Pruebas

### 7.1 JVM (`src/test/java/dev/sfg/orchard/mobile/pairing/`)

| Test | Casos mínimos |
| --- | --- |
| `PairingCryptoTest` | Ida y vuelta ECDH+HKDF+GCM; ambos lados derivan `k` y SAS iguales; SAS en formato `ddd-ddd`; `th` cambia con cualquier campo; GCM con AAD distinta, `iv` distinto o un bit alterado falla; MITM simulado (dos handshakes con claves del atacante) da SAS distintos con alta probabilidad; vector fijo HKDF RFC 5869 caso 1 |
| `PairingMessagesTest` | Serialización/parseo de cada mensaje; rechazo de `v ≠ 1`, campos desconocidos, campos faltantes, longitudes excedidas, cookie sin SAPISID; `toString()` sin cookie, token ni claves |
| `PairingUriTest` | URI válida; rechazo de IP pública, loopback, multicast, hostname, IPv6, puerto fuera de rango, `t` o `f` de longitud incorrecta, esquema o host distintos |
| `TvPairingServerTest` | Socket real en loopback inyectado para test: flujo completo `qr` y `sas`; token incorrecto → 403 y contador; 5 fallos → cerrado; segundo cliente → 409; cuerpo de 65 KiB → cerrado; expiración con reloj inyectado; `close()` idempotente y libera el puerto; tras éxito el servidor ya no acepta conexiones |
| `PairingClientTest` | Huella del QR que no coincide → aborta antes de `/v1/key`; sin confirmación no se envía `/v1/session` |

El servidor recibe `InetAddress` y reloj inyectados para que los tests usen `127.0.0.1`; en producción la validación de PAIR-INV-4 exige interfaz Wi-Fi/Ethernet.

### 7.2 Manual en dispositivo

Philips 65PUD8507/43 + teléfono Android en la misma Wi-Fi: modo QR con cámara del sistema; modo código con lista NSD; cancelar en cada paso; salir de la pantalla y comprobar con `adb shell ss -ltn` que el puerto se cerró; cuenta con 2FA activo; reproducción tras vincular.

## 8. Criterios de aceptación (checklist QA)

- [ ] `./gradlew :app:testDebugUnitTest` en verde con los tests de 7.1.
- [ ] `assembleRelease` del teléfono y `-PorchardTvBuild=true` del TV compilan.
- [ ] Árbol de dependencias de release sin `gms`, `firebase`, `mlkit`; única dependencia nueva la de D3 (si A).
- [ ] Sin permiso `CAMERA` ni `RECORD_AUDIO` en el manifest fusionado.
- [ ] `git grep` de `Log.` en `pairing/` y `ui/tv/pairing/` sin secretos.
- [ ] Todo `TvQrLoginScreen` y su diálogo operables solo con D-pad (TV-INV-1).
- [ ] Puerto cerrado y NSD retirado tras éxito, cancelación, salida y expiración.
- [ ] Prueba en dispositivo real con cuenta 2FA: la TV queda con sesión y reproduce.
- [ ] D3 y D6 decididas por el propietario y registradas.
- [ ] `AUDIT.md`, `UPSTREAM.md` (zonas protegidas) y `technical_memory.md` actualizados.

## 9. Riesgos

| ID | Riesgo | Mitigación |
| --- | --- | --- |
| R1 | Google rota `__Secure-1PSIDTS`/`__Secure-3PSIDTS` y puede invalidar una cookie usada en dos dispositivos a la vez, cerrando la sesión en uno o ambos | Validar en la prueba de dispositivo con uso simultáneo durante 24 h. Si ocurre, documentarlo y evaluar un modo "mover sesión" (cerrar sesión en el teléfono tras transferir) en una HU aparte |
| R2 | Redes con aislamiento de clientes (Wi-Fi de invitados, AP isolation) impiden que el teléfono llegue a la TV | Mensaje claro en ambos lados y botón al login web |
| R3 | Algunas TV no tienen NSD funcional o filtran multicast | El modo QR no depende de NSD |
| R4 | `spotifySpdc` se guarda hoy en `SettingsRepository` (DataStore) sin cifrar, en teléfono y TV | Fuera de alcance de esta HU; registrar en `AUDIT.md` como hallazgo para una HU de endurecimiento (mover a `AndroidKeystoreCipher`) |
| R5 | Un QR fotografiado por terceros permite iniciar un handshake | La TV pide OK y muestra la cuenta antes de aplicar; el token es de un solo uso y caduca en 5 min |

## 10. Plan para DeepSeek Dev

### Contexto de negocio

El propietario usa Orchard en un televisor Philips con Android TV. El login de YouTube en la TV abre una página web de Google que no se puede usar con el control remoto cuando la cuenta pide verificación en dos pasos. Quiere iniciar sesión desde el teléfono: la TV muestra un QR, el teléfono lo reconoce, el usuario confirma y la TV queda con la sesión, sin pasar por ningún servidor externo.

### HU-ORCH-13.1: Núcleo criptográfico y mensajes

- 📁 **Repositorio:** `orchard-audited`, `mobile/android/app/src/main/java/dev/sfg/orchard/mobile/pairing/`
- 🎯 **Capa a trabajar:** lógica común JVM (sin UI)
- 📌 **Objetivo técnico:** implementar `PairingCrypto`, `PairingMessages` y `PairingUri` según secciones 5.1 a 5.4, y sus tests de 7.1. Resolver cómo reutilizar HKDF de `MediaCrypto` sin alterar su API.

### HU-ORCH-13.2: Servidor de emparejamiento en la TV

- 📁 **Repositorio:** `orchard-audited`, `.../pairing/TvPairingServer.kt`, `PairingAdvertiser.kt`
- 🎯 **Capa a trabajar:** red LAN en la TV
- 📌 **Objetivo técnico:** servidor de un solo uso con la máquina de estados, los límites de 5.5 y el ciclo de vida de PAIR-INV-4; anuncio NSD. Diseñar la inyección de dirección y reloj para que `TvPairingServerTest` corra en JVM.

### HU-ORCH-13.3: Cliente y descubrimiento en el teléfono

- 📁 **Repositorio:** `orchard-audited`, `.../pairing/PairingClient.kt`, `PairingDiscovery.kt`
- 🎯 **Capa a trabajar:** red LAN en el teléfono
- 📌 **Objetivo técnico:** cliente de 5.3 con aborto ante huella o compromiso inválidos y envío solo tras confirmación; descubrimiento NSD siguiendo el patrón de `CastDiscovery`.

### HU-ORCH-13.4: Pantallas TV

- 📁 **Repositorio:** `orchard-audited`, `.../ui/tv/pairing/`, `TvRoutes.kt`, `TvOrchardApp.kt`
- 🎯 **Capa a trabajar:** UI Compose de TV
- 📌 **Objetivo técnico:** `TvQrLoginScreen` y `TvPairingConfirmDialog` según 6.2 y 6.4, cumpliendo TV-INV-1 a TV-INV-3. QR por `QrMatrixEncoder` (implementación según D3).

### HU-ORCH-13.5: Pantallas y deep link del teléfono

- 📁 **Repositorio:** `orchard-audited`, `.../ui/screens/`, `app/OrchardNavigation.kt`, `MainActivity.kt`
- 🎯 **Capa a trabajar:** UI Compose del teléfono y enrutado
- 📌 **Objetivo técnico:** fila de Ajustes, `PairWithTvScreen`, `PairConfirmDialog` y enrutado de `orchard://pair` según 6.3, con PAIR-INV-2.

**Orden:** 13.1 → 13.2 y 13.3 en paralelo → 13.4 y 13.5. Claude (QA) revisa cada bloque contra los `PAIR-INV` antes del siguiente. Commits con prefijo `audit-fork:` y autorización explícita del propietario.

## 11. Decisiones pendientes del propietario

| ID | Pregunta | Recomendación |
| --- | --- | --- |
| Q1 | Excepción a SDD §3.2 para esta funcionalidad nueva | Aprobar: resuelve un bloqueo real de uso en TV |
| Q2 | D3: codificador QR | Opción A, `zxing:core:3.5.3` |
| Q3 | D6: enmiendas a TV-INV-4 y TV-INV-7 | Aprobar con el texto de D6 |
| Q4 | D4: modo código por SAS en lugar de código tecleado (diferencia con el pedido y con el criterio del backlog) | Aprobar; la alternativa tecleada sin PAKE no es segura |
| Q5 | D1: criterio del backlog "clave efímera transmitida en el QR" se reemplaza por ECDH con huella en el QR | Aprobar y ajustar el texto de HU-ORCH-13 en `backlog.md` |
