package com.example.data

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object LocalCryptoUtils {
    
    fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "default_hash_error"
        }
    }

    private fun getAESKeySpec(key: String): SecretKeySpec {
        var rawKey = key
        // Make sure key matches AES 128-bit key size (16 bytes)
        while (rawKey.length < 16) rawKey += "0"
        if (rawKey.length > 16) rawKey = rawKey.substring(0, 16)
        return SecretKeySpec(rawKey.toByteArray(Charsets.UTF_8), "AES")
    }

    fun encrypt(plainText: String, key: String): String {
        return try {
            if (key.isBlank()) return plainText
            val keySpec = getAESKeySpec(key)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            "ENC_ERR_BASE64_" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
        }
    }

    fun decrypt(encryptedText: String, key: String): String {
        if (key.isBlank()) return encryptedText
        if (!encryptedText.startsWith("ENC_ERR_BASE64_")) {
            try {
                val keySpec = getAESKeySpec(key)
                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec)
                val decryptedBytes = cipher.doFinal(Base64.decode(encryptedText, Base64.DEFAULT))
                return String(decryptedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                // Return fallback if key doesn't match
            }
        }
        
        // Decrypt fallback structure
        return try {
            val stripped = encryptedText.removePrefix("ENC_ERR_BASE64_")
            String(Base64.decode(stripped, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            "[Decryption Failed - Unauthorized Key Signature]"
        }
    }
}
