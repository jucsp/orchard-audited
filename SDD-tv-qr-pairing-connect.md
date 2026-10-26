# Contrato SDD — Propagación integral de credenciales por QR a Android TV, con Orchard Connect (HU-ORCH-21)

- **Versión del contrato:** 1.0 · 2026-10-26 · APROBADO
- **Propietario:** juanca
- **Autor:** Claude (PM/QA)
- **Contratos padre:** `SDD-orchard-audit-fork.md` (INV-1 a INV-7), `SDD-android-tv.md` (TV-INV-*) y `SDD-tv-qr-pairing.md` (PAIR-INV-1 a PAIR-INV-8). Siguen vigentes. Este documento agrega los invariantes `PAIR-CONN-INV-*` y enmienda PAIR-INV-1, PAIR-INV-2 y el alcance §2.2 de `SDD-tv-qr-pairing.md` (D1, D2, aplicadas).
- **Base de código:** `audited` en `0c4a59b`. Rama de trabajo: `integrate/hu-orch-19-20-21` (compartida con HU-ORCH-19 y HU-ORCH-20).
- **Aprobación:** el propietario aprobó D1 a D6 el 2026-10-26 (sección 12).
- **Backlog:** HU-ORCH-21 en `backlog.md`.

---

## 1. Contexto de negocio

En Android TV no se puede iniciar sesión en Orchard Connect (`connect.kasper-cloud.com`), así que la TV no aparece como dispositivo para control remoto, cola compartida ni Listening Party. El login por QR de HU-ORCH-13 solo transfiere YouTube Music y, opcionalmente, la cookie de Spotify. El propietario quiere que escanear el QR de la TV con el teléfono deje la TV con **todo** lo que el teléfono tiene activo: Orchard Connect, YouTube Music, Spotify, Qobuz y claves de traducción BYOK, viendo antes qué se va a transferir y sin pasar los secretos por servidores intermedios.

## 2. Diagnóstico técnico de causa raíz

### 2.1 Por qué la TV no puede iniciar Orchard Connect

| # | Causa | Evidencia |
| --- | --- | --- |
| C1 | **No hay entrada en la TV.** Los ajustes de TV solo listan cuentas de YouTube en "Cuenta" e integraciones Spotify/Qobuz; no hay ninguna fila de Orchard Connect. La tarjeta de cuenta existe solo en el teléfono | `ui/tv/settings/TvSettingsPages.kt:83-108` (cuentas), `:219-221` (integraciones); `ui/screens/OrchardAccountSettingsCard.kt:48, 104` y `ui/screens/SettingsScreen.kt:136` (solo teléfono). `git grep OrchardAccountService ui/tv` vacío |
| C2 | **El login depende de un navegador externo.** `signIn()` abre `connect.kasper-cloud.com/auth/google/start` con `Intent.ACTION_VIEW` y espera volver por `<applicationId>://account/callback`. En Android TV suele no haber navegador (la excepción termina en "Could not open browser for sign-in") y Google bloquea OAuth en WebView embebido | `auth/OrchardAccountService.kt:125-143` (`:138` `startActivity`, `:141` `fail`), `:146-182` (callback) |
| C3 | **Copiar el token del teléfono no sirve.** El refresh token es `<deviceId>.<secreto>` y rota en cada refresco. El Worker acepta el secreto anterior solo 60 s; pasado ese plazo, reusarlo se trata como robo y **borra el dispositivo**, cerrando la sesión en ambos equipos. Además, teléfono y TV compartirían `did` en el JWT y Connect los vería como un solo dispositivo | Token: `services/account/src/service.js:96`; rotación y borrado: `:220-258` (`:237-242`); gracia `REFRESH_REUSE_GRACE_SECONDS = 60` en `:35`. Cliente: `auth/OrchardAccountService.kt:272-312`, `:370-371` |

Conclusión: la TV necesita **su propio dispositivo** en la cuenta, emitido por el Worker a partir de una autorización que el teléfono ya autenticado le entregue por el canal cifrado del QR.

### 2.2 Por qué el QR no propaga el resto

