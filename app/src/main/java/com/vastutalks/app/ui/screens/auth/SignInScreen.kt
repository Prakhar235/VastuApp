package com.vastutalks.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.ui.components.GradientButton
import com.vastutalks.app.ui.components.VastuTextField
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary

private enum class SignInMode { PASSWORD, OTP }

@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    onNavigateToCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var mode by remember { mutableStateOf(SignInMode.PASSWORD) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val uiState by authViewModel.uiState.collectAsState()

    var errorText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            errorText = uiState.errorMessage
            authViewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
        ) {
            Spacer(height = 48.dp)

            // Small logo mark — no full-bleed gradient background on this screen.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VastuPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(height = 24.dp)

            Text(
                text = "Welcome Back!",
                color = Color(0xFF2B2B2B),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sign in to continue your journey",
                color = TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Password / OTP toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .padding(4.dp)
            ) {
                ModeTab(
                    label = "Password",
                    selected = mode == SignInMode.PASSWORD,
                    modifier = Modifier.weight(1f)
                ) { mode = SignInMode.PASSWORD }
                ModeTab(
                    label = "OTP",
                    selected = mode == SignInMode.OTP,
                    modifier = Modifier.weight(1f)
                ) { mode = SignInMode.OTP }
            }

            Spacer(height = 20.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                Column {
                    Text("Email", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                    Spacer(height = 8.dp)
                    VastuTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "you@example.com",
                        leadingIcon = Icons.Filled.Email,
                        keyboardType = KeyboardType.Email
                    )

                    if (mode == SignInMode.PASSWORD) {
                        Spacer(height = 18.dp)
                        Text("Password", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                        Spacer(height = 8.dp)
                        VastuTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "Enter password",
                            leadingIcon = Icons.Filled.Lock,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
                        )

                        Spacer(height = 14.dp)
                        Text(
                            text = "Forgot Password?",
                            color = VastuPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onForgotPassword() }
                        )
                    } else {
                        Spacer(height = 16.dp)
                        Text(
                            text = "OTP sign-in isn't wired up yet — use Password to sign in for now.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    if (errorText != null) {
                        Spacer(height = 12.dp)
                        Text(
                            text = errorText ?: "",
                            color = Color(0xFFD32F2F),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(height = 24.dp)
                    GradientButton(
                        text = "Sign In",
                        enabled = mode == SignInMode.PASSWORD,
                        isLoading = uiState.isLoading,
                        onClick = {
                            errorText = null
                            authViewModel.signIn(email, password, onSuccess = onSignInSuccess)
                        }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Don't have an account? ", color = TextSecondary, fontSize = 14.sp)
                Text(
                    text = "Sign Up",
                    color = VastuPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToCreateAccount() }
                )
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) VastuPrimary else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.height(height))
}
