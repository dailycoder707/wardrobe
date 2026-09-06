package com.wardrobe.app.core.ai.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wardrobe.app.core.model.ai.AiCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME = "ai_api_keys"

/**
 * Android Keystore-backed encrypted storage (ADR-012 rule 11) — the master
 * key itself is generated inside `AndroidKeyStore` and never leaves it;
 * `EncryptedSharedPreferences` uses it to encrypt both the preference keys
 * (AES256-SIV, deterministic so lookups still work) and values
 * (AES256-GCM). This is this app's first "encrypt an arbitrary secret
 * string" need — `core:sync`'s existing `AndroidKeystoreDeviceIdentity` is a
 * signing key, not a secret-blob store, so this is new infrastructure, not
 * a reuse of that.
 */
@Singleton
class EncryptedApiKeyStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ApiKeyStore {
        private val prefs: SharedPreferences by lazy {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        override fun getApiKey(capability: AiCapability): String? = prefs.getString(keyFor(capability), null)

        override fun setApiKey(
            capability: AiCapability,
            apiKey: String?,
        ) {
            val editor = prefs.edit()
            if (apiKey.isNullOrBlank()) {
                editor.remove(keyFor(capability))
            } else {
                editor.putString(keyFor(capability), apiKey)
            }
            editor.apply()
        }

        private fun keyFor(capability: AiCapability): String = capability.name
    }