| Limitación | Evidencia |
| --- | --- |
| El payload `v=1` solo admite `youtube` (obligatorio) y `spotify`; cualquier otro campo se rechaza | `pairing/PairingMessages.kt:215-298` (`:261-263`) |
| El teléfono exige sesión de YouTube para vincular | `ui/screens/PairWithTvScreen.kt:109-114`; payload en `:117` |
| La TV aplica solo YouTube y `sp_dc` | `ui/tv/TvLoginRoutes.kt:28-38` |
| La TV entrega el payload por `onSessionReceived` tras descifrar | `pairing/TvPairingServer.kt:264, 274-281` |
| El QR y el TXT NSD son estrictos (`v=1`, conjunto fijo de parámetros): no admiten campos nuevos sin romper teléfonos con la versión anterior | `pairing/PairingUri.kt` (`params.keys != setOf("v","h","p","t","f","n")`), `pairing/PairingAdvertiser.kt:34` |

### 2.3 Almacenamiento actual de cada credencial

| Credencial | Dónde vive | Cifrado |
| --- | --- | --- |
| Orchard Connect (refresh) | `orchard_account` + `AndroidKeystoreCipher("orchard_account_refresh_v1")` (`OrchardAccountService.kt:71-72, 294-307`) | Sí |
| YouTube Music | `SecureYouTubeSessionStore` (HU-ORCH-17) | Sí |
| Spotify `sp_dc` | `SettingsRepository` DataStore (`settings/SettingsRepository.kt:76, 136`) | **No** (hallazgo R4 de HU-ORCH-13) |
| Qobuz `token` + `userId` | SharedPreferences `orchard_qobuz` (`qobuz/QobuzRepository.kt:67-69`) | **No** |
| Claves BYOK de traducción | `TranslationApiKeys` + `AndroidKeystoreCipher` (`lyrics/translation/TranslationApiKeys.kt:14-16`) | Sí |

## 3. Alcance

### 3.1 Dentro

1. Worker `services/account`: emisión y canje de un **código de aprovisionamiento** de un solo uso (5.2).
2. `OrchardAccountService`: pedir código (teléfono) y canjearlo (TV).
3. Payload `v=2` de `orchard-pair-v1` con bloques opcionales por servicio (5.3).
4. Diálogo de consentimiento con lista por servicio en el teléfono y lista recibida en la TV.
5. Aplicación por servicio en la TV, aislando fallos (5.5).
6. Ajustes de TV → Cuenta: estado de Orchard Connect, "Vincular desde el teléfono" y "Cerrar sesión de Orchard Connect".
7. Qobuz cifrado con `AndroidKeystoreCipher` antes de propagarlo (D4).
8. Tests JVM del payload y de la aplicación, y tests del Worker.

### 3.2 Fuera

- Login de Orchard Connect con navegador o WebView en la TV.
- Last.fm, ListenBrainz y Discord.
- Sincronización continua teléfono ↔ TV; la transferencia sigue siendo única y manual.
- Transferir todas las cuentas de YouTube de HU-ORCH-17: se envía la activa.
- Cambios en el transporte criptográfico de `orchard-pair-v1` (handshake, HKDF, GCM, QR, NSD).
- Cifrado de `sp_dc` (sigue como hallazgo R4 de HU-ORCH-13).

## 4. Invariantes

