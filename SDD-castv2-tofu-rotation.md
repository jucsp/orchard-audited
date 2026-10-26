# Contrato SDD — CastV2: rotación del certificado TLS de receptores Cast (HU-ORCH-20)

- **Versión del contrato:** 1.0 · 2026-10-26 · APROBADO
- **Propietario:** juanca
- **Autor:** Claude (PM/QA)
- **Contratos padre:** `SDD-orchard-audit-fork.md` (INV-1 a INV-7) y `SDD-chromecast-castv2.md` (CAST-INV-1 a CAST-INV-9). Siguen vigentes. Este documento agrega los invariantes `CAST-ROT-INV-*` y enmienda CAST-INV-7 y el alcance §2.3 de `SDD-chromecast-castv2.md` (D1, aplicada).
- **Base de código:** `audited` en `0c4a59b`. Rama de trabajo: `integrate/hu-orch-19-20-21` (compartida con HU-ORCH-19 y HU-ORCH-21).
- **Aprobación:** el propietario aprobó D1, D2 y D3 (Opción B) el 2026-10-26, con el ajuste de migración v1→v2 sin captura previa (T0 no bloqueante), verificación SHA-256 con fallback SHA-1 y diálogo de confianza ante fallo de `deviceauth` (sección 13).
- **Backlog:** HU-ORCH-20 en `backlog.md`.

---

## 1. Contexto de negocio

Con el cliente CastV2 propio (HU-ORCH-12) el propietario enviaba canciones a su Google Nest Mini sin problemas. Días después, el mismo Nest Mini dejó de aceptar conexiones: la app aborta diciendo que el dispositivo "presentó otro certificado". Desde ese momento el parlante queda inutilizable para Orchard, sin forma de recuperarlo desde la app. El propietario quiere que el envío funcione de forma sostenida, incluso tras reinicios o renovaciones de certificado del parlante, sin bajar la seguridad contra suplantación en la LAN y sin Play Services ni servidores externos.

## 2. Diagnóstico técnico de causa raíz

### 2.1 Origen del mensaje

| Paso | Archivo y línea | Qué pasa |
| --- | --- | --- |
| 1 | `playback/ChromecastPlayback.kt:107` | Cada conexión crea `CastChannel(device.host, device.port, device.id, trustStore)` con el almacén persistente |
| 2 | `playback/OrchardPlaybackService.kt:316-317` | El almacén es `SharedPreferencesCastTrustStore(this)` (prefs `orchard_cast_tofu_pins`, `cast/SharedPreferencesCastTrustStore.kt:13-30`) |
| 3 | `cast/CastChannel.kt:68-69` | `createSslContext(deviceId, trustStore)` instala `CastTrustManager` |
| 4 | `cast/CastTrust.kt:93-107` | `checkServerTrusted` calcula SHA-256 del **certificado TLS hoja** (`certificate.encoded`, `:109-115`). Sin pin → lo guarda (`:101`). Pin distinto → `CastSecurityException("Certificate fingerprint mismatch …")` (`:103-105`) |
| 5 | `cast/CastChannel.kt:78-87` | El handshake se hace dentro de `open()` y se desenvuelve la excepción para conservar la de pin |
| 6 | `playback/ChromecastPlayback.kt:262-268` | `messageFor` traduce el "mismatch" a **"This Cast device presented a different certificate. Connection aborted"**, que llega a la UI por `onCastError` (`playback/CastControls.kt:87`) |

### 2.2 Causa raíz

El pin TOFU se aplica al certificado TLS **efímero** del receptor. Los dispositivos Google Cast generan su certificado TLS autofirmado con validez corta y lo renuevan periódicamente o al reiniciar (VLC documentó validez de unos 3 días al desactivar la verificación de CA para Chromecast). La identidad estable del dispositivo es otra: un **certificado de dispositivo** de larga duración, emitido por una CA intermedia de Google, que firma el certificado TLS vigente cuando el emisor lo pide por el canal `urn:x-cast:com.google.cast.tp.deviceauth`.

Con el diseño actual:

1. La primera conexión fija el SHA-256 del certificado TLS del día.
2. El Nest Mini renueva el certificado; el `id` del TXT mDNS no cambia (`cast/CastDiscovery.kt:27`).
3. `CastTrustManager` ve otro hash para el mismo `id` y aborta. Siempre.
4. No hay ruta de recuperación: ni UI para olvidar el dispositivo ni lógica para renovar el pin. Solo borrar los datos de la app lo destraba.

