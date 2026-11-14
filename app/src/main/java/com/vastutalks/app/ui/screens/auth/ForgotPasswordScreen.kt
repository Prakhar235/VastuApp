package com.vastutalks.app.ui.screens.auth

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.vastutalks.app.ui.components.keepAboveKeyboard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastutalks.app.ui.components.DarkScreenStatusBar
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.Poppins
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuIndigo
import com.vastutalks.app.ui.theme.VastuPrimary

/**
 * The design's Forgot Password screen. Firebase resets a password through
 * a link it emails (there's no code to type in the app), so after the
 * email step this shows "Check your email" instead of the design's
 * OTP and new-password steps.
 */
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, authViewModel: AuthViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    DarkScreenStatusBar()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF9333EA), Color(0xFFDB2777))))
    ) {
        // Soft glows (radial gradients stand in for the design's blurred circles).
        Box(Modifier.align(Alignment.TopEnd).offset(x = 110.dp, y = 30.dp).size(320.dp).background(Brush.radialGradient(listOf(Color(0x4DC084FC), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-110).dp, y = (-30).dp).size(380.dp).background(Brush.radialGradient(listOf(Color(0x33F472B6), Color.Transparent))))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(start = 21.dp, end = 21.dp, top = 28.dp, bottom = 14.dp)
            ) {
                if (!sent) {
                    Row(
                        Modifier.padding(bottom = 21.dp).clickable(onClick = onBack),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ChevronLeft, "Back", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
                        Text(" Back", color = Color.White.copy(alpha = 0.9f), fontSize = 12.25.sp)
                    }
                }
                Text(if (sent) "Check your email" else "Forgot Password", color = Color.White, fontSize = 26.25.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (sent) "We've sent you a link to reset your password" else "Enter your email to receive a reset link",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 12.25.sp, modifier = Modifier.padding(top = 7.dp)
                )
            }

            Column(
                Modifier
                    .padding(start = 21.dp, end = 21.dp, bottom = 21.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(21.dp), spotColor = Color(0x33581C87))
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color.White)
                    .padding(21.dp),
                verticalArrangement = Arrangement.spacedBy(21.dp)
            ) {
                if (!sent) {
                    Column {
                        Text("Email", fontSize = 12.25.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.padding(bottom = 7.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .shadow(1.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF9FAFB))
                                .padding(horizontal = 14.dp, vertical = 12.25.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Email, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                                if (email.isEmpty()) Text("you@example.com", color = Color(0xFFA0AEC0), fontSize = 12.25.sp)
                                BasicTextField(
                                    value = email,
                                    onValueChange = { email = it.trim(); error = null },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 12.25.sp, color = TextPrimary, fontFamily = Poppins),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    cursorBrush = SolidColor(VastuPrimary),
                                    modifier = Modifier.fillMaxWidth().keepAboveKeyboard(spaceBelow = 96.dp)
                                )
                            }
                        }
                        error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, start = 3.5.dp)) }
                    }
                    GradientCta(if (sending) null else "Send Reset Link", enabled = email.contains('@')) {
                        sending = true
                        authViewModel.requestPasswordReset(email) { ok, message ->
                            sending = false
                            if (ok) sent = true else error = message
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(84.dp)
                                .shadow(10.dp, CircleShape, spotColor = Color(0x4D51CF66))
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF51CF66), Color(0xFF22C55E)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.MarkEmailRead, null, tint = Color.White, modifier = Modifier.size(44.dp))
                        }
                        Text("Reset link sent", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(top = 21.dp))
                        Text(
                            "Open the email we sent to $email and follow the link to create a new password, then sign in.",
                            color = TextSecondary, fontSize = 12.25.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.5.dp, bottom = 28.dp).widthIn(max = 280.dp)
                        )
                        GradientCta("Back to Sign In", enabled = true, onClick = onBack)
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientCta(text: String?, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = Color(0x406C5CE7))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(VastuPrimary, VastuIndigo)))
            .clickable(enabled = enabled && text != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text == null) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        else Text(text, color = Color.White.copy(alpha = if (enabled) 1f else 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