| ID | Invariante | Cómo se verifica |
| --- | --- | --- |
| PAIR-CONN-INV-1 | **Los tokens del teléfono no salen del teléfono.** Ni refresh ni access token de Orchard Connect viajan en el payload. Connect se propaga solo con un código de aprovisionamiento de un solo uso, 256 bits de entropía, vida ≤ 120 s, guardado en el Worker como hash y ligado al transcript del emparejamiento (`binding`) | Tests del Worker (reuso, expiración, binding distinto); test JVM de que el payload no contiene `refresh_token` |
| PAIR-CONN-INV-2 | **Canal LAN intacto.** Todos los secretos viajan dentro del sobre AES-256-GCM de `orchard-pair-v1` por la LAN. El único tráfico a Internet es teléfono → `connect.kasper-cloud.com` (pedir código, autenticado) y TV → el mismo host (canjear). Host ya inventariado (INV-3); ningún host nuevo | `git grep -nE 'https?://' pairing/ auth/` sin hosts nuevos; revisión QA |
| PAIR-CONN-INV-3 | **Consentimiento granular.** El teléfono muestra cada servicio detectado con su cuenta visible y una casilla. Solo se envía lo marcado. La TV muestra la lista recibida y aplica solo tras "Iniciar sesión" con foco inicial en "Cancelar". El código de Connect se pide **después** de que el usuario confirma | Test de estado: sin confirmar no hay `POST /devices/provision` ni `/v1/session` |
| PAIR-CONN-INV-4 | **Esquema estricto y sin degradación silenciosa.** Payload `v=2` con bloques opcionales y al menos uno presente; campos, proveedores y longitudes conocidos; desconocido → rechazo. La TV acepta `v=1` y `v=2`. Si una TV antigua responde `bad_payload`, el teléfono muestra "Actualiza Orchard en la TV" y no reintenta con `v=1` sin avisar | `PairingPayloadV2Test`; `PairingClientTest` |
| PAIR-CONN-INV-5 | **Persistencia solo por rutas seguras.** Connect por `OrchardAccountService` (Keystore), YouTube por `completeSignIn` → `SecureYouTubeSessionStore`, BYOK por `TranslationApiKeys`, Qobuz por `QobuzRepository` cifrado (D4). Ningún secreto nuevo en DataStore o SharedPreferences en claro | Revisión QA; test de `QobuzRepository` con prefs inspeccionadas |
| PAIR-CONN-INV-6 | **Sin rastro de secretos.** Código de aprovisionamiento, tokens, claves y cookies fuera de logcat, `toString()`, excepciones y portapapeles. Buffers `ByteArray` del texto claro se sobrescriben tras aplicar; el código se descarta tras canjearlo o al expirar | `git grep -n 'Log\.' pairing/ auth/` revisado; tests de `toString()` |
| PAIR-CONN-INV-7 | **La TV es un dispositivo propio.** El canje crea un `deviceId` nuevo con `platform: "android"` y nombre `<modelo> (TV)`. La TV aparece en la lista de dispositivos del teléfono y se puede revocar desde ahí (`removeDevice`) | Test del Worker; prueba manual |
| PAIR-CONN-INV-8 | **Aplicación aislada por servicio.** El fallo de un servicio no impide aplicar los demás; la TV informa el resultado de cada uno. El canje de Connect se reintenta como máximo 2 veces y nunca después de expirar | `PairingSessionApplierTest` |
| PAIR-CONN-INV-9 | Archivos de menos de 500 líneas, cabecera AGPL-3.0-or-later en archivos nuevos, sin narración histórica. UI de TV cumple TV-INV-1 a TV-INV-3 | Revisión QA |

PAIR-INV-1 a PAIR-INV-8 siguen vigentes con las enmiendas de D1 y D2. Una implementación que viole un `PAIR-CONN-INV`, un `PAIR-INV`, un `TV-INV` o un `INV` del contrato padre no se integra.

## 5. Diseño

### 5.1 Secuencia

```
Teléfono                              TV                               connect.kasper-cloud.com
   │ hello / key (sin cambios, PAIR-INV-5)│                                        │
   │─────────────────────────────────────►│                                        │
   │ diálogo: lista por servicio (INV-3)  │                                        │
   │ usuario confirma                     │                                        │
   │ POST /devices/provision {binding} ───┼───────────────────────────────────────►│ guarda hash(code), user, binding, exp=120 s
   │◄─────────────────────────────────────┼──────────────────── {code, expires_in} │
   │ POST /v1/session {GCM(payload v2)} ─►│                                        │
   │                                      │ diálogo TV: lista recibida             │
   │                                      │ usuario OK                             │
   │                                      │ YouTube, Spotify, Qobuz, BYOK (local)  │
   │                                      │ POST /auth/token {grant provision} ───►│ borra code, crea device nuevo
   │                                      │◄──────────────── tokens del device TV  │
   │                                      │ ConnectRepository → ConnectHub         │
```

`binding = SHA-256("orchard-pair-v1 connect" ‖ th)`, con `th` el transcript ya calculado por ambos lados (`SDD-tv-qr-pairing.md` §5.3). Liga el código a este emparejamiento; la confidencialidad la sigue dando GCM.

### 5.2 Worker `services/account`

**Emisión** `POST /devices/provision` (requiere `Authorization: Bearer <access token>` del teléfono):

- Cuerpo `{binding: base64url(32 B)}`.
- Genera `code` de 32 bytes aleatorios; inserta `provision_codes(code_hash, user_id, issuer_device_id, binding, expires_at = now + 120)`.
- Límite: bucket `provision` en `AUTH_LIMITER` y máximo 5 códigos vivos por usuario.
- Respuesta `{code, expires_in: 120}`.

