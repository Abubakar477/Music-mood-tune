package com.abubakar.musicplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abubakar.musicplayer.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeoutRunnable: Runnable

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-in
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()

        // Check for the placeholder string to avoid errors and warn the user
        val webClientId = getString(R.string.default_web_client_id)
        if (webClientId == "YOUR_WEB_CLIENT_ID_HERE") {
            Log.w(TAG, "Google Sign-In not configured: default_web_client_id is still the placeholder.")
        } else {
            gsoBuilder.requestIdToken(webClientId)
        }

        googleSignInClient = GoogleSignIn.getClient(this, gsoBuilder.build())

        timeoutRunnable = Runnable {
            stopLoading()
            val msg = "Connection timed out. Please check your internet connection or try again."
            Log.e(TAG, msg)
            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
        }

        // Check if user is already logged in
        if (auth.currentUser != null) {
            Log.d(TAG, "User already logged in, redirecting to MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startLoading()
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    stopLoading()
                    Log.d(TAG, "signInWithEmail:success")
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    stopLoading()
                    val msg = "Authentication failed: ${e.message}"
                    Log.e(TAG, msg, e)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
        }

        binding.googleSignInButton.setOnClickListener {
            val clientId = getString(R.string.default_web_client_id)
            if (clientId == "YOUR_WEB_CLIENT_ID_HERE") {
                val msg = "Please configure Firebase and update Web Client ID in strings.xml"
                Log.e(TAG, msg)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            } else {
                signInWithGoogle()
            }
        }

        binding.signUpTextView.setOnClickListener {
             startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun signInWithGoogle() {
        startLoading()
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.result
                if (account?.idToken != null) {
                    firebaseAuthWithGoogle(account.idToken!!)
                } else {
                    stopLoading()
                    val msg = "Google Sign-In failed: No ID Token found. Check your SHA-1 in Firebase Console."
                    Log.e(TAG, msg)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                stopLoading()
                val msg = "Google sign in failed: ${e.message}"
                Log.e(TAG, msg, e)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                stopLoading()
                Log.d(TAG, "firebaseAuthWithGoogle:success")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                stopLoading()
                val msg = "Authentication Failed: ${e.message}"
                Log.e(TAG, msg, e)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
    }

    private fun startLoading() {
        showLoading(true)
        // Set a timeout of 15 seconds
        handler.postDelayed(timeoutRunnable, 15000)
    }

    private fun stopLoading() {
        handler.removeCallbacks(timeoutRunnable)
        showLoading(false)
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.loginButton.isEnabled = false
            binding.googleSignInButton.isEnabled = false
        } else {
            binding.progressBar.visibility = View.GONE
            binding.loginButton.isEnabled = true
            binding.googleSignInButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeoutRunnable)
    }
}
