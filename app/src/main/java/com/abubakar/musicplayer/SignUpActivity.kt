package com.abubakar.musicplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abubakar.musicplayer.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeoutRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        timeoutRunnable = Runnable {
            stopLoading()
            val msg = "Connection timed out. Please check your internet connection or try again."
            Toast.makeText(this@SignUpActivity, msg, Toast.LENGTH_LONG).show()
        }

        binding.signUpButton.setOnClickListener {
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

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            startLoading()
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    stopLoading()
                    Toast.makeText(this, "Account created successfully.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    stopLoading()
                    val errorMessage = e.message ?: "Unknown error"
                    Toast.makeText(this, "Registration failed: $errorMessage", Toast.LENGTH_LONG).show()
                }
        }

        binding.loginTextView.setOnClickListener {
            // Go back to Login Activity
            finish()
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
            binding.signUpButton.isEnabled = false
        } else {
            binding.progressBar.visibility = View.GONE
            binding.signUpButton.isEnabled = true
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeoutRunnable)
    }
}