**Canje** en `/auth/token` con `grant_type: "urn:orchard:grant-type:provision"`:

- Cuerpo `{code, binding, device: {name, platform}}`.
- `DELETE FROM provision_codes WHERE code_hash = ? RETURNING *` (un solo uso); rechaza si no existe, expiró o `binding` no coincide (`400 invalid_grant`).
- Crea el dispositivo con la misma lógica que `redeemAuthorizationCode` (`service.js:183-212`), extraída a una función común, y responde con `issueTokens`.
- Migración nueva en `services/account/migrations/` para la tabla `provision_codes` con índice por `expires_at` y limpieza de expirados en cada emisión.

### 5.3 Payload `v=2` (texto claro antes de cifrar)

```json
{
  "v": 2,
  "youtube": { "cookie": "…", "visitorData": "…", "dataSyncId": "…", "accountIndex": 0, "displayName": "…", "avatarUrl": "…" },
  "spotify": { "spDc": "…" },
  "orchardConnect": { "provisionCode": "<43 car. base64url>", "email": "…", "name": "…" },
  "qobuz": { "token": "…", "userId": 123456 },
  "translationKeys": [ { "provider": "gemini", "key": "…" } ]
}
```

| Bloque | Reglas |
| --- | --- |
| `youtube` | Igual que `v=1` (`cookie` ≤ 16 KiB y validada con `YouTubeSessionAuth.loginCookieValue`). Ahora opcional |
| `spotify` | `spDc` ≤ 1 KiB |
| `orchardConnect` | `provisionCode` exactamente 32 bytes base64url; `email` y `name` ≤ 256, solo para mostrar en la TV |
| `qobuz` | `token` ≤ 512; `userId` entero > 0 |
| `translationKeys` | ≤ 4 entradas; `provider` ∈ `LyricTranslationProvider` sin `LOCAL`; `key` ≤ 256; sin duplicados |
| Global | `v ∈ {1, 2}`; al menos un bloque; payload ≤ 32 KiB; campos desconocidos → `bad_payload` |

`PairingSessionPayload` pasa a tener todos los bloques opcionales. Su `toString()` solo informa qué bloques vienen.

### 5.4 Teléfono

- `PairingCredentialCollector` (nuevo, `pairing/`): reúne lo disponible: sesión activa de YouTube, `sp_dc`, estado de `OrchardAccountService`, `QobuzRepository.getCredentials()`, `TranslationApiKeys` guardadas. Devuelve una lista para el diálogo sin exponer los valores.
- `PairConfirmDialog`: una fila por servicio con la cuenta visible (correo de Connect, nombre de YouTube, "Qobuz (usuario 1234…)", "Gemini") y casilla. Valores por defecto en D3. Sin servicios detectados → no se ofrece vincular.
- `PairWithTvScreen.send`: deja de exigir YouTube (`:109-114`); tras confirmar, si Connect está marcado llama `OrchardAccountService.requestProvisionCode(binding)` y luego `client.send` con el payload `v=2`. Si el código falla (sin red), se avisa y se ofrece enviar sin Connect.

### 5.5 TV

- `PairingSessionApplier` (nuevo, `pairing/`): aplica en orden YouTube (`viewModel.completeSignIn`), Spotify (`updateSettings`), Qobuz (`QobuzRepository.connect`), BYOK (`TranslationApiKeys.save`) y por último Connect (`OrchardAccountService.redeemProvision(code, binding, name = "<Build.MODEL> (TV)")`). Devuelve un resultado por servicio.
- `OrchardAccountService.redeemProvision` reutiliza `acceptTokens` (`:294-312`). Al cambiar `state.deviceId`, `ConnectRepository` ya reconecta solo (`connect/ConnectRepository.kt:88-91` → `accountChanged`), así que ConnectHub se activa sin código extra.
- `TvLoginRoutes.kt:28-38` delega en el aplicador. `TvPairingConfirmDialog` lista lo recibido y, tras aplicar, el resultado por servicio.
- Ajustes TV → Cuenta (`TvSettingsPages.kt:83-108`): sección "Orchard Connect" con correo y estado ("Conectado" / "Sin sesión"), "Vincular desde el teléfono" (navega a `TvRoutes.LOGIN`) y "Cerrar sesión de Orchard Connect" (`signOut`). Sin botón "Iniciar sesión con Google" en TV.

