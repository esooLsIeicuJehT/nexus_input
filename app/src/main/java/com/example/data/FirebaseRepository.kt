package com.example.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.example.model.MappingConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository(private val context: Context) {
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "FirebaseAuth not available: ${e.message}")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "FirebaseFirestore not available: ${e.message}")
            null
        }
    }

    val currentUser: com.google.firebase.auth.FirebaseUser?
        get() = try { auth?.currentUser } catch (e: Exception) { null }

    fun authStateFlow(): Flow<com.google.firebase.auth.FirebaseUser?> = callbackFlow {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }
        val authStateListener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser)
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        trySend(firebaseAuth.currentUser)
        awaitClose { firebaseAuth.removeAuthStateListener(authStateListener) }
    }

    suspend fun signInWithGoogleCredential(
        webClientId: String = "393750783022-placeholder.apps.googleusercontent.com"
    ): Result<com.google.firebase.auth.FirebaseUser> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase Auth is not initialized."))
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val googleCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(googleCredential).await()
                val user = authResult.user
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Firebase user is null after sign in."))
                }
            } else {
                Result.failure(Exception("Invalid credential type received."))
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Google Sign-In failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Sign out error: ${e.message}")
        }
    }

    suspend fun syncConfigToFirestore(userId: String, config: MappingConfig): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore is not initialized."))
        return try {
            db.collection("users")
                .document(userId)
                .collection("profiles")
                .document(config.id)
                .set(config)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Firestore sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchConfigsFromFirestore(userId: String): Result<List<MappingConfig>> {
        val db = firestore ?: return Result.failure(Exception("Firestore is not initialized."))
        return try {
            val snapshot = db.collection("users")
                .document(userId)
                .collection("profiles")
                .get()
                .await()
            val list = snapshot.documents.mapNotNull { doc ->
                doc.toObject(MappingConfig::class.java)
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Firestore fetch failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
