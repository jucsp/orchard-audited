// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.auth

import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import org.json.JSONObject

/** Error envelope from the account Worker. [code] is the OAuth-style `error`, never a secret. */
class AccountServiceException(val status: Int, val code: String, message: String) : Exception(message)

/** One HTTP call to the account Worker; [bearer] is an access token or null. */
fun interface AccountTransport {
    fun request(method: String, path: String, body: JSONObject?, bearer: String?): JSONObject
}

/** A provisioning code as issued; [toString] keeps the code out of logs (PAIR-CONN-INV-6). */
class ProvisionCode(val code: String, val expiresInSeconds: Int) {
    override fun toString(): String = "ProvisionCode(expiresIn=${expiresInSeconds}s)"
}

/**
 * Raised when a provisioning step fails. [retriable] is false once the Worker answered, since the
 * code is single-use and already burnt (PAIR-CONN-INV-8). Messages never carry the code.
 */
class ProvisionException(message: String, val retriable: Boolean, cause: Throwable? = null) : Exception(message, cause)

/**
 * Orchard Connect provisioning for a second device (SDD-tv-qr-pairing-connect §5.1-5.2). Plain JVM
 * so the request shapes and error mapping are unit-tested without Android.
 */
class OrchardAccountProvisioning(private val transport: AccountTransport) {

    /** Phone side: asks for a one-time code bound to this pairing. Call only after the user confirmed. */
    fun requestProvisionCode(accessToken: String, binding: ByteArray): ProvisionCode {
        require(binding.size == BINDING_BYTES) { "binding must be $BINDING_BYTES bytes" }
        val response = call("POST", "/devices/provision", JSONObject().put("binding", encode(binding)), accessToken)
        val code = response.optString("code")
        if (code.length != CODE_CHARS) throw ProvisionException("Malformed provisioning response", retriable = false)
        return ProvisionCode(code, response.optInt("expires_in", 0))
    }

    /** TV side: redeems the code for this device's own tokens. Returns the token response. */
    fun redeemProvision(code: String, binding: ByteArray, deviceName: String): JSONObject {
        require(binding.size == BINDING_BYTES) { "binding must be $BINDING_BYTES bytes" }
        val body = JSONObject()
            .put("grant_type", GRANT_TYPE)
            .put("code", code)
            .put("binding", encode(binding))
            .put("device", JSONObject().put("name", deviceName.take(MAX_DEVICE_NAME)).put("platform", "android"))
        return call("POST", "/auth/token", body, null)
    }

    private fun call(method: String, path: String, body: JSONObject, bearer: String?): JSONObject = try {
        transport.request(method, path, body, bearer)
    } catch (e: AccountServiceException) {
        val message = when {
            e.status == 400 && e.code == "invalid_grant" -> "El código de Orchard Connect expiró o ya se usó"
            e.status == 401 -> "Inicia sesión de nuevo en Orchard Connect en el teléfono"
            e.status == 429 -> "Demasiados intentos; espera un momento"
            else -> "Orchard Connect respondió con un error (${e.status})"
        }
        throw ProvisionException(message, retriable = e.status >= 500, cause = e)
    } catch (e: IOException) {
        throw ProvisionException("Sin conexión con Orchard Connect", retriable = true, cause = e)
    }

    companion object {
        const val GRANT_TYPE = "urn:orchard:grant-type:provision"
        const val BINDING_BYTES = 32
        const val CODE_CHARS = 43
        const val MAX_DEVICE_NAME = 64
        private val BINDING_LABEL = "orchard-pair-v1 connect".toByteArray(Charsets.US_ASCII)

        /** `binding = SHA-256("orchard-pair-v1 connect" ‖ th)`, computed identically by phone and TV. */
        fun computeProvisionBinding(transcriptHash: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").run {
                update(BINDING_LABEL)
                update(transcriptHash)
                digest()
            }

        fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