CAST-INV-7 pedía exactamente este comportamiento ("si cambia, se aborta y se avisa") y `SDD-chromecast-castv2.md` §2.3 dejó fuera la autenticación de dispositivo. El defecto es de contrato, no de implementación.

### 2.3 Causas descartadas

| Hipótesis del backlog | Resultado |
| --- | --- |
| Rechazo en `ChromecastStreamServer` por token o IP | No produce ese mensaje. El servidor responde `404` al receptor (`playback/ChromecastStreamServer.kt:118-121, 143-152`) y el síntoma sería `LOAD_FAILED`, no un fallo de conexión. Se conserva como riesgo R3 (grupos multizona) |
| Credenciales de cuenta, DRM o `customData` | El Default Media Receiver `CC1AD845` no recibe credenciales; el `LOAD` solo lleva una URL LAN con token |
| Cambio de IP por DHCP | El pin está indexado por `id`, no por IP; un cambio de IP no dispara el error |

## 3. Alcance

### 3.1 Dentro

1. Canal `deviceauth` mínimo: `AuthChallenge` con nonce y verificación de `AuthResponse`.
2. Pin TOFU sobre la clave del certificado de dispositivo, no sobre el certificado TLS.
3. Migración de pines existentes (esquema v1 → v2), automática cuando `deviceauth` verifica, aunque el certificado TLS ya haya rotado.
4. Flujo de consentimiento cuando cambia la clave del dispositivo o falla `deviceauth`, y acción "Olvidar dispositivo".
5. Mensajes de error precisos en la UI (teléfono y TV con D-pad).
6. Tests JVM con vectores y receptor simulado.

### 3.2 Fuera

- Validar la cadena hasta raíces de Google, CRL o revocación en línea (D2).
- Receptor propio o App ID.
- Escritorio (Fase 2 de HU-ORCH-12).

## 4. Invariantes

`CAST-INV-1` a `CAST-INV-9` siguen vigentes, con la enmienda de CAST-INV-7 de D1.

| ID | Invariante | Cómo se verifica |
| --- | --- | --- |
| CAST-ROT-INV-1 | **Identidad = clave de dispositivo.** El pin por `id` es el SHA-256 del `SubjectPublicKeyInfo` del certificado de dispositivo (`client_auth_certificate`). Un certificado TLS nuevo se acepta solo si la firma de `AuthResponse` sobre `sender_nonce ‖ DER(certificado TLS hoja)` verifica con esa clave | `CastDeviceAuthTest`: TLS nuevo + firma válida → acepta; firma inválida → rechaza |
| CAST-ROT-INV-2 | **Nonce fresco.** 16 bytes de `SecureRandom` por conexión. Respuesta sin nonce, o con nonce distinto, se rechaza | Test de nonce repetido y ausente |
| CAST-ROT-INV-3 | **Nunca se re-confía en silencio.** Si cambia la clave de dispositivo pinneada (`CastDeviceKeyChanged`) o si falla `deviceauth` (`CastDeviceAuthFailed`), se aborta. Solo `CastTrustChangedDialog` ("Cancelar" con foco inicial, nombre `fn`, modelo `md` y 8 hex de la huella nueva y la anterior) puede reemplazar el pin. Sin diálogo no hay escritura en el almacén. Única excepción: la migración de CAST-ROT-INV-7 | Test de estado: sin confirmación, el almacén no cambia |
| CAST-ROT-INV-4 | **Sin red nueva.** Ni CRL, ni OCSP, ni descarga de raíces. Todo ocurre en el canal TLS ya abierto al IP privado (CAST-INV-2) | Revisión QA; `git grep -nE 'https?://' cast/` vacío |
| CAST-ROT-INV-5 | **Algoritmos acotados.** RSA ≥ 2048 bits con PKCS#1 v1.5. El desafío pide siempre `hash_algorithm = SHA256` y se verifica con `SHA256withRSA`. Se usa `SHA1withRSA` solo si el `AuthResponse` declara `hash_algorithm = SHA1`, siempre sobre `sender_nonce ‖ DER(TLS hoja)`. Cualquier otro algoritmo o clave → rechazo | Tests por algoritmo (SHA-256 válido, SHA-1 declarado válido, SHA-1 no declarado rechazado) |
| CAST-ROT-INV-6 | **Entradas acotadas.** Mensaje `deviceauth` ≤ 16 KiB, cada certificado ≤ 4 KiB, ≤ 3 intermedios, respuesta en ≤ 5 s. El parseo proto2 de `DeviceAuthMessage` falla cerrado ante campos mal formados | Tests del codec con entradas truncadas y sobredimensionadas |
| CAST-ROT-INV-7 | **Migración atómica sin bloqueo.** Si existe un pin v1 (hash de TLS) y no hay pin v2, y `deviceauth` verifica criptográficamente que el certificado de dispositivo (RSA ≥ 2048) firmó `sender_nonce ‖ DER(TLS hoja actual)`, se guarda `pinDk` y se borra el pin v1 en una sola escritura `commit()`, sin preguntar, coincida o no el pin v1 con el TLS actual. Si `deviceauth` falla, no hay migración y aplica CAST-ROT-INV-3. "Olvidar dispositivo" borra ambos | Tests de `SharedPreferencesCastTrustStore` v1→v2 con TLS igual y TLS rotado |
| CAST-ROT-INV-8 | **Logs sin identificadores completos.** Ni huellas completas, ni `id` completo, ni certificados (CAST-INV-6) | `git grep -n 'Log\.' cast/ playback/Chromecast*` revisado |
| CAST-ROT-INV-9 | **Stream server ligado al canal real.** `receiverIp` de `ChromecastStreamServer` es el IP del canal TLS autenticado; en cada reconexión de `CastSession` se revalida que siga siendo el mismo | Test de reconexión con IP distinto → sesión cerrada |

