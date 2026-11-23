package io.github.rex25415.haiyu.schedule.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.rex25415.haiyu.schedule.BuildConfig
import java.security.MessageDigest
import java.util.Locale

enum class SignatureTrustState {
    VERIFIED_ORIGINAL,
    DEBUG_LOCAL_BUILD,
    THIRD_PARTY_MODIFIED
}

data class SignatureVerificationResult(
    val state: SignatureTrustState,
    val actualSha256: String,
    val shortFingerprint: String
)

/**
 * 运行时安装包签名证书验真器：
 * 对比当前 APK 证书 SHA-256 与本项目公开声明的统一发布证书指纹，
 * 防止第三方二次打包植入广告或篡改后冒充原作者发布版本。
 */
object AppSignatureVerifier {

    const val EXPECTED_RELEASE_CERT_SHA256 =
        "9d7c62e6a8b6099970964d7ff8cf6918c82422d2b37985523f40517898ec26ce"

    fun evaluateFingerprints(
        actualFingerprints: List<String>,
        isDebugBuild: Boolean
    ): SignatureVerificationResult {
        val normalized = actualFingerprints
            .map { it.replace(":", "").trim().lowercase(Locale.US) }
            .filter { it.isNotEmpty() }
        val primary = normalized.firstOrNull().orEmpty()
        val short = formatShortFingerprint(primary)

        val state = when {
            normalized.any { it.equals(EXPECTED_RELEASE_CERT_SHA256, ignoreCase = true) } ->
                SignatureTrustState.VERIFIED_ORIGINAL
            isDebugBuild ->
                SignatureTrustState.DEBUG_LOCAL_BUILD
            else ->
                SignatureTrustState.THIRD_PARTY_MODIFIED
        }
        return SignatureVerificationResult(
            state = state,
            actualSha256 = primary,
            shortFingerprint = short
        )
    }

    fun formatShortFingerprint(sha256: String): String {
        val clean = sha256.replace(":", "").trim().uppercase(Locale.US)
        if (clean.length < 16) return clean.ifBlank { "未知" }
        return "${clean.take(8)}…${clean.takeLast(8)}"
    }

    @Suppress("DEPRECATION")
    fun verifyCurrentApp(context: Context): SignatureVerificationResult {
        val fingerprints = mutableListOf<String>()
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                val sigs = when {
                    signingInfo == null -> emptyArray()
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                    else -> signingInfo.signingCertificateHistory
                }
                sigs?.forEach { sig ->
                    fingerprints.add(sha256Hex(sig.toByteArray()))
                }
            } else {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                info.signatures?.forEach { sig ->
                    fingerprints.add(sha256Hex(sig.toByteArray()))
                }
            }
        } catch (_: Exception) {
        }
        return evaluateFingerprints(fingerprints, BuildConfig.DEBUG)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
