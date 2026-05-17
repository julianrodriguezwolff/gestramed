package com.gestramed

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GestramedBlue = Color(0xFF0077C8)

class MainActivity : ComponentActivity() {

    private val aboutMessage =
        "Prototipo Gestramed\n" + 
            "Gestión de Tratamientos Médicos\n" +
            "Versión 1.0.0 (Mayo, 2026)\n" +
            "Autor: Julián Andrés Rodríguez Wolff\n" +
            "Correo: julianrodriguezwolff@gmail.com"

    private var selectedFlow: UploadFlow = UploadFlow.ORDENES
    private var statusMessage by mutableStateOf("Seleccione Órdenes o Autorizaciones para ser cargadas")
    private var isUploading by mutableStateOf(false)
    private var accessCode by mutableStateOf("")
    private var accessCodeError by mutableStateOf<String?>(null)
    private var isAccessValidated by mutableStateOf(false)

    private fun isValidAccessCode(code: String): Boolean = code.matches(Regex("^[A-Za-z0-9]{5}$"))

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            statusMessage = "Selección cancelada"
            return@registerForActivityResult
        }

        statusMessage = "Subiendo archivo a S3..."
        isUploading = true

        contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        lifecycleScope.launch {
            val codeForFile = accessCode
            val result = withContext(Dispatchers.IO) {
                S3Uploader.uploadUri(
                    context = this@MainActivity,
                    uri = uri,
                    flow = selectedFlow,
                    code = codeForFile
                )
            }

            if (result.isSuccess) {
                statusMessage = when (selectedFlow) {
                    UploadFlow.ORDENES -> "Orden cargada correctamente.\nDile a Alexa: Abre Gestión de tratamientos médicos.\nPosteriormente dile a Alexa: Consultar órdenes pendientes."
                    UploadFlow.AUTORIZACIONES -> "Autorización cargada correctamente.\nDile a Alexa: Abre Gestión de tratamientos médicos.\nPosteriormente dile a Alexa: Consultar autorizaciones pendientes."
                }
                isUploading = false
            } else {
                statusMessage = "Error al subir archivo"
                isUploading = false
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    println("Error GESTRAMED -->")
                    exception.printStackTrace()
                }
                Toast.makeText(
                    this@MainActivity,
                    "Error al cargar: ${exception?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher),
                        contentDescription = "Logo de Gestramed",
                        modifier = Modifier
                            .size(128.dp)
                            .padding(bottom = 8.dp)
                    )


                    if (!isAccessValidated) {
                        Text(
                            text = "Dile a Alexa: Abre Gestión de tratamientos médicos.\nPosteriormente dile: Código de carga.\nIngresa el código que te dice Alexa a continuación:",
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = accessCode,
                            onValueChange = { input ->
                                val sanitized = input.filter { it.isLetterOrDigit() }.take(5).uppercase()
                                accessCode = sanitized
                                accessCodeError = null
                            },
                            singleLine = true,
                            label = { Text("Código (5 caracteres)") },
                            isError = accessCodeError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (accessCode.length == 5 && isValidAccessCode(accessCode)) {
                                        isAccessValidated = true
                                        statusMessage = "Seleccione Órdenes o Autorizaciones para ser cargadas"
                                        accessCodeError = null
                                    } else if (accessCode.length == 5) {
                                        accessCodeError = "El código debe ser alfanumérico de 5 caracteres"
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (accessCodeError != null) {
                            Text(
                                text = accessCodeError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (isValidAccessCode(accessCode)) {
                                    isAccessValidated = true
                                    statusMessage = "Seleccione Órdenes o Autorizaciones para ser cargadas"
                                    accessCodeError = null
                                } else {
                                    accessCodeError = "El código debe ser alfanumérico de 5 caracteres"
                                }
                            },
                            enabled = accessCode.length == 5,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GestramedBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text("Continuar")
                        }
                    } else {
                        Text(
                            text = statusMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                        }

                        Button(
                            onClick = {
                                selectedFlow = UploadFlow.ORDENES
                                statusMessage = "Seleccionando archivo para Órdenes..."
                                picker.launch(arrayOf("application/pdf", "image/jpeg"))
                            },
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GestramedBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Órdenes")
                        }

                        Button(
                            onClick = {
                                selectedFlow = UploadFlow.AUTORIZACIONES
                                statusMessage = "Seleccionando archivo para Autorizaciones..."
                                picker.launch(arrayOf("application/pdf", "image/jpeg"))
                            },
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GestramedBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text("Autorizaciones")
                        }

                        Button(
                            onClick = {
                                statusMessage = aboutMessage
                            },
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GestramedBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text("Acerca de")
                        }
                    }
                }
            }
        }
    }
}

enum class UploadFlow(val prefix: String) {
    ORDENES("ordenes"),
    AUTORIZACIONES("autorizaciones")
}

fun Context.displayName(uri: Uri): String {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return "documento"
}