Una implementación que viole un `CAST-ROT-INV`, un `CAST-INV` o un `INV` del contrato padre no se integra.

## 5. Decisiones (ADR)

**D1. Enmienda a CAST-INV-7 y a §2.3 de `SDD-chromecast-castv2.md` (aprobada, aplicada).** CAST-INV-7: "TLS sin validar CA, con pin TOFU del certificado de dispositivo por `id` del TXT, verificado por `deviceauth` en cada conexión (CAST-ROT-INV-1). La renovación del certificado TLS es transparente; un cambio de la clave de dispositivo o un fallo de `deviceauth` aborta y requiere confirmación explícita". En §2.3 se retiró "Autenticación de dispositivo" de "Fuera".

**D2. Sin validación de cadena hasta Google (aprobada).** Validar contra raíces de Cast embebidas eliminaría el TOFU de la primera conexión, pero ata la app a CA intermedias que caducan: en marzo de 2025 venció la ICA de Chromecast 2.ª gen. y esos equipos quedaron "no confiables", y hay reportes de que la CA del Google Home Mini vence en enero de 2027. Pinear la clave de dispositivo evita esa clase de caída. La cadena se puede agregar luego como verificación informativa, nunca bloqueante.

**D3. Opciones evaluadas (aprobada la B).**

| Opción | Descripción | Pros | Contras |
| --- | --- | --- | --- |
| A | Solo consentimiento: ante cambio de TLS, diálogo "¿confiar de nuevo?" | Simple, sin protocolo nuevo | El usuario verá el diálogo cada pocos días y aprenderá a aceptarlo: anula la protección |
| **B (aprobada)** | `deviceauth` + pin de la clave de dispositivo; diálogo solo si cambia esa clave | Rotación TLS transparente; MITM posterior a la primera conexión detectado; sin red | Codec proto2 adicional (~150 líneas) y vectores reales |
| C | Desactivar el pin | Ninguno | Viola CAST-INV-7. No se ofrece |

## 6. Protocolo `deviceauth`

### 6.1 Mensajes (proto2, codificación manual como `CastMessage`)

Namespace `urn:x-cast:com.google.cast.tp.deviceauth`, `source_id` `sender-0`, `destination_id` `receiver-0`, `payload_type` `BINARY` (1), cuerpo en `payload_binary` (campo 7, tag `0x3a`). Hoy el decodificador ignora `payload_binary` (`SDD-chromecast-castv2.md` §4.3): se habilita solo para este namespace.

```
DeviceAuthMessage { 1: AuthChallenge challenge; 2: AuthResponse response; 3: AuthError error }
AuthChallenge     { 1: enum signature_algorithm = RSASSA_PKCS1v15(1); 2: bytes sender_nonce; 3: enum hash_algorithm = SHA256(1) }
AuthResponse      { 1: bytes signature; 2: bytes client_auth_certificate; 3: repeated bytes intermediate_certificate;
                    4: enum signature_algorithm; 5: bytes sender_nonce; 6: enum hash_algorithm; 7: bytes crl }
AuthError         { 1: enum error_type }
```

