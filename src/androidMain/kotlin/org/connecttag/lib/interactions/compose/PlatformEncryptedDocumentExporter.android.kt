package org.connecttag.lib.interactions.compose

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@Composable
actual fun rememberPlatformEncryptedDocumentExporter(
    onResult: (Boolean) -> Unit,
): PlatformEncryptedDocumentExporter {
    val context = LocalView.current.context
    val currentOnResult by rememberUpdatedState(onResult)
    var pendingPayload by remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val payload = pendingPayload
        pendingPayload = null
        val saved = uri != null && payload != null && runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(payload) }
                ?: error("Unable to open encrypted document destination")
        }.isSuccess
        currentOnResult(saved)
    }
    return remember(context, launcher) {
        PlatformEncryptedDocumentExporter { request ->
            runCatching { encrypt(request.plainText, request.keyAlias) }
                .onSuccess { encrypted ->
                    pendingPayload = encrypted
                    launcher.launch(request.fileName)
                }
                .onFailure { currentOnResult(false) }
        }
    }
}

private fun encrypt(plainText: String, keyAlias: String): ByteArray {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val key = (keyStore.getKey(keyAlias, null) as? SecretKey) ?: KeyGenerator
        .getInstance("AES", "AndroidKeyStore")
        .run {
            init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    keyAlias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
    return buildString {
        appendLine("PLATFORMTAG-ENCRYPTED-DOCUMENT-V1")
        appendLine(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        append(Base64.encodeToString(ciphertext, Base64.NO_WRAP))
    }.toByteArray(StandardCharsets.UTF_8)
}
