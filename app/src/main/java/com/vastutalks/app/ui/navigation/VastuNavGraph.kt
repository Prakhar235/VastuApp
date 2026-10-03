package com.vastutalks.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.model.CallHistoryEntry
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.data.repository.CallHistoryRepository
import com.vastutalks.app.data.repository.UserRepository
import com.vastutalks.app.ui.screens.auth.CreateAccountScreen
import com.vastutalks.app.ui.screens.auth.SignInScreen
import com.vastutalks.app.ui.screens.call.DemoCallingScreen
import com.vastutalks.app.ui.screens.call.DemoInCallScreen
import com.vastutalks.app.ui.screens.call.LiveCallingScreen
import com.vastutalks.app.ui.screens.call.LiveInCallScreen
import com.vastutalks.app.ui.screens.home.HomeScreen
import com.vastutalks.app.ui.screens.profile.ProfileScreen
import com.vastutalks.app.ui.screens.splash.SplashScreen
import kotlinx.coroutines.launch
import java.net.URLDecoder

/** Set by MainActivity when launched right after IncomingCallActivity accepted+joined a call. */
data class CallDeepLink(val channelName: String, val peerName: String, val callType: String)

@Composable
fun VastuNavGraph(initialCallDeepLink: CallDeepLink? = null) {
    val navController = rememberNavController()

    // Holds duration/cost from the most recently ended call so the
    // summary screen can show a receipt. A small in-memory holder is
    // enough for this prototype; swap for a ViewModel-backed repository
    // when wiring real billing.
    // Lives as long as the NavHost itself (not tied to any one screen's
    // back-stack entry), so logging a finished call to Firestore isn't
    // cut short by that screen leaving composition right after this runs.
    val navScope = rememberCoroutineScope()
    val callHistoryRepository = remember { CallHistoryRepository() }
    val userRepository = remember { UserRepository() }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.SPLASH) {

            composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                if (initialCallDeepLink != null) {
                    // Already accepted + joined via IncomingCallActivity — go straight to the call.
                    navController.navigate(
                        Routes.liveInCall(initialCallDeepLink.channelName, initialCallDeepLink.peerName, initialCallDeepLink.callType)
                    ) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                    return@SplashScreen
                }
                // Firebase persists sessions on-device, so a signed-in user
                // skips Sign In and lands straight on Home. A signed-out
                // user who hasn't seen onboarding yet sees it once first.
                val prefs = context.getSharedPreferences("vastu_prefs", android.content.Context.MODE_PRIVATE)
                val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)
                val destination = when {
                    FirebaseAuth.getInstance().currentUser != null -> Routes.HOME
                    !hasSeenOnboarding -> Routes.ONBOARDING
                    else -> Routes.SIGN_IN
                }
                navController.navigate(destination) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }

        composable(Routes.ONBOARDING) {
            com.vastutalks.app.ui.screens.onboarding.OnboardingScreen(onDone = {
                context.getSharedPreferences("vastu_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_seen_onboarding", true)
                    .apply()
                navController.navigate(Routes.SIGN_IN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }

        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignInSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                },
                onNavigateToCreateAccount = { navController.navigate(Routes.CREATE_ACCOUNT) },
                onForgotPassword = { /* TODO: wire forgot-password flow */ }
            )
        }

        composable(Routes.CREATE_ACCOUNT) {
            CreateAccountScreen(
                onBack = { navController.popBackStack() },
                onAccountCreated = {
                    // Firebase's createUserWithEmailAndPassword already confirms
                    // the account exists, so there's no phone/email OTP step to
                    // wait on here — go straight to Home. OTP_VERIFY is left in
                    // Routes/nav graph for when phone verification gets wired up.
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                },
                onOpenTerms = { /* TODO: open terms screen / web view */ },
                onOpenPrivacyPolicy = { /* TODO: open privacy policy screen / web view */ }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onSearchClick = { /* TODO: dedicated search screen */ },
                onNotificationsClick = { /* TODO: notifications screen */ },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                onCallExpert = { expertId, expertName, callType ->
                    navController.navigate(Routes.liveCalling(expertId, expertName, callType.name))
                },
                onAcceptIncomingCall = { channelName, callType, peerName ->
                    navController.navigate(Routes.liveInCall(channelName, peerName, callType.name)) {
                        launchSingleTop = true
                    }
                },
                onExpertCardClick = { expertUid, expertName ->
                    navController.navigate(Routes.realExpertProfile(expertUid, expertName))
                },
                onTryDemoCall = { navController.navigate(Routes.DEMO_CALLING) }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSignOut = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.REAL_EXPERT_PROFILE,
            arguments = listOf(
                navArgument("expertUid") { type = NavType.StringType },
                navArgument("expertName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val expertUid = URLDecoder.decode(backStackEntry.arguments?.getString("expertUid") ?: "", "UTF-8")
            val expertName = URLDecoder.decode(backStackEntry.arguments?.getString("expertName") ?: "", "UTF-8")
            com.vastutalks.app.ui.screens.expert.RealExpertProfileScreen(
                expertUid = expertUid,
                expertName = expertName,
                onBack = { navController.popBackStack() },
                onChatNow = { /* TODO: no chat feature yet — see README */ },
                onVideoCall = {
                    navController.navigate(Routes.liveCalling(expertUid, expertName, CallType.VIDEO.name))
                }
            )
        }

        composable(Routes.DEMO_CALLING) {
            DemoCallingScreen(
                onConnected = {
                    navController.navigate(Routes.DEMO_IN_CALL) {
                        popUpTo(Routes.DEMO_CALLING) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.DEMO_IN_CALL) {
            DemoInCallScreen(
                onEndCall = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.LIVE_CALLING,
            arguments = listOf(
                navArgument("calleeUid") { type = NavType.StringType },
                navArgument("calleeName") { type = NavType.StringType },
                navArgument("callType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val calleeUid = URLDecoder.decode(backStackEntry.arguments?.getString("calleeUid") ?: "", "UTF-8")
            val calleeName = URLDecoder.decode(backStackEntry.arguments?.getString("calleeName") ?: "", "UTF-8")
            val callType = CallType.valueOf(backStackEntry.arguments?.getString("callType") ?: CallType.AUDIO.name)
            LiveCallingScreen(
                calleeUid = calleeUid,
                calleeName = calleeName,
                callType = callType,
                onConnected = { channelName, peerName ->
                    navController.navigate(Routes.liveInCall(channelName, peerName, callType.name)) {
                        popUpTo(Routes.LIVE_CALLING) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.LIVE_IN_CALL,
            arguments = listOf(
                navArgument("channelName") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType },
                navArgument("callType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val channelName = URLDecoder.decode(backStackEntry.arguments?.getString("channelName") ?: "", "UTF-8")
            val peerName = URLDecoder.decode(backStackEntry.arguments?.getString("peerName") ?: "", "UTF-8")
            val callType = CallType.valueOf(backStackEntry.arguments?.getString("callType") ?: CallType.AUDIO.name)
            LiveInCallScreen(
                channelName = channelName,
                peerName = peerName,
                callType = callType,
                onEndCall = { durationSeconds, startTimeMillis ->
                    // channelName is "expert-<expertUid>" (see CallSignalingRepository),
                    // so this recovers the expert's uid without needing another nav param.
                    val otherPartyId = channelName.removePrefix("expert-")
                    val myUid = FirebaseAuth.getInstance().currentUser?.uid
                    navScope.launch {
                        callHistoryRepository.logCall(
                            CallHistoryEntry(
                                expertId = otherPartyId,
                                expertName = peerName,
                                callType = callType.name,
                                startTimeMillis = startTimeMillis,
                                durationSeconds = durationSeconds,
                                cost = 0, // real experts don't have per-minute pricing set up yet
                                channelName = channelName
                            )
                        )
                        // Only bump the counter from the expert's own device (i.e. when
                        // this device's uid IS the expert whose channel this was) — the
                        // caller's device also runs this handler, and we don't want to
                        // double-count one call as two consultations.
                        if (myUid != null && myUid == otherPartyId) {
                            userRepository.incrementConsultationCount(otherPartyId)
                        }
                    }
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

    }
    }
}
