package online.sistemahelpdesk.movil.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra el token de sesión con Android Keystore (AES-256/GCM, clave no exportable
 * en el hardware/keystore del sistema), de modo que el JWT no queda en claro en
 * SharedPreferences.
 *
 * - Prefijo `enc:v1:` identifica el valor cifrado.
 * - Un valor sin prefijo es un token legado en claro: se devuelve tal cual (migración).
 * - Si Keystore falla al cifrar (raro), se conserva el valor en claro para no
 *   bloquear el inicio de sesión; si falla al descifrar, se devuelve `null` y
 *   la sesión se cierra (re-login).
 */
object TokenCrypto {

    private const val ALIAS = "shd_token_v1"
    const val PREFIJO = "enc:v1:"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_IV_GCM = 128

    fun cifrar(texto: String): String = try {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, clave())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val datos = Base64.encodeToString(cipher.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        "$PREFIJO$iv:$datos"
    } catch (e: Exception) {
        texto
    }

    /** Devuelve el token, `null` si no se pudo descifrar (clave perdida o dato corrupto). */
    fun descifrar(valor: String): String? {
        if (!valor.startsWith(PREFIJO)) return valor
        return try {
            val partes = valor.removePrefix(PREFIJO).split(":", limit = 2)
            if (partes.size != 2) return null
            val iv = Base64.decode(partes[0], Base64.NO_WRAP)
            val datos = Base64.decode(partes[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMACION)
            cipher.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(BITS_IV_GCM, iv))
            String(cipher.doFinal(datos), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun clave(): SecretKey {
        val almacén = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (almacén.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }
}
