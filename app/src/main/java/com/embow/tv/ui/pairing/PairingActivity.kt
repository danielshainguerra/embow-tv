package com.embow.tv.ui.pairing

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.embow.tv.BuildConfig
import com.embow.tv.data.network.ApiClient
import com.embow.tv.data.repository.EmbowRepository
import com.embow.tv.data.storage.SecureStorage
import com.embow.tv.databinding.ActivityPairingBinding
import com.embow.tv.ui.player.MainActivity
import com.embow.tv.util.QRCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding
    private lateinit var repository: EmbowRepository
    private var pairingCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val secureStorage = SecureStorage(this)
        val apiClient = ApiClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY
        )
        repository = EmbowRepository(apiClient, secureStorage)

        if (repository.isDeviceActivated()) {
            validateAndNavigate()
            return
        }

        setupPairing()
    }

    private fun validateAndNavigate() {
        lifecycleScope.launch {
            val result = repository.validateDeviceToken()
            result.fold(
                onSuccess = { isValid ->
                    if (isValid) {
                        navigateToPlayer()
                    } else {
                        setupPairing()
                    }
                },
                onFailure = {
                    setupPairing()
                }
            )
        }
    }

    private fun setupPairing() {
        val deviceId = repository.getDeviceId()
        pairingCode = generatePairingCode()

        binding.tvDeviceId.text = "Device ID: $deviceId"
        binding.tvPairingCode.text = pairingCode

        val qrContent = "embow://pair?code=$pairingCode&device=$deviceId"
        val qrBitmap = QRCodeGenerator.generateQRCode(qrContent, 400)
        binding.ivQrCode.setImageBitmap(qrBitmap)

        binding.tvInstructions.text =
            "1. Visit app.embow.com on your phone or computer\n" +
            "2. Scan this QR code or enter code: $pairingCode\n" +
            "3. Wait for activation..."

        startPolling()
    }

    private fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun startPolling() {
        lifecycleScope.launch {
            while (isActive) {
                checkActivation()
                delay(3000)
            }
        }
    }

    private suspend fun checkActivation() {
        val result = repository.claimDeviceToken(pairingCode)
        result.fold(
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Device activated!", Toast.LENGTH_SHORT).show()
                    navigateToPlayer()
                }
            },
            onFailure = {
            }
        )
    }

    private fun navigateToPlayer() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