Los números de campo siguen el `cast_channel.proto` público del emisor de Chromium/Open Screen. T1 y T2 se implementan y prueban con vectores sintéticos (certificado RSA generado en el test que firma `nonce ‖ DER(TLS)`). T0 deja de bloquear: la captura del Nest Mini real valida el formato después del build y, si difiere, se corrige el codec y esta sección antes de cerrar la HU.

### 6.2 Secuencia

1. TLS con un `TrustManager` que **acepta y registra** el certificado hoja (sin pin en esta capa) y lo expone a `CastChannel`.
2. Antes de `CONNECT`, el emisor envía `AuthChallenge{sender_nonce = N(16 B), hash = SHA256}`.
3. Recibe `AuthResponse` (timeout 5 s) y verifica en `CastDeviceAuth.verify`:
   - `response.sender_nonce == N`.
   - `client_auth_certificate` es X.509 parseable, RSA ≥ 2048.
   - Algoritmo según `response.hash_algorithm`: `SHA256` (o ausente) → `SHA256withRSA`; `SHA1` → `SHA1withRSA`; otro → rechazo (CAST-ROT-INV-5).
   - `Signature(alg).verify(N ‖ DER(TLS hoja), signature)` con la clave del certificado de dispositivo.
   - `pinDk = SHA-256(SPKI del certificado de dispositivo)`.
4. Decisión con el almacén:

| Pin v2 guardado | Pin v1 guardado | Resultado |
| --- | --- | --- |
| = `pinDk` | — | Acepta. Rotación TLS transparente |
| ≠ `pinDk` | — | Aborta con `CastDeviceKeyChanged` → diálogo (CAST-ROT-INV-3) |
| ninguno | = SHA-256(TLS hoja) | Migra: guarda `pinDk`, borra v1, acepta |
| ninguno | ≠ SHA-256(TLS hoja) | Migra igual (TLS rotado, `deviceauth` verificado): guarda `pinDk`, borra v1, acepta sin diálogo (CAST-ROT-INV-7). Caso actual del Nest Mini del propietario |
| ninguno | ninguno | Primer uso: guarda `pinDk`, acepta |

5. Si `deviceauth` falla (`AuthError`, sin respuesta en 5 s, nonce distinto, firma inválida o algoritmo no admitido): aborta con `CastDeviceAuthFailed`, sin escribir, y la UI ofrece `CastTrustChangedDialog`. Si el usuario confirma, se guarda como pin v1 el SHA-256 del TLS hoja actual y la fila del dispositivo muestra "Sin autenticación de dispositivo". En conexiones siguientes con `deviceauth` fallido: TLS = pin v1 → acepta; TLS distinto → aborta y vuelve a preguntar. Si más adelante `deviceauth` verifica, aplica la migración de CAST-ROT-INV-7. No hay degradación sin consentimiento.

### 6.3 Almacén v2

`CastTrustStore` gana `getDeviceKeyPin(id)`, `setDeviceKeyPin(id, pin)`, `getLegacyPin(id)` y `forget(id)`. `SharedPreferencesCastTrustStore` usa claves `dk:<id>` (v2) y `<id>` (v1 existente). Escrituras con `commit()`.

## 7. UI

- **Teléfono** (`FrostedDevicesScreen.kt`) y **TV** (selector con D-pad): diálogo `CastTrustChangedDialog` ante `CastDeviceKeyChanged` o `CastDeviceAuthFailed`, con nombre, modelo, huellas cortas anterior y nueva, texto "Si no reiniciaste ni cambiaste este parlante, cancela" (con firma inválida: "La respuesta no pudo verificarse; podría ser otro equipo suplantando al parlante") y botones "Cancelar" (foco inicial) y "Confiar en este dispositivo".
- Hoja o fila de cada dispositivo Cast: acción "Olvidar dispositivo" → `forget(id)`, siempre disponible, para que un parlante nunca quede bloqueado sin salida.
- `messageFor` (`ChromecastPlayback.kt:262-271`) distingue: clave de dispositivo cambiada, autenticación fallida, IP no privada, sin respuesta.

## 8. Componentes

