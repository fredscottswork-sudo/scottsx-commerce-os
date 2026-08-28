package com.scottsx.app.navigation

import java.net.URLDecoder
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scottsx.app.data.AuthRepository
import com.scottsx.app.data.GoogleSignInHelper
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.Session
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.SessionCache
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.screens.AiAssistantScreen
import com.scottsx.app.ui.screens.RealAiChatScreen
import com.scottsx.app.ui.screens.AiPersonalizationScreen
import com.scottsx.app.ui.screens.BuyerHomeScreen
import com.scottsx.app.ui.screens.CartScreen
import com.scottsx.app.ui.screens.CategoriesScreen
import com.scottsx.app.ui.screens.DisputeScreen
import com.scottsx.app.ui.screens.HomeScreen
import com.scottsx.app.ui.screens.LoginScreen
import com.scottsx.app.ui.screens.NearbyMapScreen
import com.scottsx.app.ui.screens.NearbyScreen
import com.scottsx.app.ui.screens.OnboardingFlow
import com.scottsx.app.ui.screens.ProfileScreen
import com.scottsx.app.ui.screens.ReceiptDesignerScreen
import com.scottsx.app.ui.screens.ReceiptPreviewScreen
import com.scottsx.app.ui.screens.ReceiptsHistoryScreen
import com.scottsx.app.ui.screens.TransactionDetailScreen
import com.scottsx.app.ui.screens.TransactionsListScreen
import com.scottsx.app.ui.screens.RoleSelectionScreen
import com.scottsx.app.ui.screens.SearchScreen
import com.scottsx.app.ui.screens.AddProductScreen
import com.scottsx.app.ui.screens.MarketplaceToolsScreen
import com.scottsx.app.ui.screens.MessageThreadScreen
import com.scottsx.app.ui.screens.ProductDetailScreen
import com.scottsx.app.ui.screens.ProfileSettingsScreen
import com.scottsx.app.ui.screens.ReviewsScreen
import com.scottsx.app.ui.screens.SellerAnalyticsScreen
import com.scottsx.app.ui.screens.SellerAIAssistantScreen
import com.scottsx.app.ui.screens.SellerHomeScreen
import com.scottsx.app.ui.screens.SellerMessagesScreen
import com.scottsx.app.ui.screens.ThemeScreen
import com.scottsx.app.ui.screens.StoreSettingsDetailScreen
import com.scottsx.app.ui.screens.SellerOrdersScreen
import com.scottsx.app.ui.screens.SellerStorefrontScreen
import com.scottsx.app.ui.screens.SignUpScreen
import com.scottsx.app.ui.screens.SplashScreen
import com.scottsx.app.ui.screens.MessagesScreen
import com.scottsx.app.ui.screens.AccountSettingsScreen
import com.scottsx.app.ui.screens.AddressesScreen
import com.scottsx.app.ui.screens.PaymentMethodsScreen
import com.scottsx.app.ui.screens.CmsScreen
import com.scottsx.app.ui.screens.ContactScreen
import com.scottsx.app.ui.screens.ReportProblemScreen
import com.scottsx.app.ui.screens.HelpCenterScreen
import com.scottsx.app.ui.screens.NotificationSettingsScreen
import com.scottsx.app.ui.screens.LanguageScreen
import com.scottsx.app.ui.screens.CurrencyScreen
import com.scottsx.app.ui.screens.AuditScreen
import com.scottsx.app.ui.screens.SecurityScreen
import com.scottsx.app.ui.screens.DeleteAccountScreen
import com.scottsx.app.ui.screens.MyOrdersScreen
import com.scottsx.app.ui.screens.TrackOrderScreen
import com.scottsx.app.ui.screens.RefundsScreen
import com.scottsx.app.ui.screens.ReturnsScreen
import com.scottsx.app.ui.screens.SavedProductsScreen
import com.scottsx.app.ui.screens.SavedSellersScreen
import com.scottsx.app.ui.screens.NotificationsScreen
import com.scottsx.app.ui.screens.BecomeSellerScreen
import com.scottsx.app.ui.screens.StoreSettingsScreen
import com.scottsx.app.ui.screens.WishlistScreen
import com.scottsx.app.ui.screens.WrongRoleScreen
import com.scottsx.app.ui.screens.SettingsScreen
import com.scottsx.app.ui.screens.VerifyEmailPendingScreen
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * Stage-3 navigation host.
 *
 * Routes the entire app — Splash → Onboarding → RoleSelection →
 * Login/SignUp → role-specific dashboard (Buyer or Seller) →
 * tabbed sub-screens (Nearby, AI, Wishlist, Profile) and detail
 * screens (Cart, Search, Categories).
 *
 * Three new edge-case routes over Stage 2:
 *  - `wrongRole/{picked}/{actual}` — bounces a buyer/seller mismatch
 *    to a screen that lets them pick the right dashboard or pick a
 *    different account.
 *  - `seller_home/{displayName}/{email}` — counterpart to BUYER_HOME.
 *  - `home/{role}` — legacy placeholder retained for the HomeScreen
 *    pick-up that runs *before* the buyer dashboard kicks in.
 *
 * Sign-out is centralized in [Session.signOut] which clears Firebase
 * Auth, the Google SDK cache, and the local SessionCache. Every
 * dashboard-level sign-out button routes through that helper so we
 * cannot accidentally skip any of the three subsystems.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val authRepository = remember { AuthRepository(auth) }
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            BackHandler { /* splash blocks back */ }
            SplashHost(onContinue = {
                navController.navigate(Routes.ONBOARDING) {
                    launchSingleTop = true
                }
            })
        }

        composable(Routes.ONBOARDING) {
            BackHandler { /* onboarding blocks back */ }
            OnboardingFlow(onFinish = {
                navController.navigate(Routes.ROLE) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }

        composable(Routes.ROLE) {
            RoleSelectionScreen(
                onLogin = { role: Role ->
                    navController.navigate(Routes.login(role)) {
                        launchSingleTop = true
                    }
                },
                onSignUp = { role: Role ->
                    navController.navigate(Routes.signup(role)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.LOGIN,
            arguments = listOf(navArgument("role") { type = NavType.StringType }),
        ) { backStackEntry ->
            val role = Routes.roleFromBackStack(backStackEntry.arguments?.getString("role"))
            LoginScreen(
                role = role,
                authRepository = authRepository,
                onBack = { navController.popBackStack() },
                onLogin = { signedRole: Role ->
                    navController.navigate(Routes.dashboard(signedRole)) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onGoogle = { signedRole: Role ->
                    navController.navigate(Routes.dashboard(signedRole)) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onApple = { /* Stage 3 — Apple Sign-In (TBD) */ },
                onSignUp = {
                    navController.navigate(Routes.signup(role)) {
                        launchSingleTop = true
                    }
                },
                onForgotPassword = { /* Stage 3 — reset link email (TBD) */ },
                onRoleMismatch = { actualRole ->
                    navController.navigate(Routes.wrongRole(picked = role, actual = actualRole)) {
                        launchSingleTop = true
                    }
                },
                onVerificationPending = { email ->
                    navController.navigate(Routes.verifyEmailPending(email)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.SIGNUP,
            arguments = listOf(navArgument("role") { type = NavType.StringType }),
        ) { backStackEntry ->
            val role = Routes.roleFromBackStack(backStackEntry.arguments?.getString("role"))
            SignUpScreen(
                role = role,
                authRepository = authRepository,
                onBack = { navController.popBackStack() },
                onSubmit = { signedRole: Role ->
                    navController.navigate(Routes.dashboard(signedRole)) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSignInInstead = { navController.popBackStack() },
                onRoleMismatch = { actualRole ->
                    navController.navigate(Routes.wrongRole(picked = role, actual = actualRole)) {
                        launchSingleTop = true
                    }
                },
                onVerificationPending = { email ->
                    navController.navigate(Routes.verifyEmailPending(email)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.VERIFY_EMAIL_PENDING,
            arguments = listOf(navArgument("email") { type = NavType.StringType }),
        ) { backStackEntry ->
            val email = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("email") ?: "",
                "UTF-8",
            )
            VerifyEmailPendingScreen(
                email = email,
                onBack = { navController.popBackStack() },
                onVerified = {
                    // After verification, route to the role dashboard.
                    // We have to read the role back from SessionCache
                    // because the pending screen doesn't know it.
                    val verifiedRole = SessionCache.role ?: Role.BUYER
                    navController.navigate(Routes.dashboard(verifiedRole)) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onUseDifferentEmail = {
                    navController.popBackStack()
                    navController.navigate(Routes.ROLE) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.WRONG_ROLE,
            arguments = listOf(
                navArgument("picked") { type = NavType.StringType },
                navArgument("actual") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val picked = Routes.roleFromBackStack(
                backStackEntry.arguments?.getString("picked"),
            )
            val actual = Routes.roleFromBackStack(
                backStackEntry.arguments?.getString("actual"),
            )
            WrongRoleScreen(
                pickedRole = picked,
                actualRole = actual,
                onContinueAsActual = {
                    navController.navigate(Routes.dashboard(actual)) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onUseDifferentAccount = {
                    scope.launch {
                        Session.signOut(authRepository, null /* helper per-screen is reset on next login */)
                    }
                    navController.navigate(Routes.ROLE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.HOME,
            arguments = listOf(navArgument("role") { type = NavType.StringType }),
        ) { backStackEntry ->
            val role = Routes.roleFromBackStack(backStackEntry.arguments?.getString("role"))
            HomeScreen(
                role = role,
                onContinue = { userProfile ->
                    SessionCache.role = role
                    SessionCache.displayName = userProfile.displayName
                    SessionCache.email = userProfile.email
                    navController.navigate(Routes.dashboard(role)) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSignOut = {
                    scope.launch {
                        Session.signOut(authRepository, null /* helper per-screen is reset on next login */)
                    }
                    navController.navigate(Routes.ROLE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.BUYER_HOME,
            arguments = listOf(
                navArgument("displayName") { type = NavType.StringType; defaultValue = "Buyer" },
                navArgument("email") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val displayName = backStackEntry.arguments?.getString("displayName")
            val email = backStackEntry.arguments?.getString("email")
            // Real profile from the session — cart/notification counts
            // are fetched live by the dashboard from the backend; no
            // demo numbers are seeded here.
            val profile = com.scottsx.app.data.domain.BuyerProfile(
                uid = SessionCache.userId ?: "u-self",
                displayName = displayName?.ifBlank { "Buyer" } ?: "Buyer",
                email = email ?: "",
                avatarUrl = SessionCache.avatarUrl,
            )
            // Role-separation gate. If the signed-in session says Seller
            // but the buyer dashboard got requested, bounce to the
            // wrong-role screen so we never show the wrong UI.
            val sessionRole = SessionCache.role
            if (sessionRole == Role.SELLER) {
                LaunchedEffect(Unit) {
                    navController.navigate(
                        Routes.wrongRole(picked = Role.BUYER, actual = Role.SELLER),
                    ) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                return@composable
            }
            BuyerHomeScreen(
                profile = profile,
                onNavigateToCart = { navController.navigate(Routes.CART) },
                onNavigateToCategory = { navController.navigate(Routes.CATEGORIES) },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToNearby = { navController.navigate(Routes.NEARBY) },
                onNavigateToAi = { navController.navigate(Routes.AI) },
                onNavigateToAllProducts = { navController.navigate(Routes.CATEGORIES) },
                onNavigateToTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onNavigateToReceipts = { navController.navigate(Routes.RECEIPTS_HISTORY) },
                onNavigateToAiPersonalization = { navController.navigate(Routes.AI_PERSONALIZATION) },
                onNavigateToMessages = { navController.navigate(Routes.MESSAGES) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToSellerCenter = {
                    navController.navigate(Routes.SELLER_HOME +
                        "/${java.net.URLEncoder.encode(profile.displayName.ifBlank { "Seller" }, "UTF-8")}/" +
                        "${java.net.URLEncoder.encode(profile.email.ifBlank { "seller" }, "UTF-8")}")
                },
                onNavigateToBecomeSeller = { navController.navigate(Routes.BECOME_SELLER) },
                onOpenProduct = { p: com.scottsx.app.data.domain.Product -> navController.navigate(Routes.product(p.id)) },
                onOpenStore = { sid -> navController.navigate(Routes.storefront(sid)) },
                onTabSelect = { tab: BottomTab -> onBuyerTab(navController, tab) },
                // Stage 3.1 sidebar sign-out: same single sign-out helper
                // we use everywhere; clear Firebase + Google SDK +
                // SessionCache, then bounce back to the role picker.
                onSignOutRequested = {
                    scope.launch {
                        Session.signOut(authRepository, null)
                    }
                    navController.navigate(Routes.ROLE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.SELLER_HOME,
            arguments = listOf(
                navArgument("displayName") { type = NavType.StringType; defaultValue = "Seller" },
                navArgument("email") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val displayName = backStackEntry.arguments?.getString("displayName") ?: "Seller"
            val email = backStackEntry.arguments?.getString("email") ?: ""
            // Role-separation gate.
            val sessionRole = SessionCache.role
            if (sessionRole == Role.BUYER) {
                LaunchedEffect(Unit) {
                    navController.navigate(
                        Routes.wrongRole(picked = Role.SELLER, actual = Role.BUYER),
                    ) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                return@composable
            }
            SellerHomeScreen(
                displayName = displayName,
                email = email,
                onAddProduct = { navController.navigate(Routes.SELLER_ADD_PRODUCT) },
                onManageOrders = { navController.navigate(Routes.SELLER_ORDERS) },
                onOpenInventory = { navController.navigate(Routes.SELLER_ORDERS) },
                onOpenAnalytics = { navController.navigate(Routes.SELLER_ANALYTICS) },
                onOpenMarketplaceTools = { navController.navigate(Routes.SELLER_TOOLS) },
                onOpenStoreSettings = { navController.navigate(Routes.SELLER_STORE_SETTINGS) },
                onOpenProfileSettings = { navController.navigate(Routes.SELLER_PROFILE_SETTINGS) },
                onOpenMessages = { navController.navigate(Routes.SELLER_MESSAGES) },
                onOpenProduct = { product -> navController.navigate(Routes.product(product.id)) },
                onNavigateToTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onNavigateToReceipts = { navController.navigate(Routes.RECEIPTS_HISTORY) },
                onCreateReceipt = { navController.navigate(Routes.RECEIPT_NEW) },
                onNavigateToAiPersonalization = { navController.navigate(Routes.AI_PERSONALIZATION) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onSwitchToBuyer = {
                    // Switch to buyer app: keep the same Firebase session,
                    // just flip the role expectation so the buyer dashboard
                    // accepts the seller account too.
                    navController.navigate(Routes.ROLE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSignOut = {
                    scope.launch {
                        Session.signOut(authRepository, null)
                    }
                    navController.navigate(Routes.ROLE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                // Stage 5.x: seller bottom nav swap. "AI" navigates to the
                // shared AiAssistantScreen (same one the buyer uses); the
                // header is the same — the seller gets a Seller AI tab on
                // the AI assistant screen via header context.
                onOpenSellerAi = { navController.navigate(Routes.SELLER_AI) },
                onOpenSellerMessages = { navController.navigate(Routes.SELLER_MESSAGES) },
            )
        }

        composable(Routes.CART) {
            CartScreen(
                onBack = { navController.popBackStack() },
                onTabSelect = { tab: BottomTab -> onBuyerTab(navController, tab) },
            )
        }
        composable(Routes.NEARBY) {
            NearbyScreen(
                onBack = { navController.popBackStack() },
                onTabSelect = { tab: BottomTab -> onBuyerTab(navController, tab) },
            )
        }
        composable(Routes.AI) {
            RealAiChatScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { p: com.scottsx.app.data.domain.Product -> navController.navigate(Routes.product(p.id)) },
                onTabSelect = { tab: BottomTab -> onBuyerTab(navController, tab) },
            )
        }
        composable(Routes.SELLER_AI) {
            SellerAIAssistantScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { p: com.scottsx.app.data.domain.Product -> navController.navigate(Routes.product(p.id)) },
                onTabSelect = { tab: BottomTab -> onBuyerTab(navController, tab) },
            )
        }
        composable(Routes.CATEGORIES) {
            CategoriesScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { p: com.scottsx.app.data.domain.Product -> navController.navigate(Routes.product(p.id)) },
                onTabSelect = { tab: BottomTab -> onBuyerTab(navController, tab) },
            )
        }
        composable(Routes.WISHLIST) {
            WishlistScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { p: com.scottsx.app.data.domain.Product -> navController.navigate(Routes.product(p.id)) },
                onTabSelect = { tab: BottomTab -> onBuyerTab(navController, tab) },
            )
        }
        composable(
            Routes.PROFILE,
            arguments = listOf(
                navArgument("displayName") { type = NavType.StringType; defaultValue = "Buyer" },
                navArgument("email") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val displayName = backStackEntry.arguments?.getString("displayName")
            val email = backStackEntry.arguments?.getString("email")
            val profile = MarketplaceDataSource.profileFor(displayName, email)
            val activityContext = androidx.compose.ui.platform.LocalContext.current
            ProfileScreen(
                profile = profile,
                onBack = { navController.popBackStack() },
                onTabSelect = { tab: BottomTab -> onBuyerTab(navController, tab) },
                onSignOut = {
                    scope.launch {
                        Session.signOut(authRepository, null)
                    }
                    navController.navigate(Routes.ROLE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSwitchAccount = {
                    try {
                        (activityContext as? android.app.Activity)?.let { activity ->
                            com.scottsx.app.data.GoogleSignInHelper(activity)
                                .forcePickerOnNextSignIn()
                        }
                    } catch (t: Throwable) {
                        android.util.Log.w("AppNavigation", "switchAccount: force-picker failed", t)
                    }
                    scope.launch { Session.signOut(authRepository, null) }
                    navController.navigate(Routes.ROLE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onEditProfile = { navController.navigate(Routes.ACCOUNT) },
                onOpenSection = { section ->
                    when (section) {
                        "account" -> navController.navigate(Routes.ACCOUNT)
                        "addresses" -> navController.navigate(Routes.ADDRESSES)
                        "saved-locations" -> navController.navigate(Routes.ADDRESSES)
                        "payments" -> navController.navigate(Routes.PAYMENT_METHODS)
                        "orders" -> navController.navigate(Routes.MY_ORDERS)
                        "track-orders" -> navController.navigate(Routes.MY_ORDERS)
                        "refunds" -> navController.navigate(Routes.REFUNDS)
                        "saved-products" -> navController.navigate(Routes.SAVED_PRODUCTS)
                        "saved-sellers" -> navController.navigate(Routes.SAVED_SELLERS)
                        "nearby" -> navController.navigate(Routes.NEARBY)
                        "ai" -> navController.navigate(Routes.AI)
                        "notifications" -> navController.navigate(Routes.NOTIFICATIONS)
                        "messages" -> navController.navigate(Routes.MESSAGES)
                        "buyer-protection" -> navController.navigate(Routes.cmsPath("buyer-protection"))
                        "theme" -> navController.navigate(Routes.THEME)
                        "language" -> navController.navigate(Routes.LANGUAGE)
                        "currency" -> navController.navigate(Routes.CURRENCY)
                        "help" -> navController.navigate(Routes.HELP)
                        "contact" -> navController.navigate(Routes.CONTACT)
                        "report" -> navController.navigate(Routes.REPORT)
                        "terms" -> navController.navigate(Routes.cmsPath("terms"))
                        "privacy-policy" -> navController.navigate(Routes.cmsPath("privacy"))
                        else -> Unit
                    }
                },
            )
        }
        // =====================================================================
        // Stage 3 — Product discovery + seller storefront + messaging
        // =====================================================================

        composable(
            Routes.PRODUCT,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId").orEmpty()
            ProductDetailScreen(
                productId = productId,
                onBack = { navController.popBackStack() },
                onViewSeller = { sid -> navController.navigate(Routes.storefront(sid)) },
                onMessageSeller = { sid, pid -> navController.navigate(Routes.thread(sid, pid)) },
                onViewAllReviews = { pid -> navController.navigate(Routes.reviews(pid)) },
                onOpenCart = { navController.navigate(Routes.CART) },
                onOpenNearby = { pid -> navController.navigate(Routes.NEARBY) },
                onOpenAi = { navController.navigate(Routes.AI) },
                onOpenProduct = { pid -> navController.navigate(Routes.product(pid)) },
            )
        }

        composable(
            Routes.STOREFRONT,
            arguments = listOf(navArgument("sellerId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sellerId = backStackEntry.arguments?.getString("sellerId").orEmpty()
            SellerStorefrontScreen(
                sellerId = sellerId,
                onBack = { navController.popBackStack() },
                onOpenProduct = { pid -> navController.navigate(Routes.product(pid)) },
                onMessageSeller = { sid, pid -> navController.navigate(Routes.thread(sid, pid)) },
                onViewAllReviews = { pid -> navController.navigate(Routes.reviews(pid)) },
            )
        }

        composable(
            Routes.REVIEWS,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val pid = backStackEntry.arguments?.getString("productId").orEmpty()
            ReviewsScreen(productId = pid, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.THREAD,
            arguments = listOf(
                navArgument("sellerId") { type = NavType.StringType },
                navArgument("productId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val sid = backStackEntry.arguments?.getString("sellerId").orEmpty()
            val pid = backStackEntry.arguments?.getString("productId").orEmpty().ifBlank { null }
            MessageThreadScreen(
                sellerId = sid,
                productId = pid,
                onBack = { navController.popBackStack() },
                onOpenProduct = { id -> navController.navigate(Routes.product(id)) },
                onViewStore = { id -> navController.navigate(Routes.storefront(id)) },
            )
        }

        // =====================================================================
        // Stage 3 — Seller side screens
        // =====================================================================

        composable(Routes.SELLER_ORDERS) {
            SellerOrdersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SELLER_ADD_PRODUCT) {
            AddProductScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Routes.SELLER_MESSAGES) {
            SellerMessagesScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { conversationId, peerName -> navController.navigate(Routes.thread("tech-hub", null)) },
            )
        }
        composable(Routes.SELLER_ANALYTICS) {
            SellerAnalyticsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SELLER_TOOLS) {
            MarketplaceToolsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SELLER_STORE_SETTINGS) {
            StoreSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSection = { section ->
                    navController.navigate("seller/store-settings/$section")
                },
            )
        }
        composable(
            Routes.SELLER_STORE_SETTING_DETAIL,
            arguments = listOf(navArgument("section") { type = NavType.StringType }),
        ) { backStack ->
            val section = backStack.arguments?.getString("section") ?: "store-profile"
            StoreSettingsDetailScreen(section = section, onBack = { navController.popBackStack() })
        }
        composable(Routes.SELLER_PROFILE_SETTINGS) {
            ProfileSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSection = { section ->
                    when (section) {
                        "account" -> navController.navigate(Routes.ACCOUNT)
                        "security" -> navController.navigate(Routes.SECURITY)
                        "addresses" -> navController.navigate(Routes.ADDRESSES)
                        "payments" -> navController.navigate(Routes.PAYMENT_METHODS)
                        "notifications" -> navController.navigate(Routes.NOTIFICATION_PREFS)
                        "privacy" -> navController.navigate(Routes.PRIVACY)
                        "language" -> navController.navigate(Routes.LANGUAGE)
                        "currency" -> navController.navigate(Routes.CURRENCY)
                        "theme" -> navController.navigate(Routes.THEME)
                        "orders" -> navController.navigate(Routes.MY_ORDERS)
                        "saved-products" -> navController.navigate(Routes.SAVED_PRODUCTS)
                        "saved-sellers" -> navController.navigate(Routes.SAVED_SELLERS)
                        "buyer-protection" -> navController.navigate(Routes.cmsPath("buyer-protection"))
                        "help" -> navController.navigate(Routes.HELP)
                        "contact" -> navController.navigate(Routes.CONTACT)
                        "report" -> navController.navigate(Routes.REPORT)
                        "terms" -> navController.navigate(Routes.cmsPath("terms"))
                        "privacy-policy" -> navController.navigate(Routes.cmsPath("privacy"))
                        "about" -> navController.navigate(Routes.cmsPath("about"))
                        "audit" -> navController.navigate(Routes.AUDIT)
                        "delete-account" -> navController.navigate(Routes.DELETE_ACCOUNT)
                        else -> Unit
                    }
                },
            )
        }
        composable(Routes.BUYER_PROFILE_SETTINGS) {
            ProfileSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSection = { section ->
                    when (section) {
                        "account" -> navController.navigate(Routes.ACCOUNT)
                        "security" -> navController.navigate(Routes.SECURITY)
                        "addresses" -> navController.navigate(Routes.ADDRESSES)
                        "payments" -> navController.navigate(Routes.PAYMENT_METHODS)
                        "notifications" -> navController.navigate(Routes.NOTIFICATION_PREFS)
                        "privacy" -> navController.navigate(Routes.PRIVACY)
                        "language" -> navController.navigate(Routes.LANGUAGE)
                        "currency" -> navController.navigate(Routes.CURRENCY)
                        "theme" -> navController.navigate(Routes.THEME)
                        "orders" -> navController.navigate(Routes.MY_ORDERS)
                        "saved-products" -> navController.navigate(Routes.SAVED_PRODUCTS)
                        "saved-sellers" -> navController.navigate(Routes.SAVED_SELLERS)
                        "buyer-protection" -> navController.navigate(Routes.cmsPath("buyer-protection"))
                        "help" -> navController.navigate(Routes.HELP)
                        "contact" -> navController.navigate(Routes.CONTACT)
                        "report" -> navController.navigate(Routes.REPORT)
                        "terms" -> navController.navigate(Routes.cmsPath("terms"))
                        "privacy-policy" -> navController.navigate(Routes.cmsPath("privacy"))
                        "about" -> navController.navigate(Routes.cmsPath("about"))
                        "audit" -> navController.navigate(Routes.AUDIT)
                        "delete-account" -> navController.navigate(Routes.DELETE_ACCOUNT)
                        else -> Unit
                    }
                },
            )
        }

        // ---- Stage 4 transactions / receipts / AI personalization ----
        composable(Routes.TRANSACTIONS) {
            TransactionsListScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate(Routes.transaction(id)) },
                onOpenReceipt = { num -> navController.navigate(Routes.receiptPreview(num)) },
            )
        }
        composable(
            Routes.TRANSACTION_DETAIL,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) { entry ->
            val tid = entry.arguments?.getString("transactionId").orEmpty()
            TransactionDetailScreen(
                transactionId = tid,
                onBack = { navController.popBackStack() },
                onOpenReceipt = { num -> navController.navigate(Routes.receiptPreview(num)) },
                onOpenThread = { threadId, _ -> navController.navigate("thread/$threadId/") },
                onOpenDispute = { id -> navController.navigate(Routes.dispute(id)) },
                onCreateReceipt = { id -> navController.navigate(Routes.receiptNewForTx(id)) },
            )
        }
        composable(Routes.RECEIPT_NEW) {
            ReceiptDesignerScreen(
                transactionId = null,
                onBack = { navController.popBackStack() },
                onReceiptCreated = { num ->
                    navController.navigate(Routes.receiptPreview(num)) {
                        popUpTo(Routes.RECEIPT_NEW) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.RECEIPT_NEW_FOR_TX,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) { entry ->
            val tid = entry.arguments?.getString("transactionId").orEmpty()
            ReceiptDesignerScreen(
                transactionId = tid,
                onBack = { navController.popBackStack() },
                onReceiptCreated = { num ->
                    navController.navigate(Routes.receiptPreview(num)) {
                        popUpTo(Routes.RECEIPT_NEW_FOR_TX) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.RECEIPT_PREVIEW,
            arguments = listOf(navArgument("receiptNumber") { type = NavType.StringType }),
        ) { entry ->
            val num = entry.arguments?.getString("receiptNumber").orEmpty()
            ReceiptPreviewScreen(
                receiptNumber = num,
                onBack = { navController.popBackStack() },
                onAcknowledge = { /* acknowledged */ },
            )
        }
        composable(Routes.RECEIPTS_HISTORY) {
            ReceiptsHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenReceipt = { num -> navController.navigate(Routes.receiptPreview(num)) },
            )
        }
        composable(
            Routes.DISPUTE,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) { entry ->
            val tid = entry.arguments?.getString("transactionId").orEmpty()
            DisputeScreen(
                transactionId = tid,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.AI_PERSONALIZATION) {
            AiPersonalizationScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAiPersonalization = { navController.navigate(Routes.AI_PERSONALIZATION) },
            )
        }
        composable(Routes.MESSAGES) {
            MessagesScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { sellerId, _ -> navController.navigate(Routes.thread(sellerId, null)) },
            )
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { id -> navController.navigate(Routes.product(id)) },
            )
        }
        composable(Routes.BECOME_SELLER) {
            BecomeSellerScreen(
                onBack = { navController.popBackStack() },
                onUpgraded = {
                    navController.navigate(Routes.dashboard(Role.SELLER)) {
                        popUpTo(Routes.BUYER_HOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.NEARBY_MAP) {
            NearbyMapScreen(
                onBack = { navController.popBackStack() },
                onOpenStore = { id -> navController.navigate(Routes.storefront(id)) },
                onOpenProductById = { id -> navController.navigate(Routes.product(id)) },
            )
        }

        // ---- User settings routes ----
        composable(Routes.ACCOUNT) { AccountSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SECURITY) { SecurityScreen(onBack = { navController.popBackStack() }, onSignOut = { navController.navigate(Routes.LOGIN.format(Role.BUYER)) { popUpTo(0) } }) }
        composable(Routes.ADDRESSES) { AddressesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PAYMENT_METHODS) { PaymentMethodsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATION_PREFS) { NotificationSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.LANGUAGE) { LanguageScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.THEME) { ThemeScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CURRENCY) { CurrencyScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.MY_ORDERS) {
            MyOrdersScreen(
                onBack = { navController.popBackStack() },
                onTrack = { id -> navController.navigate(Routes.TRACK_ORDER.replace("{orderId}", URLEncoder.encode(id, "UTF-8"))) },
                onOpenReturn = { navController.navigate(Routes.RETURNS) },
                onOpenRefund = { navController.navigate(Routes.REFUNDS) },
            )
        }
        composable(
            Routes.TRACK_ORDER,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
        ) { backStack ->
            val id = URLDecoder.decode(backStack.arguments?.getString("orderId") ?: "", "UTF-8")
            TrackOrderScreen(orderId = id, onBack = { navController.popBackStack() })
        }
        composable(Routes.SAVED_PRODUCTS) { SavedProductsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SAVED_SELLERS) { SavedSellersScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.REFUNDS) { RefundsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.RETURNS) { ReturnsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HELP) {
            HelpCenterScreen(
                onBack = { navController.popBackStack() },
                onContact = { navController.navigate(Routes.CONTACT) },
                onTerms = { navController.navigate(Routes.cmsPath("terms")) },
                onPrivacy = { navController.navigate(Routes.cmsPath("privacy")) },
                onReport = { navController.navigate(Routes.REPORT) },
            )
        }
        composable(Routes.CONTACT) { ContactScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.REPORT) { ReportProblemScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ABOUT) { CmsScreen(slug = "about", title = "About ScottsTechX", onBack = { navController.popBackStack() }) }
        composable(Routes.AUDIT) { AuditScreen(onBack = { navController.popBackStack() }) }
        composable(
            Routes.CMS,
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: "about"
            CmsScreen(
                slug = slug,
                title = when (slug) {
                    "terms" -> "Terms of Service"
                    "privacy" -> "Privacy Policy"
                    "about" -> "About ScottsTechX"
                    "buyer-protection" -> "Buyer Protection"
                    "help" -> "Help Center"
                    "contact" -> "Contact"
                    else -> slug.replaceFirstChar { it.uppercase() }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DELETE_ACCOUNT) { DeleteAccountScreen(onBack = { navController.popBackStack() }, onConfirm = { navController.popBackStack() }) }
        composable(Routes.PRIVACY) { CmsScreen(slug = "privacy", title = "Privacy Policy", onBack = { navController.popBackStack() }) }
        composable(
            Routes.CMS,
            arguments = listOf(navArgument("slug") { type = NavType.StringType }),
        ) { backStack ->
            val slug = backStack.arguments?.getString("slug") ?: ""
            val title = when (slug) {
                "terms" -> "Terms of Service"
                "privacy" -> "Privacy Policy"
                "about" -> "About ScottsTechX"
                "help" -> "Help Center"
                "contact" -> "Contact ScottsTechX"
                "buyer-protection" -> "Buyer Protection"
                else -> slug.replaceFirstChar { it.uppercase() }
            }
            CmsScreen(slug = slug, title = title, onBack = { navController.popBackStack() })
        }
    }

}

/**
 * Tiny holder that lazily builds a GoogleSignInHelper when the
 * Application context is reachable. Letting AppNavigation own the
 * helper means the LoginScreen can stay a pure composable and we
 * avoid recreating the GoogleSignInClient on every recomposition.
 */
// Removed in Stage 3.1 — the LoginScreen now constructs its own
// helper via `remember(activityContext)` and clears the cached
// Google account on demand via the "Use a different Google account"
// button. AppNavigation does not need to hold a singleton helper.

private fun onBuyerTab(navController: NavHostController, tab: BottomTab) {
    val dn = SessionCache.displayName ?: "Buyer"
    val em = SessionCache.email ?: ""
    val encoded = java.net.URLEncoder.encode(em, "UTF-8")
    when (tab) {
        BottomTab.Home -> navController.navigate("buyer_home/$dn/$encoded") {
            popUpTo(Routes.BUYER_HOME) { inclusive = true }
            launchSingleTop = true
        }
        BottomTab.Nearby -> navController.navigate(Routes.NEARBY) {
            launchSingleTop = true
        }
        BottomTab.Ai -> navController.navigate(Routes.AI) {
            launchSingleTop = true
        }
        BottomTab.Wishlist -> navController.navigate(Routes.WISHLIST) {
            launchSingleTop = true
        }
        BottomTab.Profile -> navController.navigate("profile/$dn/$encoded") {
            launchSingleTop = true
        }
    }
}

private fun onSellerTab(navController: NavHostController, tab: BottomTab) {
    val dn = SessionCache.displayName ?: "Seller"
    val em = SessionCache.email ?: ""
    val encoded = java.net.URLEncoder.encode(em, "UTF-8")
    when (tab) {
        BottomTab.Home -> navController.navigate("seller_home/$dn/$encoded") {
            popUpTo(Routes.SELLER_HOME) { inclusive = true }
            launchSingleTop = true
        }
        BottomTab.Nearby -> navController.navigate(Routes.NEARBY) {
            launchSingleTop = true
        }
        BottomTab.Ai -> navController.navigate(Routes.AI) {
            launchSingleTop = true
        }
        BottomTab.Wishlist -> navController.navigate(Routes.WISHLIST) {
            launchSingleTop = true
        }
        BottomTab.Profile -> navController.navigate("profile/$dn/$encoded") {
            launchSingleTop = true
        }
    }
}

@Composable
private fun SplashHost(onContinue: () -> Unit) {
    SplashScreen(onContinue = onContinue)
}

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val ROLE = "role"
    const val LOGIN = "login/{role}"
    const val SIGNUP = "signup/{role}"
    const val WRONG_ROLE = "wrongRole/{picked}/{actual}"
    const val VERIFY_EMAIL_PENDING = "verify-email-pending/{email}"
    const val HOME = "home/{role}"
    const val BUYER_HOME = "buyer_home/{displayName}/{email}"
    const val SELLER_HOME = "seller_home/{displayName}/{email}"
    const val CART = "cart"
    const val NEARBY = "nearby"
    const val AI = "ai"
    const val SELLER_AI = "seller/ai"
    const val SEARCH = "search"
    const val CATEGORIES = "categories"
    const val WISHLIST = "wishlist"
    const val PROFILE = "profile/{displayName}/{email}"

    // ---- Stage 3 product discovery routes ----
    const val PRODUCT = "product/{productId}"
    const val STOREFRONT = "storefront/{sellerId}"
    const val REVIEWS = "reviews/{productId}"
    const val THREAD = "thread/{sellerId}/{productId}"
    // ---- Stage 3 seller side routes ----
    const val SELLER_ORDERS = "seller/orders"
    const val SELLER_ADD_PRODUCT = "seller/add-product"
    const val SELLER_MESSAGES = "seller/messages"
    const val SELLER_ANALYTICS = "seller/analytics"
    const val SELLER_TOOLS = "seller/marketplace-tools"
    const val SELLER_STORE_SETTINGS = "seller/store-settings"
    const val SELLER_STORE_SETTING_DETAIL = "seller/store-settings/{section}"
    const val SELLER_PROFILE_SETTINGS = "seller/profile-settings"
    const val BUYER_PROFILE_SETTINGS = "buyer/profile-settings"

    // ---- Stage 4 transaction / receipt / AI routes ----
    const val TRANSACTIONS = "transactions"
    const val TRANSACTION_DETAIL = "transaction/{transactionId}"
    const val RECEIPT_NEW = "receipt/new"
    const val RECEIPT_NEW_FOR_TX = "receipt/new/{transactionId}"
    const val RECEIPT_PREVIEW = "receipt/preview/{receiptNumber}"
    const val RECEIPTS_HISTORY = "receipts"
    const val DISPUTE = "dispute/{transactionId}"
    const val AI_PERSONALIZATION = "ai/personalization"
    const val NEARBY_MAP = "nearby/map"
    const val SETTINGS = "settings"

    // ---- Stage 5.x communications routes ----
    const val MESSAGES = "messages"
    const val NOTIFICATIONS = "notifications"
    const val BECOME_SELLER = "become-seller"

    // ---- User settings sub-routes ----
    const val ACCOUNT = "settings/account"
    const val SECURITY = "settings/security"
    const val ADDRESSES = "settings/addresses"
    const val PAYMENT_METHODS = "settings/payment-methods"
    const val NOTIFICATION_PREFS = "settings/notifications"
    const val PRIVACY = "settings/privacy"
    const val LANGUAGE = "settings/language"
    const val CURRENCY = "settings/currency"
    const val THEME = "settings/theme"
    const val MY_ORDERS = "settings/my-orders"
    const val TRACK_ORDER = "settings/track-order/{orderId}"
    const val SAVED_PRODUCTS = "settings/saved-products"
    const val SAVED_SELLERS = "settings/saved-sellers"
    const val REFUNDS = "settings/refunds"
    const val RETURNS = "settings/returns"
    const val HELP = "settings/help"
    const val CONTACT = "settings/contact"
    const val REPORT = "settings/report"
    const val ABOUT = "settings/about"
    const val AUDIT = "settings/audit"
    const val DELETE_ACCOUNT = "settings/delete-account"
    const val CMS = "settings/cms/{slug}"
    fun cmsPath(slug: String) = "settings/cms/$slug"

    fun transaction(id: String) = "transaction/${URLEncoder.encode(id, "UTF-8")}"
    fun receiptNew() = "receipt/new"
    fun receiptNewForTx(id: String) = "receipt/new/${URLEncoder.encode(id, "UTF-8")}"
    fun receiptPreview(num: String) = "receipt/preview/${URLEncoder.encode(num, "UTF-8")}"
    fun dispute(id: String) = "dispute/${URLEncoder.encode(id, "UTF-8")}"
    fun agreementNew(productId: String) = "agreement/new/${URLEncoder.encode(productId, "UTF-8")}"

    fun product(id: String) = "product/${URLEncoder.encode(id, "UTF-8")}"
    fun storefront(id: String) = "storefront/${URLEncoder.encode(id, "UTF-8")}"
    fun reviews(id: String) = "reviews/${URLEncoder.encode(id, "UTF-8")}"
    fun thread(sellerId: String, productId: String? = null) =
        "thread/${URLEncoder.encode(sellerId, "UTF-8")}/${URLEncoder.encode(productId ?: "", "UTF-8")}"

    fun login(role: Role) = "login/${role.name}"
    fun signup(role: Role) = "signup/${role.name}"
    fun home(role: Role) = "home/${role.name}"
    fun wrongRole(picked: Role, actual: Role) = "wrongRole/${picked.name}/${actual.name}"
    fun verifyEmailPending(email: String): String =
        "verify-email-pending/${URLEncoder.encode(email, "UTF-8")}"
    /**
     * Build the dashboard route for the given [role]. Falls back to
     * "Buyer" / "Seller" / a single non-empty placeholder when
     * SessionCache fields are empty so the resulting path always
     * matches the registered route shape
     * `buyer_home/{displayName}/{email}` or
     * `seller_home/{displayName}/{email}`.
     *
     * Without this guard, an empty display name / email yields
     * `"buyer_home/Buyer/"` (trailing slash) which Navigation
     * Compose rejects with IllegalArgumentException "cannot be
     * found in the navigation graph".
     */
    fun dashboard(role: Role): String = when (role) {
        Role.BUYER -> buyerHome(
            com.scottsx.app.data.domain.BuyerProfile(
                uid = "",
                displayName = SessionCache.displayName ?: "Buyer",
                email = SessionCache.email ?: "buyer",
            ),
        )
        Role.SELLER -> sellerHome(
            displayName = SessionCache.displayName ?: "Seller",
            email = SessionCache.email ?: "seller",
        )
    }

    fun sellerHome(displayName: String, email: String): String =
        "seller_home/" +
            java.net.URLEncoder.encode(displayName.ifBlank { "Seller" }, "UTF-8") +
            "/" +
            java.net.URLEncoder.encode(email.ifBlank { "seller" }, "UTF-8")
    fun buyerHome(profile: com.scottsx.app.data.domain.BuyerProfile) =
        "buyer_home/${profile.displayName}/" +
            java.net.URLEncoder.encode(profile.email, "UTF-8")

    fun roleFromBackStack(s: String?): Role =
        if (s.equals("Seller", true)) Role.SELLER else Role.BUYER
}