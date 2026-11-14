package com.vastutalks.app.ui.screens.auth

import com.vastutalks.app.ui.components.keepAboveKeyboard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.vastutalks.app.ui.components.OutlineButton
import com.vastutalks.app.ui.components.OrDivider
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastutalks.app.ui.components.AiOrb
import com.vastutalks.app.ui.components.CompassMark
import com.vastutalks.app.ui.components.DarkScreenStatusBar
import com.vastutalks.app.ui.components.GlassPill
import com.vastutalks.app.ui.components.NightHero
import com.vastutalks.app.ui.components.PrimaryButton
import com.vastutalks.app.ui.components.SecureNote
import com.vastutalks.app.ui.components.UnderlineTabs
import com.vastutalks.app.ui.components.VastuTextField
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.SunsetTextGradient
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuSun

@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    onNavigateToCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel()
) {
    var phone by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val uiState by authViewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val comingSoon: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }

    var errorText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            errorText = uiState.errorMessage
            authViewModel.clearError()
        }
    }

    DarkScreenStatusBar()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        AiHero()

        Column(
            Modifier
                .offset(y = (-20).dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (EXTRA_SIGN_IN_OPTIONS) UnderlineTabs(listOf("Email", "Mobile"), tab, { tab = it })

            if (tab == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VastuTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Email",
                        leadingIcon = null,
                        keyboardType = KeyboardType.Email
                    )
                    VastuTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Password",
                        leadingIcon = null,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
                    )
                    Text(
                        "Forgot password?",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { onForgotPassword() }
                    )
                    if (errorText != null) {
                        Text(errorText ?: "", color = Danger, fontSize = 12.sp)
                    }
                    if (uiState.isLoading) {
                        Box(Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VastuPrimary, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        PrimaryButton(
                            text = "Sign In",
                            onClick = {
                                errorText = null
                                authViewModel.signIn(email.trim(), password, onSuccess = onSignInSuccess)
                            }
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF6F6F9))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+91", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Box(Modifier.weight(1f).padding(start = 10.dp)) {
                            if (phone.isEmpty()) Text("Mobile number", color = com.vastutalks.app.ui.theme.TextHint, fontSize = 14.sp)
                            androidx.compose.foundation.text.BasicTextField(
                                value = phone,
                                onValueChange = { phone = it.filter(Char::isDigit).take(10) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextPrimary, fontFamily = com.vastutalks.app.ui.theme.Poppins),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(VastuPrimary),
                                modifier = Modifier.fillMaxWidth().keepAboveKeyboard(spaceBelow = 96.dp)
                            )
                        }
                    }
                    // Phone sign-in needs Firebase phone auth, which isn't set up yet.
                    PrimaryButton(text = "Get OTP", onClick = { comingSoon("Sign in with a mobile number is coming soon. Use your email for now.") })
                }
            }

            if (EXTRA_SIGN_IN_OPTIONS) {
                OrDivider(Modifier.padding(top = 4.dp))
                OutlineButton(
                    "Continue with Google",
                    onClick = { comingSoon("Google sign-in is coming soon. Use your email for now.") },
                    leading = { GoogleMark() }
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("New here? ", color = TextSecondary, fontSize = 12.5.sp)
                Text(
                    "Create account",
                    color = VastuPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToCreateAccount() }
                )
            }
            SecureNote(Modifier.padding(bottom = 20.dp))
        }
    }
}

/** "India’s First AI Vastu" hero with the spinning orb and score chips. */
@Composable
private fun AiHero() {
    NightHero(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 21.dp, end = 21.dp, top = 12.dp, bottom = 34.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { CompassMark(17.dp) }
                Spacer(Modifier.width(8.dp))
                Text("Vastu Talks", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.horizontalGradient(listOf(VastuSun, Color(0xFFFB923C))))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("NEW · AI", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp)
                    .height(118.dp),
                contentAlignment = Alignment.Center
            ) {
                AiOrb(110.dp, iconSize = 44.dp)
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 10.dp)
                        .shadow(14.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.95f))
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    Text("VASTU SCORE", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                    Text(
                        buildAnnotatedString {
                            append("86")
                            withStyle(SpanStyle(fontSize = 10.sp, color = Color(0xFF22A06B))) { append(" /100") }
                        },
                        color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp
                    )
                }
                GlassPill(
                    "NE zone balanced",
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 12.dp)
                ) { Icon(Icons.Outlined.AutoAwesome, null, tint = VastuSun, modifier = Modifier.size(11.dp)) }
                GlassPill(
                    "Scanning floor plan…",
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 14.dp)
                ) { Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF51CF66))) }
            }

            Text(
                buildAnnotatedString {
                    append("India’s First ")
                    withStyle(SpanStyle(brush = SunsetTextGradient)) { append("AI Vastu") }
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Scan your floor plan and get your Vastu score with smart remedies in seconds.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .widthIn(max = 290.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                GlassPill("Floor plan scan")
                GlassPill("Instant score")
                GlassPill("Smart remedies")
            }
        }
    }
}

/**
 * Mobile-number (OTP) and Google sign-in aren't set up in Firebase yet, so
 * they're hidden and only email sign-in shows. Flip to true once they work.
 */
private const val EXTRA_SIGN_IN_OPTIONS = false

/** The four-colour Google "G". */
@Composable
private fun GoogleMark() {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2
        val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
        val st = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
        drawArc(Color(0xFFEA4335), 200f, 115f, false, tl, arc, style = st)
        drawArc(Color(0xFFFBBC05), 135f, 65f, false, tl, arc, style = st)
        drawArc(Color(0xFF34A853), 45f, 90f, false, tl, arc, style = st)
        drawArc(Color(0xFF4285F4), -15f, 60f, false, tl, arc, style = st)
        drawRect(Color(0xFF4285F4), topLeft = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2 - stroke / 2), size = androidx.compose.ui.geometry.Size(size.width / 2 - inset / 2, stroke))
    }
}
