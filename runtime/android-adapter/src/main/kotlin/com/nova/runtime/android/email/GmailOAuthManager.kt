package com.nova.runtime.android.email

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Google Sign-In + token refresh for Gmail read-only access. */
class GmailOAuthManager(
    private val context: Context,
) {
    private val prefs by lazy {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        EncryptedSharedPreferences.create(
            context,
            GmailOAuthConfig.TOKEN_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun createSignInClient(): GoogleSignInClient {
        val options =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(GmailOAuthConfig.GMAIL_READONLY_SCOPE))
                .requestIdToken(GmailOAuthConfig.WEB_CLIENT_ID)
                .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun isSignedIn(): Boolean = getLastSignedInAccount() != null

    fun persistAccountEmail(email: String) {
        prefs.edit().putString(GmailOAuthConfig.KEY_ACCOUNT_EMAIL, email).apply()
    }

    fun storedAccountEmail(): String? = prefs.getString(GmailOAuthConfig.KEY_ACCOUNT_EMAIL, null)

    fun recordSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(GmailOAuthConfig.KEY_LAST_SYNC_AT, timestamp).apply()
    }

    fun lastSyncAt(): Long = prefs.getLong(GmailOAuthConfig.KEY_LAST_SYNC_AT, 0L)

    suspend fun getAccessToken(): String? =
        withContext(Dispatchers.IO) {
            val account = getLastSignedInAccount()?.account ?: return@withContext null
            runCatching {
                GoogleAuthUtil.getToken(
                    context,
                    account,
                    "oauth2:${GmailOAuthConfig.GMAIL_READONLY_SCOPE}",
                )
            }.getOrNull()
        }

    suspend fun signOut() {
        createSignInClient().signOut()
        prefs.edit().clear().apply()
    }
}
