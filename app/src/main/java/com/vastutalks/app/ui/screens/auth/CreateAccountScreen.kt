package com.vastutalks.app.ui.screens.auth

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Back", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Create Account",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp)
                )
                Text(
                    text = "Join Vastu Talks today",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp, bottom = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    FieldLabel("I am a...")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF3F1F7))
                            .padding(4.dp)
                    ) {
                        RoleTab(
                            label = "Normal User",
                            icon = Icons.Filled.Person,
                            selected = selectedRole == UserRole.NORMAL_USER,
                            modifier = Modifier.weight(1f)
                        ) { selectedRole = UserRole.NORMAL_USER }
                        RoleTab(
                            label = "Vastu Expert",
                            icon = Icons.Filled.Support,
                            selected = selectedRole == UserRole.VASTU_EXPERT,
                            modifier = Modifier.weight(1f)
                        ) { selectedRole = UserRole.VASTU_EXPERT }
                    }
                    if (selectedRole == UserRole.VASTU_EXPERT) {
                        Text(
                            text = "You'll be listed for normal users to call, and won't see the expert directory yourself.",
                            fontSize = 12.sp,
                            color = VastuPrimary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProfilePhotoEditor(
                            photo = "",
                            name = fullName,
                            size = 96.dp,
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
                            color = if (photo == null && isExpert) VastuPrimary else Color(0xFF6B6878),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    FieldLabel("Full Name", topPadding = 16.dp)
                    VastuTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        placeholder = "John Doe",
                        leadingIcon = Icons.Filled.Person
                    )

                    FieldLabel("Email", topPadding = 16.dp)
                    VastuTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "you@example.com",
                        leadingIcon = Icons.Filled.Email,
                        keyboardType = KeyboardType.Email
                    )

                    FieldLabel("Mobile Number", topPadding = 16.dp)
                    VastuTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        placeholder = "+91 98765 43210",
                        leadingIcon = Icons.Filled.Phone,
                        keyboardType = KeyboardType.Phone
                    )

                    if (isExpert) {
                        FieldLabel("Specialty", topPadding = 16.dp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EXPERT_SPECIALTIES.forEach { option ->
                                val selected = specialty == option
                                Text(
                                    option,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else Color(0xFF6B6878),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (selected) VastuPrimary else Color(0xFFF3F1F7))
                                        .clickable { specialty = option }
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                )
                            }
                        }

                        FieldLabel("Years of Experience", topPadding = 16.dp)
                        VastuTextField(
                            value = experience,
                            onValueChange = { experience = it.filter(Char::isDigit).take(2) },
                            placeholder = "e.g. 10",
                            leadingIcon = Icons.Filled.WorkspacePremium,
                            keyboardType = KeyboardType.Number
                        )

                        FieldLabel("City", topPadding = 16.dp)
                        VastuTextField(
                            value = city,
                            onValueChange = { city = it },
                            placeholder = "e.g. Mumbai",
                            leadingIcon = Icons.Filled.LocationOn
                        )

                        FieldLabel("About You", topPadding = 16.dp)
                        VastuTextField(
                            value = bio,
                            onValueChange = { bio = it.take(400) },
                            placeholder = "Your experience, approach and what you help clients with",
                            leadingIcon = Icons.Filled.Info,
                            minLines = 3
                        )
                    }

                    FieldLabel("Password", topPadding = 16.dp)
                    VastuTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Create password",
                        leadingIcon = Icons.Filled.Lock,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
                    )

                    FieldLabel("Confirm Password", topPadding = 16.dp)
                    VastuTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = "Re-enter password",
                        leadingIcon = Icons.Filled.Lock,
                        isPassword = true,
                        passwordVisible = confirmPasswordVisible,
                        onTogglePasswordVisibility = { confirmPasswordVisible = !confirmPasswordVisible }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 18.dp)
                    ) {
                        Checkbox(
                            checked = agreedToTerms,
                            onCheckedChange = { agreedToTerms = it },
                            colors = CheckboxDefaults.colors(checkedColor = VastuPrimary)
                        )
                        Row {
                            Text("I agree to the ", fontSize = 13.sp, color = Color(0xFF4A4A4A))
                            Text(
                                "Terms",
                                fontSize = 13.sp,
                                color = VastuPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onOpenTerms() }
                            )
                            Text(" and ", fontSize = 13.sp, color = Color(0xFF4A4A4A))
                            Text(
                                "Privacy Policy",
                                fontSize = 13.sp,
                                color = VastuPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onOpenPrivacyPolicy() }
                            )
                        }
                    }

                    if (errorText != null) {
                        Box(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorText ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFFD32F2F)
                        )
                    }

                    Box(modifier = Modifier.height(24.dp))

                    GradientButton(
                        text = "Create Account",
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
                                onSuccess = onAccountCreated
                            )
                        }
                    )
                    if (!canSubmit) {
                        Text(
                            "Still needed: " + missing.joinToString(", "),
                            fontSize = 12.sp,
                            color = Color(0xFF6B6878),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Box(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/** The specialties an expert can pick at sign-up (shown on their card and profile). */
private val EXPERT_SPECIALTIES = listOf(
    "Residential Vastu",
    "Commercial Vastu",
    "Industrial Vastu",
    "Interior Vastu",
    "Plot & Land Vastu",
    "Spiritual Consultation"
)

@Composable
private fun RoleTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) VastuPrimary else Color.Transparent)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else Color(0xFF6B6878),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF6B6878),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun FieldLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF2B2B2B),
        modifier = Modifier.padding(top = topPadding, bottom = 8.dp)
    )
}
