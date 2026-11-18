package com.pokke.music.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.ui.liquid.MoshiiGlass
import kotlinx.coroutines.launch

private const val OWNER_EMAIL = "lunethos.dev@gmail.com"
private const val OWNER_PIN = "4049959"
private val Accent = Color(0xFFFC3C44)
private val Bg = Color(0xFF07070A)

/**
 * Login rediseñado al estilo Moshii (auth_screen.dart):
 * fondo oscuro, blobs de acento, tarjeta glass, logo gradiente.
 * Mantiene AuthRepository, guest y PIN de owner.
 */
@Composable
fun LoginScreen(
    repository: AuthRepository,
    onLoginSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var needOwnerPin by remember { mutableStateOf(false) }
    var ownerPin by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val slide by animateFloatAsState(if (appeared) 0f else 24f, tween(420), label = "login-slide")
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(420), label = "login-alpha")

    Box(Modifier.fillMaxSize().background(Bg)) {
        // Ambient blobs (Moshii)
        Box(
            Modifier
                .offset((-80).dp, (-100).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.18f))
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(60.dp, (-80).dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.08f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .graphicsLayer { translationY = slide; this.alpha = alpha },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            // Logo
            Box(
                Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF5A62), Color(0xFFC41E3A)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Pokke", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                if (needOwnerPin) "Verificación Owner"
                else if (register) "Crea tu cuenta"
                else "Inicia sesión para continuar",
                color = Color.White.copy(0.55f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )

            MoshiiGlass(
                modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                enabled = true,
                shape = RoundedCornerShape(24.dp),
                opacity = 0.10f,
                elevation = 8.dp
            ) {
                Column(Modifier.padding(20.dp)) {
                    if (needOwnerPin) {
                        Text(
                            "Esta cuenta requiere PIN de Owner.",
                            color = Color.White.copy(0.6f),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        GlassField(
                            value = ownerPin,
                            onValueChange = { ownerPin = it.filter(Char::isDigit).take(12) },
                            hint = "PIN",
                            leading = Icons.Default.Lock,
                            isPassword = true,
                            showPassword = false,
                            onTogglePassword = null
                        )
                        message?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = if (error) Color(0xFFFF8A80) else Color(0xFF9BE7B0), fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        AccentButton("Verificar PIN", enabled = !busy) {
                            if (ownerPin == OWNER_PIN) {
                                needOwnerPin = false
                                onLoginSuccess()
                            } else {
                                error = true
                                message = "PIN incorrecto"
                                scope.launch { repository.signOut() }
                                needOwnerPin = false
                                ownerPin = ""
                            }
                        }
                        TextButton(onClick = {
                            scope.launch { repository.signOut() }
                            needOwnerPin = false
                            ownerPin = ""
                            message = null
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancelar", color = Color.White.copy(0.7f))
                        }
                    } else {
                        if (register) {
                            GlassField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                hint = "Nombre visible",
                                leading = Icons.Default.Person,
                                isPassword = false,
                                showPassword = false,
                                onTogglePassword = null
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        GlassField(
                            value = email,
                            onValueChange = { email = it },
                            hint = "Email",
                            leading = Icons.Default.Email,
                            isPassword = false,
                            showPassword = false,
                            onTogglePassword = null,
                            keyboardType = KeyboardType.Email
                        )
                        Spacer(Modifier.height(10.dp))
                        GlassField(
                            value = password,
                            onValueChange = { password = it },
                            hint = "Contraseña",
                            leading = Icons.Default.Lock,
                            isPassword = true,
                            showPassword = showPass,
                            onTogglePassword = { showPass = !showPass }
                        )
                        message?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = if (error) Color(0xFFFF8A80) else Color(0xFF9BE7B0), fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(18.dp))
                        AccentButton(
                            if (busy) "…" else if (register) "Crear cuenta" else "Iniciar sesión",
                            enabled = !busy
                        ) {
                            busy = true; message = null; error = false
                            scope.launch {
                                val result = if (register) {
                                    repository.signUp(email.trim(), password, displayName.trim().ifBlank { email.substringBefore('@') })
                                } else {
                                    repository.signIn(email.trim(), password).map { it }
                                }
                                result
                                    .onSuccess {
                                        if (email.trim().equals(OWNER_EMAIL, true) && !register) {
                                            needOwnerPin = true
                                            busy = false
                                        } else {
                                            busy = false
                                            onLoginSuccess()
                                        }
                                    }
                                    .onFailure {
                                        busy = false
                                        error = true
                                        message = it.localizedMessage ?: "No se pudo autenticar"
                                    }
                            }
                        }
                        TextButton(
                            onClick = { register = !register; message = null; error = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (register) "¿Ya tienes cuenta? Inicia sesión"
                                else "¿No tienes cuenta? Regístrate",
                                color = Color.White.copy(0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onContinueAsGuest) {
                Text("Continuar como invitado", color = Color.White.copy(0.5f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AccentButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean,
    showPassword: Boolean,
    onTogglePassword: (() -> Unit)?,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, color = Color.White.copy(0.35f)) },
        singleLine = true,
        leadingIcon = { Icon(leading, null, tint = Color.White.copy(0.55f)) },
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null,
                        tint = Color.White.copy(0.55f)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Accent.copy(0.7f),
            unfocusedBorderColor = Color.White.copy(0.12f),
            cursorColor = Accent,
            focusedContainerColor = Color.White.copy(0.04f),
            unfocusedContainerColor = Color.White.copy(0.03f)
        )
    )
}
