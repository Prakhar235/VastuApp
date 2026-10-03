package com.vastutalks.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.ui.theme.OnboardingGreenEnd
import com.vastutalks.app.ui.theme.OnboardingGreenStart
import com.vastutalks.app.ui.theme.OnboardingPinkEnd
import com.vastutalks.app.ui.theme.OnboardingPinkStart
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuPrimaryDark
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val icon: ImageVector,
    val gradient: Brush,
    val badge: String,
    val title: String,
    val subtitle: String
)

private val slides = listOf(
    OnboardingSlide(
        icon = Icons.Filled.Home,
        gradient = Brush.linearGradient(listOf(VastuPrimary, VastuPrimaryDark)),
        badge = "Your spiritual guide",
        title = "Welcome to Vastu Talks",
        subtitle = "Connect with certified Vastu experts for personalized guidance on harmonizing your spaces."
    ),
    OnboardingSlide(
        icon = Icons.Filled.Chat,
        gradient = Brush.linearGradient(listOf(OnboardingPinkStart, OnboardingPinkEnd)),
        badge = "Anytime, Anywhere",
        title = "Expert Guidance",
        subtitle = "Chat or video call with experienced consultants who understand your needs."
    ),
    OnboardingSlide(
        icon = Icons.Filled.AutoAwesome,
        gradient = Brush.linearGradient(listOf(OnboardingGreenStart, OnboardingGreenEnd)),
        badge = "Positive Energy",
        title = "Transform Your Space",
        subtitle = "Receive customized recommendations to enhance prosperity and well-being."
    )
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
    ) {
        Text(
            "Skip",
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(20.dp)
                .clickable { onDone() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(slide.gradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(slide.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }

                    Box(modifier = Modifier.height(28.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(slide.badge, color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Box(modifier = Modifier.height(20.dp))

                    Text(
                        slide.title,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2B2B2B),
                        textAlign = TextAlign.Center
                    )

                    Box(modifier = Modifier.height(14.dp))

                    Text(
                        slide.subtitle,
                        fontSize = 15.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                slides.indices.forEach { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (isSelected) 24.dp else 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) VastuPrimary else TextSecondary.copy(alpha = 0.25f))
                    )
                }
            }

            Box(modifier = Modifier.height(24.dp))

            val isLastSlide = pagerState.currentPage == slides.lastIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(VastuPrimary)
                    .clickable {
                        if (isLastSlide) {
                            onDone()
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isLastSlide) "Get Started" else "Continue",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}
