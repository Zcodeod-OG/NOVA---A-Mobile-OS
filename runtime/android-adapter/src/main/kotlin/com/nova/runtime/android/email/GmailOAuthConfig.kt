package com.nova.runtime.android.email

/**
 * Gmail OAuth configuration.
 *
 * Replace [WEB_CLIENT_ID] with the Web application client ID from Google Cloud Console
 * (APIs & Services → Credentials → OAuth 2.0 Client IDs). Enable Gmail API for the project.
 */
object GmailOAuthConfig {
    const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    const val TOKEN_PREFS = "nova_gmail_oauth"
    const val KEY_ACCOUNT_EMAIL = "account_email"
    const val KEY_LAST_SYNC_AT = "last_sync_at"
}