| Archivo | Cambio | `android.*` |
| --- | --- | --- |
| `cast/CastDeviceAuth.kt` (nuevo) | Codec `DeviceAuthMessage`, `verify` con SHA-256 y fallback SHA-1 declarado, cálculo de `pinDk` | No |
| `cast/CastTrust.kt` | `CastTrustManager` captura la hoja; decisión de 6.2 pasos 4 y 5; `CastDeviceKeyChanged` y `CastDeviceAuthFailed` | No |
| `cast/CastMessage.kt` | `payload_binary` para el namespace `deviceauth` | No |
| `cast/CastSession.kt` | Paso de autenticación antes de `CONNECT`; revalidación de IP en reconexión (CAST-ROT-INV-9). Está en 442 líneas: la lógica nueva va en `CastDeviceAuth.kt` | No |
| `cast/SharedPreferencesCastTrustStore.kt` | Esquema v2 y migración | Sí |
| `playback/ChromecastPlayback.kt` | Mensajes y callback de diálogo | Sí |
| `ui/…/CastTrustChangedDialog.kt` (nuevo) | Diálogo teléfono/TV | Compose |

`cast/CastV2Player.kt` ya tiene 501 líneas: no se toca en esta HU; su división queda registrada como deuda para Q2.

## 9. Desglose de tareas

| ID | Tarea | Responsable | Entrega | Depende de |
| --- | --- | --- | --- | --- |
| T0 | Validación posterior al build (no bloqueante): capturar en LAN propia, con el Nest Mini, un intercambio `deviceauth` (build debug que vuelca bytes hex a un archivo local, nunca a logcat) y dos certificados TLS de días distintos | Propietario + Claude PM/QA | `tests/fixtures/castv2/deviceauth-*.hex` | Build de la rama |
| T1 | Codec `DeviceAuthMessage` + `payload_binary`, con vectores sintéticos | DeepSeek Dev | `CastDeviceAuth.kt`, `CastMessage.kt` + tests | — |
| T2 | Verificación de firma (SHA-256 y fallback SHA-1), `pinDk`, tabla de decisión 6.2 y paso 5 | DeepSeek Dev | `CastDeviceAuth.kt`, `CastTrust.kt` + tests | T1 |
| T3 | Almacén v2, migración y `forget` | DeepSeek Dev | `SharedPreferencesCastTrustStore.kt` + tests | T2 |
| T4 | Integración en `CastSession`/`CastChannel` y revalidación de IP | DeepSeek Dev | Código + tests con receptor simulado | T2 |
| Q1 | Diálogo, "Olvidar dispositivo", mensajes (teléfono y TV) | Claude PM/QA | Compose | T3, T4 |
| Q2 | Verificación CAST-INV y CAST-ROT-INV, `wc -l`, logs | Claude PM/QA | Informe en `AUDIT.md` | Q1 |
| Q3 | Prueba en hardware (10.2) y vectores reales de T0 agregados a los tests | Claude PM/QA + propietario | Informe | Q2, T0 |
| I1 | Rama `integrate/hu-orch-19-20-21`, commits `audit-fork:` | Antigravity PO | Rama | T1..Q3 |

## 10. Plan de verificación

### 10.1 JVM (`src/test/java/dev/sfg/orchard/mobile/cast/`)

| Test | Casos mínimos |
| --- | --- |
| `CastDeviceAuthTest` | Vector sintético verifica con SHA-256 y con SHA-1 declarado; SHA-1 no declarado falla; vector real de T0 verifica (cuando exista); un bit alterado en firma, nonce o TLS falla; nonce distinto falla; RSA 1024 rechazado; algoritmo desconocido rechazado; mensaje de 17 KiB rechazado; proto truncado falla cerrado |
| `CastTrustRotationTest` | Las 5 filas de la tabla 6.2; pin v1 con TLS rotado y firma válida → migra sin diálogo; TLS rotado con misma clave de dispositivo → acepta sin escribir; clave distinta → `CastDeviceKeyChanged` sin escribir; `deviceauth` fallido → `CastDeviceAuthFailed` sin escribir; confirmación tras fallo → pin v1 del TLS actual |
| `SharedPreferencesCastTrustStoreTest` | Migración v1→v2; `forget` borra ambas claves; `commit()` síncrono |
| `CastSessionTest` (ampliado) | Autenticación antes de `CONNECT`; `AuthError` → aborta; sin respuesta en 5 s → aborta; reconexión a IP distinto → cierra |
| `CastTrustTest` (existente) | El caso `second use with different certificate throws security exception` se reescribe: aplica a la clave de dispositivo, no al TLS |

### 10.2 Manual

