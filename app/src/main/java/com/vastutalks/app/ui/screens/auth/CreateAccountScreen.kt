package com.vastutalks.app.ui.screens.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WorkspacePremium
import com.vastutalks.app.ui.components.CompassMark
import com.vastutalks.app.ui.components.DarkScreenStatusBar
import com.vastutalks.app.ui.components.NightHero
import com.vastutalks.app.ui.components.SecureNote
import com.vastutalks.app.ui.components.SquareIconButton
import com.vastutalks.app.ui.theme.CardBorder
import com.vastutalks.app.ui.theme.TextBody
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimarySoft
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Support
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.data.model.UserRole
import com.vastutalks.app.data.repository.ExpertDetails
import com.vastutalks.app.ui.components.GradientButton
import com.vastutalks.app.ui.components.ProfilePhotoEditor
import com.vastutalks.app.ui.components.VastuTextField
import com.vastutalks.app.ui.theme.AuthGradient
import com.vastutalks.app.ui.theme.VastuPrimary

@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onAccountCreated: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf(UserRole.NORMAL_USER) }
    var photo by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Expert-only: the details shown on their card and profile.
    var specialty by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    val uiState by authViewModel.uiState.collectAsState()

    var errorText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            errorText = uiState.errorMessage
            authViewModel.clearError()
        }
    }

    val isExpert = selectedRole == UserRole.VASTU_EXPERT
    val context = androidx.compose.ui.platform.LocalContext.current
    /** An expert's application went through: show "Application submitted". */
    var submitted by remember { mutableStateOf(false) }
    // Everything still missing, so the user knows why the button is disabled.
    val missing = buildList {
        if (isExpert && photo == null) add("profile photo")
        if (fullName.isBlank()) add("name")
        if (!email.contains('@')) add("email")
        if (mobile.filter(Char::isDigit).length < 10) add("10-digit mobile number")
        if (isExpert) {
            if (specialty.isBlank()) add("specialty")
            if (experience.toIntOrNull() == null) add("years of experience")
            if (city.isBlank()) add("city")
            if (bio.isBlank()) add("about you")
        }
        if (password.length < 6) add("password (6+ characters)")
        else if (password != confirmPassword) add("matching passwords")
        if (!agreedToTerms) add("agree to the Terms")
    }
    val canSubmit = missing.isEmpty()

    DarkScreenStatusBar()
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        NightHero(Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 42.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SquareIconButton(
                        Icons.Filled.ChevronLeft, "Back", onBack,
                        size = 36.dp, tint = Color.White,
                        background = Color.White.copy(alpha = 0.1f),
                        borderColor = Color.White.copy(alpha = 0.18f)
                    )
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CompassMark(15.dp)
                        Text("Vastu Talks", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
                    }
                    Box(Modifier.size(36.dp))
                }
                Text(
                    "Create your account",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)
                )
                Text(
                    if (isExpert) "Share your Vastu expertise and consult clients across India." else "Get AI Vastu insights and guidance from certified experts.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.5.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .offset(y = (-20).dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleCard(
                    title = "User",
                    sub = "Seek guidance",
                    icon = Icons.Outlined.Home,
                    selected = selectedRole == UserRole.NORMAL_USER,
                    modifier = Modifier.weight(1f)
                ) { selectedRole = UserRole.NORMAL_USER }
                RoleCard(
                    title = "Vastu Expert",
                    sub = "Offer consultations",
                    icon = Icons.Outlined.WorkspacePremium,
                    selected = selectedRole == UserRole.VASTU_EXPERT,
                    modifier = Modifier.weight(1f)
                ) { selectedRole = UserRole.VASTU_EXPERT }
            }

            // Experts need a photo for their card in the directory.
            if (isExpert) Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhotoEditor(
                    photo = "",
                    name = fullName,
                    size = 60.dp,
                    picked = photo,
                    onPicked = { photo = it }
                )
                Text(
                    when {
                        photo != null -> "Tap to change photo"
                        isExpert -> "Add your profile photo (required)"
                        else -> "Add a profile photo (optional)"
                    },
                    fontSize = 12.sp,
                    color = if (photo == null && isExpert) VastuPrimary else TextSecondary,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }

            VastuTextField(value = fullName, onValueChange = { fullName = it }, placeholder = "Full name", leadingIcon = Icons.Outlined.Person)
            VastuTextField(value = email, onValueChange = { email = it }, placeholder = "Email", leadingIcon = Icons.Outlined.Email, keyboardType = KeyboardType.Email)
            Box {
                VastuTextField(value = mobile, onValueChange = { mobile = it }, placeholder = "Mobile number", leadingIcon = Icons.Outlined.PhoneAndroid, keyboardType = KeyboardType.Phone)
                // WhatsApp verification isn't wired up yet; the button says so instead of pretending.
                Text(
                    "Verify",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 9.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF25D366).copy(alpha = if (mobile.filter(Char::isDigit).length >= 10) 1f else 0.45f))
                        .clickable {
                            android.widget.Toast.makeText(context, "WhatsApp verification is coming soon — your number is saved as entered.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
            VastuTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
            )
            Column {
                VastuTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = "Confirm password",
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    passwordVisible = confirmPasswordVisible,
                    onTogglePasswordVisibility = { confirmPasswordVisible = !confirmPasswordVisible }
                )
                if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                    Text("Passwords do not match", fontSize = 11.sp, color = Color(0xFFE53E3E), modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
            }

            if (isExpert) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("EXPERT DETAILS", color = VastuPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                    Box(Modifier.padding(start = 10.dp).weight(1f).height(1.dp).background(Color(0xFFEFEFF4)))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EXPERT_SPECIALTIES.forEach { (label, option) ->
                        val selected = specialty == option
                        Text(
                            label,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) VastuPrimary else TextBody,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) VastuPrimarySoft else Color.White)
                                .border(1.dp, if (selected) VastuPrimary else CardBorder, RoundedCornerShape(999.dp))
                                .clickable { specialty = option }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VastuTextField(
                        value = experience,
                        onValueChange = { experience = it.filter(Char::isDigit).take(2) },
                        placeholder = "Experience (yrs)",
                        leadingIcon = Icons.Outlined.WorkspacePremium,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    VastuTextField(
                        value = city,
                        onValueChange = { city = it },
                        placeholder = "City",
                        leadingIcon = Icons.Outlined.LocationOn,
                        modifier = Modifier.weight(1f)
                    )
                }
                VastuTextField(
                    value = bio,
                    onValueChange = { bio = it.take(400) },
                    placeholder = "About you: experience, approach and what you help clients with",
                    leadingIcon = Icons.Outlined.Info,
                    minLines = 3
                )
                Text("We verify every expert profile within 24 hours before it goes live.", fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (agreedToTerms) VastuPrimary else Color.White)
                        .border(1.5.dp, if (agreedToTerms) VastuPrimary else Color(0xFFCBD5E0), RoundedCornerShape(6.dp))
                        .clickable { agreedToTerms = !agreedToTerms },
                    contentAlignment = Alignment.Center
                ) {
                    if (agreedToTerms) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
                Text("I agree to the ", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.padding(start = 10.dp).clickable { agreedToTerms = !agreedToTerms })
                Text("Terms", fontSize = 11.5.sp, color = VastuPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenTerms() })
                Text(" and ", fontSize = 11.5.sp, color = TextSecondary)
                Text("Privacy Policy", fontSize = 11.5.sp, color = VastuPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenPrivacyPolicy() })
            }

            if (errorText != null) {
                Text(text = errorText ?: "", fontSize = 12.sp, color = Color(0xFFE53E3E))
            }

            GradientButton(
                text = if (isExpert) "Apply as Expert" else "Create Account",
                enabled = canSubmit,
                isLoading = uiState.isLoading,
                onClick = {
                    errorText = null
                    authViewModel.signUp(
                        fullName = fullName,
                        email = email,
                        password = password,
                        role = selectedRole,
                        phone = mobile,
                        photo = photo,
                        expert = if (isExpert) ExpertDetails(specialty, experience.toInt(), city.trim(), bio.trim()) else null,
                        onSuccess = { if (isExpert) submitted = true else onAccountCreated() }
                    )
                }
            )
            if (!canSubmit) {
                Text(
                    "Still needed: " + missing.joinToString(", "),
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Already have an account? ", color = TextSecondary, fontSize = 12.5.sp)
                Text("Sign in", color = VastuPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onBack() })
            }
            SecureNote(Modifier.padding(bottom = 16.dp))
        }
    }
    if (submitted) ApplicationSubmitted(onDone = {
        authViewModel.signOut()
        onBack()
    })
    }
}

