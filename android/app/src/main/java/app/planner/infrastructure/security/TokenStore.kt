package app.planner.infrastructure.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("credential", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("planner_device_token", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("planner_device_token", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun write(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(token.toByteArray()), Base64.NO_WRAP)
        check(preferences.edit().putString("token", "$iv:$data").commit()) { "Couldn't store device credential" }
    }
    fun read(): String? = try {
        preferences.getString("token", null)?.split(':')?.let { pieces ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP))) }
            String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)))
        }
    } catch (_: Exception) { null }
    // A clear is confirmed only after durable credential removal, away from the UI thread.
    @android.annotation.SuppressLint("ApplySharedPref")
    suspend fun clear() = withContext(Dispatchers.IO) { check(preferences.edit().clear().commit()) { "Couldn't clear device credential" } }
}
