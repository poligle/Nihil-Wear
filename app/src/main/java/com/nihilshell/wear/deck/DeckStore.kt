package com.nihilshell.wear.deck

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The computer this watch is a deck of: what its invitation said, and the address that answered last. */
class Computer(
    val name: String,
    val pin: String,
    val port: Int,
    val hosts: List<String>,
    var last: String?,
    val device: String,
)

/**
 * What the deck keeps, in its own preferences (out of backups,
 * res/xml/data_extraction_rules.xml): the computer, its token encrypted with a
 * key that never leaves the Android Keystore, and the invitation adb left
 * until the watch uses it.
 */
class DeckStore private constructor(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val computer: Computer?
        get() = runCatching {
            val o = JSONObject(sp.getString(COMPUTER, null) ?: return null)
            val hosts = o.getJSONArray("hosts").let { a -> List(a.length()) { a.getString(it) } }
            Computer(o.getString("name"), o.getString("pin"), o.getInt("port"), hosts, o.optString("last").ifEmpty { null }, o.getString("device"))
        }.getOrNull()

    /** The token, or null when there is none or it can't be read back (a reset Keystore). */
    fun token(): String? = runCatching {
        val (iv, data) = (sp.getString(TOKEN, null) ?: return null).split(':').map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    fun save(computer: Computer, token: String? = null) {
        val o = JSONObject()
            .put("name", computer.name)
            .put("pin", computer.pin)
            .put("port", computer.port)
            .put("hosts", JSONArray(computer.hosts))
            .put("last", computer.last ?: "")
            .put("device", computer.device)
        val edit = sp.edit().putString(COMPUTER, o.toString())
        if (token != null) {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            edit.putString(TOKEN, Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP))
        }
        edit.apply()
    }

    /** The computer, its token and any invitation go. */
    fun forget() {
        sp.edit().remove(COMPUTER).remove(TOKEN).remove(INVITATION).apply()
    }

    /** The invitation adb handed over (tools/watch invite), as its text, until it pairs or can't any more. */
    var invitation: String?
        get() = sp.getString(INVITATION, null)
        set(value) = sp.edit().apply { if (value == null) remove(INVITATION) else putString(INVITATION, value) }.apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val FILE = "deck"
        private const val COMPUTER = "computer"
        private const val TOKEN = "token"
        private const val INVITATION = "invitation"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "nihil-deck-token"
        private const val CIPHER = "AES/GCM/NoPadding"

        @Volatile
        private var instance: DeckStore? = null

        fun get(context: Context): DeckStore =
            instance ?: synchronized(this) { instance ?: DeckStore(context.applicationContext).also { instance = it } }
    }
}
