package com.gestramed

import android.content.Context
import android.net.Uri
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import java.text.SimpleDateFormat
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Date
import java.util.Locale
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object S3Uploader {

    private val insecureSslConfigured = AtomicBoolean(false)

    suspend fun uploadUri(context: Context, uri: Uri, flow: UploadFlow, code: String): Result<Unit> {
        return runCatching {
            ensureSecurityProvider(context)
            configureInsecureSslForDebugIfEnabled()
            validateConfig()

            val fileName = context.displayName(uri)
            val flowLabel = when (flow) {
                UploadFlow.ORDENES -> "orden"
                UploadFlow.AUTORIZACIONES -> "autorizacion"
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val customFileName = "${code}-${flowLabel}-$timestamp-$fileName"
            val tempFile = copyToCache(context, uri, fileName)
            val key = "${flow.prefix}/$customFileName"

            val credentials: AWSCredentials = BasicAWSCredentials(
                BuildConfig.AWS_ACCESS_KEY,
                BuildConfig.AWS_SECRET_KEY
            )
            val s3Client = AmazonS3Client(credentials)
            s3Client.setRegion(Region.getRegion(Regions.fromName(BuildConfig.AWS_REGION)))

            TransferNetworkLossHandler.getInstance(context.applicationContext)

            val transferUtility = TransferUtility.builder()
                .context(context.applicationContext)
                .s3Client(s3Client)
                .build()

            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)

                fun finishSuccess() {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                fun finishError(ex: Throwable) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resumeWithException(ex)
                    }
                }

                val observer = transferUtility.upload(BuildConfig.S3_BUCKET, key, tempFile)

                observer.setTransferListener(object : TransferListener {
                    override fun onStateChanged(id: Int, state: TransferState?) {
                        if (state == TransferState.COMPLETED) {
                            tempFile.delete()
                            finishSuccess()
                        } else if (state == TransferState.FAILED || state == TransferState.CANCELED) {
                            tempFile.delete()
                            finishError(IllegalStateException("Fallo en subida a S3"))
                        }
                    }

                    override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                        // Optional: progress updates can be surfaced in UI if needed.
                    }

                    override fun onError(id: Int, ex: Exception?) {
                        tempFile.delete()
                        finishError(ex ?: IllegalStateException("Error desconocido en S3"))
                    }
                })

                continuation.invokeOnCancellation {
                    observer.cleanTransferListener()
                    tempFile.delete()
                }
            }
        }
    }

    private fun ensureSecurityProvider(context: Context) {
        try {
            ProviderInstaller.installIfNeeded(context)
        } catch (ex: GooglePlayServicesRepairableException) {
            throw IllegalStateException("Se requiere actualizar Google Play Services para conexión segura", ex)
        } catch (ex: GooglePlayServicesNotAvailableException) {
            throw IllegalStateException("Google Play Services no disponible para conexión segura", ex)
        }
    }

    private fun configureInsecureSslForDebugIfEnabled() {
        if (!BuildConfig.DEBUG || !BuildConfig.ALLOW_INSECURE_SSL_DEBUG || insecureSslConfigured.get()) {
            return
        }

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier(HostnameVerifier { _, _ -> true })
        insecureSslConfigured.set(true)
    }

    private fun validateConfig() {
        require(BuildConfig.AWS_ACCESS_KEY.isNotBlank()) { "AWS_ACCESS_KEY no configurada" }
        require(BuildConfig.AWS_SECRET_KEY.isNotBlank()) { "AWS_SECRET_KEY no configurada" }
        require(BuildConfig.S3_BUCKET.isNotBlank()) { "S3_BUCKET no configurado" }
        require(BuildConfig.AWS_REGION.isNotBlank()) { "AWS_REGION no configurada" }
    }

    private fun copyToCache(context: Context, uri: Uri, fileName: String): File {
        val safeName = fileName.replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val tempFile = File(context.cacheDir, safeName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se pudo abrir el archivo seleccionado" }
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }
}
