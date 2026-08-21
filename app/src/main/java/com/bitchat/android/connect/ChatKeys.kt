package com.bitchat.android.connect

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The device's cloud-chat keypair (separate from the mesh Noise identity, which the mesh doesn't
 * expose). The private key never leaves this phone — it lives in EncryptedSharedPreferences; only
 * the public key is published (in the profile doc) so others can seal messages to it.
 */
object ChatKeys {

    private const val PREFS = "locus_chat_keys"
    private const val KEY_PRIV = "priv"
    private const val KEY_PUB = "pub"

    @Volatile private var priv: ByteArray? = null
    @Volatile private var pub: ByteArray? = null

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Load or lazily create the keypair. Safe to call repeatedly. */
    @Synchronized
    fun ensure(context: Context) {
        if (priv != null && pub != null) return
        val p = prefs(context.applicationContext)
        val privB64 = p.getString(KEY_PRIV, null)
        val pubB64 = p.getString(KEY_PUB, null)
        if (privB64 != null && pubB64 != null) {
            priv = Base64.decode(privB64, Base64.NO_WRAP)
            pub = Base64.decode(pubB64, Base64.NO_WRAP)
            return
        }
        val (sk, pk) = ChatCrypto.generateKeypair()
        priv = sk; pub = pk
        p.edit()
            .putString(KEY_PRIV, Base64.encodeToString(sk, Base64.NO_WRAP))
            .putString(KEY_PUB, Base64.encodeToString(pk, Base64.NO_WRAP))
            .apply()
    }

    fun privateKey(context: Context): ByteArray { ensure(context); return priv!! }
    fun publicKey(context: Context): ByteArray { ensure(context); return pub!! }

    /** Base64 public key to publish in the profile doc as `chatPubKey`. */
    fun publicKeyB64(context: Context): String =
        Base64.encodeToString(publicKey(context), Base64.NO_WRAP)
}