### 5.6 Rutas tocadas

`services/account/src/service.js` y un archivo nuevo para aprovisionamiento si `service.js` pasa de 500 líneas; `services/account/migrations/`; `services/account/test/`; `mobile/.../auth/OrchardAccountService.kt` (373 líneas: los métodos nuevos van en `auth/OrchardAccountProvisioning.kt` si supera 450); `pairing/PairingMessages.kt` (313 líneas: el payload pasa a `pairing/PairingPayload.kt`); `pairing/PairingCredentialCollector.kt`, `pairing/PairingSessionApplier.kt` (nuevos); `ui/screens/PairWithTvScreen.kt`, `PairConfirmDialog.kt`; `ui/tv/TvLoginRoutes.kt`, `ui/tv/pairing/TvPairingConfirmDialog.kt`; `ui/tv/settings/TvSettingsPages.kt`, `TvSettingsRoutes.kt`; `qobuz/QobuzRepository.kt`. Todas se agregan a **Zonas protegidas** de `UPSTREAM.md` en el mismo commit.

## 6. Decisiones (ADR)

**D1. Enmienda a PAIR-INV-1 (aprobada, aplicada en `SDD-tv-qr-pairing.md`).** PAIR-INV-1 decía "ningún host de Internet participa". El aprovisionamiento de Connect requiere que el teléfono pida un código y la TV lo canjee en `connect.kasper-cloud.com`. Texto vigente: "Ningún host de Internet participa en el canal de emparejamiento. Fuera de él, solo se admite el aprovisionamiento de Orchard Connect de PAIR-CONN-INV-1 y PAIR-CONN-INV-2 contra el host del fork ya inventariado". En §2.2 de `SDD-tv-qr-pairing.md` se retiraron Qobuz y Orchard Connect de "Fuera".

**D2. Enmienda a PAIR-INV-2 (aprobada, aplicada en `SDD-tv-qr-pairing.md`).** La casilla única de Spotify pasa a una lista por servicio (PAIR-CONN-INV-3).

**D3. Valores por defecto de las casillas (aprobada).** YouTube, Orchard Connect, Spotify y Qobuz marcados (la HU pide propagación automática); claves BYOK **desmarcadas**, porque son claves de API con cobro asociado y una TV es un equipo compartido.

**D4. Qobuz cifrado con `AndroidKeystoreCipher` antes de propagar (aprobada).** Propagar un token que hoy está en claro duplicaría la exposición en un segundo equipo. `QobuzRepository` migra `qobuz_token` a `AndroidKeystoreCipher` con migración transparente de la preferencia existente, en un commit propio previo.

**D5. Código de aprovisionamiento en lugar de copiar tokens (aprobada).** Ver C3. Alternativa "device code flow" (la TV muestra un código para aprobar en el teléfono vía Internet): descartada porque duplica la UX del QR que ya existe.

**D6. Compatibilidad (aprobada).** Teléfono y TV deben tener la versión con `v=2`. Una TV con versión anterior rechaza `v=2` con `bad_payload` y el teléfono pide actualizarla (PAIR-CONN-INV-4). No se modifica el QR ni el TXT NSD para no romper teléfonos antiguos.

## 7. Desglose de tareas

| ID | Tarea | Responsable | Entrega | Depende de |
| --- | --- | --- | --- | --- |
| T1 | Worker: `/devices/provision`, grant `provision`, migración `provision_codes`, función común de creación de dispositivo | DeepSeek Dev | `services/account` + tests (`npm test`) | — |
| T2 | `OrchardAccountService.requestProvisionCode` y `redeemProvision` | DeepSeek Dev | Código + tests JVM con servidor HTTP simulado | T1 |
| T3 | Payload `v=2` (`PairingPayload.kt`), compatibilidad `v=1` | DeepSeek Dev | Código + `PairingPayloadV2Test` | — |
| T4 | `QobuzRepository` cifrado con migración (D4) | DeepSeek Dev | Código + test | — |
| T5 | `PairingCredentialCollector` y `PairingSessionApplier` | DeepSeek Dev | Código + tests | T2, T3, T4 |
| Q1 | Teléfono: `PairConfirmDialog` por servicio, `PairWithTvScreen` sin exigir YouTube, aviso de TV antigua | Claude PM/QA | Compose | T5 |
| Q2 | TV: `TvPairingConfirmDialog` con lista y resultados, `TvLoginRoutes`, sección Orchard Connect en Ajustes | Claude PM/QA | Compose (TV-INV-1..3) | T5 |
| Q3 | QA: PAIR-INV, PAIR-CONN-INV, `wc -l`, logs, tests JVM y del Worker | Claude PM/QA | Informe en `AUDIT.md` | Q1, Q2 |
| Q4 | Docs: `UPSTREAM.md`, `technical_memory.md` (enmiendas D1/D2 ya aplicadas en `SDD-tv-qr-pairing.md`) | Claude PM/QA | Docs | Q3 |
| P1 | Despliegue del Worker con la migración (`wrangler`) | Antigravity PO con autorización del propietario | Worker en producción | T1, Q3 |
| I1 | Rama `integrate/hu-orch-19-20-21`, commits `audit-fork:` uno por tarea | Antigravity PO | Rama | T1..Q4 |

