package com.vastutalks.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.model.UserRole
import com.vastutalks.app.data.repository.UserRepository
import com.vastutalks.app.ui.components.CompassMark
import com.vastutalks.app.ui.components.DarkScreenStatusBar
import com.vastutalks.app.ui.components.VastuTextField
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.launch

/** Dark partner-app gradient used across the expert screens. */
val PartnerHeroGradient = Brush.linearGradient(listOf(Color(0xFF0B0920), Color(0xFF1E1650), Color(0xFF2E1A5E)))
val PartnerGold = Brush.linearGradient(listOf(Color(0xFFFFD93D), Color(0xFFF59E0B)))
private val PartnerInk = Color(0xFF1A1440)
private val Slate = Color(0xFF64748B)

/**
 * The design's "Expert sign in" for the Partner app. Experts sign in with
 * the email and password they applied with (mobile OTP isn't set up in
 * Firebase yet, as on the user sign-in). A non-expert account is turned
 * away with a pointer to the user sign-in.
 */
@Composable
fun ExpertSignInScreen(
    onSignedIn: () -> Unit,
    onUserSignIn: () -> Unit,
    onApply: () -> Unit,
    onForgotPassword: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val uiState by authViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            errorText = uiState.errorMessage
            authViewModel.clearError()
        }
    }

    fun signIn() {
        errorText = null
        if (email.isBlank() || password.isEmpty()) {
            errorText = "Enter your registered email and password"
            return
        }
        authViewModel.signIn(email.trim(), password, onSuccess = {
            checking = true
            scope.launch {
                val role = UserRepository().getCurrentUserRole().getOrNull()
                checking = false
                if (role == UserRole.VASTU_EXPERT) onSignedIn()
                else {
                    FirebaseAuth.getInstance().signOut()
                    errorText = "This isn't a partner account. Use User sign in instead."
                }
            }
        })
    }

    DarkScreenStatusBar()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        PartnerHero(onBack = onUserSignIn)

        Column(
            Modifier
                .offset(y = (-20).dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldLabel("Registered email")
                VastuTextField(value = email, onValueChange = { email = it }, placeholder = "you@example.com", leadingIcon = null, keyboardType = KeyboardType.Email)
                FieldLabel("Password")
                VastuTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Enter password",
                    leadingIcon = null,
                    isPassword = true,
                    passwordVisible = showPassword,
                    onTogglePasswordVisibility = { showPassword = !showPassword }
                )
                Text(
                    "Forgot password?",
                    color = Slate,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.End).clickable(onClick = onForgotPassword)
                )
                errorText?.let { Text(it, color = Danger, fontSize = 12.sp) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PartnerInk)
                        .clickable(enabled = !uiState.isLoading && !checking, onClick = ::signIn),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading || checking) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    else Text("Sign in", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Not a partner yet? → the expert application.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFFBEB))
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.PersonAddAlt, null, tint = Color(0xFFB45309), modifier = Modifier.size(17.dp)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Not a partner yet?", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Apply in 2 minutes · verified within 24 hrs", fontSize = 11.sp, color = Color(0xFF78350F))
                }
                Box(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TextPrimary)
                        .clickable(onClick = onApply)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Apply", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                Text("Looking for Vastu advice? ", color = Slate, fontSize = 12.5.sp)
                Text("User sign in", color = VastuPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onUserSignIn))
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Client details stay private and encrypted", color = Slate, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A5568))
}

@Composable
private fun PartnerHero(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(PartnerHeroGradient)
    ) {
        // Warm and violet glows, as in the design.
        Box(Modifier.align(Alignment.TopEnd).offset(x = 50.dp, y = (-70).dp).size(220.dp).blur(80.dp).clip(CircleShape).background(Color(0x59F59E0B)))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-50).dp, y = 90.dp).size(200.dp).blur(70.dp).clip(CircleShape).background(Color(0x736C5CE7)))
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 44.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Back to user sign in", onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to user sign in", tint = Color.White, modifier = Modifier.size(18.dp)) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CompassMark(16.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("Vastu Talks", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "PARTNER",
                        color = TextPrimary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(PartnerGold).padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(36.dp))
            }
            Row(Modifier.padding(top = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(PartnerGold),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.WorkspacePremium, null, tint = TextPrimary, modifier = Modifier.size(28.dp)) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Expert sign in", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                    Text(
                        "Manage consultations, availability and earnings.",
                        color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroChip(Icons.Outlined.Bolt, "Live requests")
                HeroChip(Icons.Outlined.CurrencyRupee, "Weekly payouts")
                HeroChip(Icons.Outlined.AutoAwesome, "AI scan reports")
            }
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFFFFD93D), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