/** The design's "Application submitted" dialog after an expert applies. */
@Composable
private fun ApplicationSubmitted(onDone: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDone)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x8C0F0B2E))
            .clickable(enabled = false) {}
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(68.dp).clip(CircleShape).background(Color(0xFFECFDF5)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF10B981)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Text("Application submitted", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 18.dp))
            Text(
                "Your account has been successfully created and submitted for review! Our team will connect with you within 24 working hours. Thank you for your patience!",
                fontSize = 12.5.sp, lineHeight = 20.sp, color = TextBody, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF7F5FF))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = VastuPrimary, modifier = Modifier.size(14.dp))
                Text(" Status: Under review", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VastuPrimary)
            }
            Box(
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VastuPrimary)
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center
            ) {
                Text("Back to Sign In", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The specialties an expert can pick at sign-up (shown on their card and profile). */
private val EXPERT_SPECIALTIES = listOf(
    "Residential" to "Residential Vastu",
    "Commercial" to "Commercial Vastu",
    "Industrial" to "Industrial Vastu",
    "Astrology" to "Astrology & Vastu",
    "Interior" to "Interior Vastu",
    "Plot & Land" to "Plot & Land Vastu",
)

@Composable
private fun RoleCard(
    title: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFF7F5FF) else Color.White)
            .border(1.5.dp, if (selected) VastuPrimary else CardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) VastuPrimary else Color(0xFFF3F0FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (selected) Color.White else VastuPrimary, modifier = Modifier.size(17.dp))
            }
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(sub, fontSize = 10.5.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) VastuPrimary else Color.White)
                .border(1.5.dp, if (selected) VastuPrimary else Color(0xFFCBD5E0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}
