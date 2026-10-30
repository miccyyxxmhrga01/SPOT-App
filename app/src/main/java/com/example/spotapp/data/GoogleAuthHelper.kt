package com.example.spotapp.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.spotapp.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID

object GoogleAuthHelper {
    suspend fun signInWithGoogle(context: Context): GoogleSignInResult {
        // Google signs the hashed nonce into the ID token; Supabase checks it against the raw one.
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return runGoogleSignIn(
            requestIdToken = {
                // Use the explicit button flow, with a web OAuth client and foreground context.
                val option = GetSignInWithGoogleOption.Builder(
                    context.getString(R.string.default_web_client_id)
                ).setNonce(hashedNonce).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = CredentialManager.create(context).getCredential(context, request).credential
                check(credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    "Google ID credential was not returned."
                }
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            },
            authenticate = { idToken ->
                Supabase.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    provider = Google
                    nonce = rawNonce
                }
                check(Supabase.uid() != null) { "Google account could not be loaded." }
            },
            onFailure = { stage, error -> logFailure("SpotGoogleAuth", "$stage failed", error) }
        )
    }
}
