package com.example.trashway.conta

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Conta do usuário. O login é opcional: só é pedido para sugerir, confirmar ou cadastrar lixeiras.
class ContaViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _usuario = MutableLiveData<FirebaseUser?>(auth.currentUser)
    val usuario: LiveData<FirebaseUser?> get() = _usuario

    // Administradores ficam na coleção "admins" (um documento por uid, criado pelo console)
    private val _admin = MutableLiveData(false)
    val admin: LiveData<Boolean> get() = _admin

    private val ouvinte = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _usuario.value = firebaseAuth.currentUser
        verificarAdmin(firebaseAuth.currentUser)
    }

    init {
        auth.addAuthStateListener(ouvinte)
    }

    val logado: Boolean get() = auth.currentUser != null
    val uid: String? get() = auth.currentUser?.uid

    private fun verificarAdmin(usuario: FirebaseUser?) {
        if (usuario == null) {
            _admin.value = false
            return
        }
        db.collection("admins").document(usuario.uid).get()
            .addOnSuccessListener { doc -> if (auth.currentUser?.uid == usuario.uid) _admin.value = doc.exists() }
            .addOnFailureListener { _admin.value = false }
    }

    // Abre a escolha de conta do Google. Precisa do contexto de uma Activity.
    // Retorna false se o usuário cancelou.
    suspend fun entrarComGoogle(contextoActivity: Context): Boolean {
        val clientId = idClienteWeb(contextoActivity)
            ?: throw IllegalStateException(
                "default_web_client_id ausente: ative o login com Google no Firebase e baixe o google-services.json de novo"
            )
        val pedido = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()

        val resposta = try {
            CredentialManager.create(contextoActivity).getCredential(contextoActivity, pedido)
        } catch (e: GetCredentialCancellationException) {
            return false
        }

        val credencial = resposta.credential
        if (credencial !is CustomCredential ||
            credencial.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Credencial inesperada: ${credencial.type}")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credencial.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).aguardar()
        return true
    }

    suspend fun sair(contexto: Context) {
        auth.signOut()
        try {
            CredentialManager.create(contexto).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w("ContaViewModel", "Falha ao limpar a credencial salva", e)
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(ouvinte)
    }

    private companion object {
        // Gerado pelo plugin google-services a partir do google-services.json; buscado em tempo
        // de execução para o app compilar mesmo antes de o login com Google ser ativado
        @SuppressLint("DiscouragedApi")
        fun idClienteWeb(contexto: Context): String? {
            val id = contexto.resources.getIdentifier("default_web_client_id", "string", contexto.packageName)
            return if (id == 0) null else contexto.getString(id)
        }
    }
}

// Espera uma Task do Google Play Services dentro de uma corrotina
suspend fun <T> Task<T>.aguardar(): T = suspendCancellableCoroutine { continuacao ->
    addOnSuccessListener { continuacao.resume(it) }
    addOnFailureListener { continuacao.resumeWithException(it) }
}