## 8. Plan de verificación

### 8.1 Worker (`services/account/test/`)

| Caso | Esperado |
| --- | --- |
| Emisión sin bearer o con token inválido | `401` |
| Canje correcto | Dispositivo nuevo con `deviceId` distinto al del teléfono; `refresh_token` propio |
| Canje repetido del mismo código | `400 invalid_grant` |
| Canje a los 121 s | `400 invalid_grant` |
| `binding` distinto | `400 invalid_grant` y el código queda consumido |
| Sexto código vivo del mismo usuario | `429` |
| Suite existente | 76/76 en verde |

### 8.2 JVM (`src/test/java/dev/sfg/orchard/mobile/pairing/`)

| Test | Casos mínimos |
| --- | --- |
| `PairingPayloadV2Test` | Ida y vuelta con todos los bloques; solo Connect; payload vacío → rechazo; proveedor BYOK desconocido o `LOCAL` → rechazo; claves duplicadas → rechazo; `provisionCode` de 31 bytes → rechazo; `userId` 0 → rechazo; `v=1` sigue aceptado; campo desconocido → rechazo; `toString()` sin secretos; payload sin `refresh_token` |
| `PairingSessionApplierTest` | Falla Qobuz y se aplican los demás; Connect expirado no reintenta; orden de aplicación; resultados por servicio |
| `PairingClientTest` (ampliado) | Sin confirmación no hay pedido de código; `bad_payload` → mensaje de actualizar la TV, sin reintento `v=1` |
| `QobuzRepositoryTest` | Migración de token en claro a cifrado; prefs sin el token en claro |
| `OrchardAccountProvisioningTest` | `redeemProvision` deja `signedIn` y `deviceId` nuevo; error del Worker → estado de error sin secretos |

### 8.3 Manual (Philips 65PUD8507/43 + teléfono)

1. Teléfono con Connect, YouTube, Spotify, Qobuz y Gemini: escanear QR; el diálogo lista los cinco con los valores por defecto de D3.
2. Confirmar en ambos lados: la TV muestra resultado por servicio; Ajustes TV → Cuenta muestra Orchard Connect "Conectado".
3. En el teléfono, la lista de dispositivos de Connect muestra "<modelo> (TV)"; controlar la TV desde el teléfono.
4. Revocar la TV desde el teléfono: la TV queda "Sin sesión" en Connect sin afectar al teléfono.
5. Teléfono y TV usados 48 h en paralelo: ninguno pierde la sesión de Connect (C3 resuelto).
6. Captura de tráfico durante el emparejamiento: solo payload cifrado en la LAN; hacia Internet, solo `connect.kasper-cloud.com`.

## 9. Criterios de aceptación (checklist QA)

- [x] D1 a D6 decididas por el propietario y registradas (2026-10-26).
- [ ] Escanear el QR propaga Orchard Connect, YouTube Music y las integraciones marcadas.
- [ ] La TV inicia Connect automáticamente y lo muestra en Ajustes.
- [ ] El diálogo del teléfono lista cada servicio detectado.
- [ ] Tests del Worker y `./gradlew :app:testDebugUnitTest` en verde; `assembleRelease` del teléfono y `-PorchardTvBuild=true` en CI.
- [ ] Sin dependencias ni hosts nuevos; sin secretos en logs.
- [ ] Worker desplegado con autorización del propietario.
- [ ] `AUDIT.md`, `UPSTREAM.md` y `technical_memory.md` actualizados (`SDD-tv-qr-pairing.md` enmendado el 2026-10-26).