1. Nest Mini bloqueado hoy: tras actualizar, conecta y reproduce sin diálogo (migración v1→v2 por `deviceauth`).
2. Reiniciar el Nest Mini (desenchufar) y volver a enviar: sin diálogo.
3. Repetir al día 3 y 7 sin intervención: sin diálogo.
4. Chromecast/Google TV y un grupo multizona: conectan.
5. "Olvidar dispositivo" → siguiente conexión es primer uso.
6. Receptor que no responde `deviceauth` (si existe en la LAN): diálogo, confiar, reproduce; fila muestra "Sin autenticación de dispositivo".

## 11. Criterios de aceptación (checklist QA)

- [x] Causa raíz (2.2) aceptada por el propietario y D1 aprobada (2026-10-26).
- [ ] El Nest Mini bloqueado migra sin diálogo y funciona tras reinicio y tras 7 días sin diálogo.
- [ ] Cambio de clave de dispositivo o fallo de `deviceauth` bloquea y pide confirmación (CAST-ROT-INV-3); "Olvidar dispositivo" disponible en teléfono y TV.
- [ ] T0 registrado en `AUDIT.md` con vectores reales en los tests.
- [ ] `./gradlew :app:testDebugUnitTest` en verde con los tests de 10.1; `assembleRelease` en CI.
- [ ] Sin dependencias nuevas; sin hosts nuevos (CAST-INV-1, CAST-INV-2, CAST-ROT-INV-4).
- [ ] `AUDIT.md`, `UPSTREAM.md` (zonas protegidas), `technical_memory.md` y `SDD-chromecast-castv2.md` actualizados.

## 12. Riesgos

| ID | Riesgo | Mitigación |
| --- | --- | --- |
| R1 | Formato de datos firmados distinto al supuesto | Vectores sintéticos durante el desarrollo; T0 post-build con el Nest Mini real; si difiere, se corrige el codec antes de cerrar la HU. Mientras tanto el usuario cae en el diálogo del paso 5, nunca en un bloqueo sin salida |
| R2 | Algún modelo no responde `deviceauth` con `CC1AD845` | Paso 5: diálogo explícito y pin de TLS con aviso visible; nunca degradación silenciosa |
| R3 | Grupo multizona: el líder puede cambiar y pedir el stream desde otra IP del grupo | Registrar `LOAD_FAILED` con IP de origen en QA; si ocurre, HU aparte para aceptar IPs de miembros autenticados del grupo |
| R4 | Restablecimiento de fábrica genera otra clave de dispositivo | Es el caso del diálogo; mensaje explica "si reiniciaste de fábrica, confía de nuevo" |
| R5 | Primer uso sigue siendo TOFU | Igual que hoy; superficie limitada a la LAN del usuario |
| R6 | La migración v1→v2 con TLS rotado es un nuevo TOFU: sin validación de cadena (D2), un equipo en la LAN que suplante al parlante en esa primera conexión tras actualizar puede firmar con su propia clave y quedar pinneado | Riesgo aceptado por el propietario (2026-10-26): el pin v1 ya no protegía porque el TLS rota. Ventana de una sola conexión por dispositivo; después, CAST-ROT-INV-3. Mitigable con "Olvidar dispositivo" y reconexión en una LAN de confianza |
| R7 | Confiar tras fallo de `deviceauth` reduce la protección de ese dispositivo a un pin de TLS que rota | Diálogo en cada rotación y etiqueta "Sin autenticación de dispositivo"; el texto distingue firma inválida (posible suplantación) de falta de respuesta |

## 13. Decisiones del propietario

| ID | Pregunta | Decisión (2026-10-26) |
| --- | --- | --- |
| Q1 | Enmienda D1 (CAST-INV-7 y §2.3) | Aprobada y aplicada en `SDD-chromecast-castv2.md` |
| Q2 | Opción de D3 | B: `deviceauth` + pin de SPKI de la clave de dispositivo + diálogo y "Olvidar dispositivo" |
| Q3 | D2: sin validación de cadena hasta Google | Aprobada |
| Q4 | Migración v1→v2 sin captura previa | Aprobada: con pin v1 y TLS rotado, si `deviceauth` verifica se migra a `pinDk` y se borra v1 sin bloquear (CAST-ROT-INV-7). T0 no bloqueante. Riesgo R6 aceptado |
| Q5 | Algoritmo de firma | SHA256withRSA; SHA1withRSA solo si el receptor declara SHA1 (CAST-ROT-INV-5) |
| Q6 | Fallo de `deviceauth` o cambio de `pinDk` | `CastTrustChangedDialog` con "Confiar en este dispositivo" / "Cancelar" y "Olvidar dispositivo" en la hoja o fila (6.2 paso 5, sección 7) |