## 10. Riesgos

| ID | Riesgo | Mitigación |
| --- | --- | --- |
| R1 | La cookie de YouTube usada en dos equipos se invalida por rotación de `__Secure-*PSIDTS` | Igual que HU-ORCH-13 R1; validar en 8.3 paso 5 |
| R2 | Token de Qobuz ligado a un dispositivo o con límite de sesiones simultáneas | Validar en 8.3; si Qobuz cierra la sesión del teléfono, se desmarca por defecto y se documenta |
| R3 | La TV sin Internet al confirmar: el código expira | Resultado "Connect: sin conexión, vuelve a vincular"; los demás servicios se aplican (PAIR-CONN-INV-8) |
| R4 | Clave BYOK con cobro en un equipo compartido | Desmarcada por defecto (D3); se puede borrar en Ajustes de TV |
| R5 | Teléfono y TV en versiones distintas | D6: mensaje claro, sin degradación silenciosa |
| R6 | Cambio en el Worker de producción | Despliegue solo con autorización (P1); el grant nuevo no altera los existentes; tests de regresión 76/76 |

## 11. Plan para DeepSeek Dev

### Contexto de negocio

El propietario usa Orchard en un televisor Philips con Android TV. En la TV no puede iniciar Orchard Connect porque el login requiere un navegador que el televisor no tiene, y el QR solo transfiere YouTube y Spotify. Quiere que un solo escaneo del QR deje la TV con todas sus cuentas e integraciones del teléfono, incluida Orchard Connect como dispositivo propio, sin exponer los tokens del teléfono.

### HU-ORCH-21.1: Aprovisionamiento en el Worker

- 📁 **Repositorio:** `orchard-audited`, `services/account/`
- 🎯 **Capa a trabajar:** Backend (Cloudflare Worker + D1)
- 📌 **Objetivo técnico:** emitir y canjear códigos de aprovisionamiento de un solo uso según 5.2, sin alterar los grants existentes y con la batería de 8.1.

### HU-ORCH-21.2: Cliente de cuenta en Android

- 📁 **Repositorio:** `orchard-audited`, `mobile/.../auth/`
- 🎯 **Capa a trabajar:** Android, lógica de cuenta
- 📌 **Objetivo técnico:** pedir el código en el teléfono y canjearlo en la TV reutilizando la persistencia cifrada existente, de forma que ConnectHub se active sin código adicional.

### HU-ORCH-21.3: Payload v2, recolección y aplicación

- 📁 **Repositorio:** `orchard-audited`, `mobile/.../pairing/`, `mobile/.../qobuz/`
- 🎯 **Capa a trabajar:** Android, lógica común JVM
- 📌 **Objetivo técnico:** esquema `v=2` estricto con compatibilidad `v=1`, recolección de credenciales sin exponer valores, aplicación aislada por servicio y cifrado previo de Qobuz.

**Orden:** 21.1 y 21.3 (payload, Qobuz) en paralelo → 21.2 → recolector y aplicador → UI (Claude). Claude revisa cada bloque contra los `PAIR-INV` y `PAIR-CONN-INV` antes del siguiente.

## 12. Decisiones del propietario

| ID | Pregunta | Decisión (2026-10-26) |
| --- | --- | --- |
| Q1 | D1: enmienda a PAIR-INV-1 para el aprovisionamiento de Connect | Aprobada y aplicada en `SDD-tv-qr-pairing.md` |
| Q2 | D2: lista por servicio en lugar de la casilla de Spotify | Aprobada y aplicada en `SDD-tv-qr-pairing.md` |
| Q3 | D3: valores por defecto | YouTube, Orchard Connect, Spotify y Qobuz marcados; BYOK desmarcado |
| Q4 | D4: cifrar Qobuz con `AndroidKeystoreCipher` en esta HU | Aprobada |
| Q5 | D5: código de aprovisionamiento de un solo uso | Aprobada |
| Q6 | D6: compatibilidad `v=1`/`v=2` sin degradación silenciosa | Aprobada |
| Q7 | Excepción a SDD §3.2 por cambio en el Worker | Aprobada junto con D1; el despliegue (P1) sigue requiriendo autorización explícita |
