package com.scottsx.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scottsx.app.SessionCache
import com.scottsx.app.UserPrefs
import com.scottsx.app.ui.screens.AccountSettingsScreen
import com.scottsx.app.ui.screens.AddProductScreen
import com.scottsx.app.ui.screens.AddressesScreen
import com.scottsx.app.ui.screens.AiPersonalizationScreen
import com.scottsx.app.ui.screens.BecomeSellerScreen
import com.scottsx.app.ui.screens.BuyerHomeScreen
import com.scottsx.app.ui.screens.CartScreen
import com.scottsx.app.ui.screens.CmsScreen
import com.scottsx.app.ui.screens.ForgotPasswordScreen
import com.scottsx.app.ui.screens.LoginScreen
import com.scottsx.app.ui.screens.MessageThreadScreen
import com.scottsx.app.ui.screens.MessagesScreen
import com.scottsx.app.ui.screens.NearbyScreen
import com.scottsx.app.ui.screens.NotificationsScreen
import com.scottsx.app.ui.screens.OrdersScreen
import com.scottsx.app.ui.screens.PaymentMethodsScreen
import com.scottsx.app.ui.screens.ProductDetailScreen
import com.scottsx.app.ui.screens.ProfileScreen
import com.scottsx.app.ui.screens.ProfileSettingsScreen
import com.scottsx.app.ui.screens.RealAiChatScreen
import com.scottsx.app.ui.screens.RefundsScreen
import com.scottsx.app.ui.screens.SavedProductsScreen
import com.scottsx.app.ui.screens.SellerAIAssistantScreen
import com.scottsx.app.ui.screens.SellerAnalyticsScreen
import com.scottsx.app.ui.screens.SellerHomeScreen
import com.scottsx.app.ui.screens.SellerMessagesScreen
import com.scottsx.app.ui.screens.SignUpScreen
import com.scottsx.app.ui.screens.StoreSettingsDetailScreen
import com.scottsx.app.ui.screens.SupportScreen
import com.scottsx.app.ui.screens.ThemeScreen
import com.scottsx.app.ui.screens.VerifyEmailScreen
import com.scottsx.app.ui.screens.OnboardingScreen
import com.scottsx.app.ui.screens.SplashScreen
import com.scottsx.app.ui.screens.WelcomeScreen

/** All navigation routes — kebab-case strings, matching the master doc. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"
    const val LOGIN = "login?role={role}"
    const val FORGOT_PASSWORD = "forgot-password"
    const val SIGNUP = "signup?role={role}"

    /** Sign-up with the buyer/seller choice already made. */
    fun signup(role: String): String = "signup?role=" + role

    /** Login carrying the buyer/seller choice from the welcome screen. */
    fun login(role: String): String = "login?role=" + role
    const val VERIFY_EMAIL = "verify-email"
    const val BUYER_HOME = "buyer/home"
    const val SELLER_HOME = "seller/home"
    const val PRODUCT = "product/{id}"
    const val MESSAGES = "messages"
    const val THREAD = "thread/{conversationId}"
    const val AI = "ai"
    const val SELLER_AI = "seller/ai"
    const val SELLER_MESSAGES = "seller/messages"
    const val SELLER_ANALYTICS = "seller/analytics"
    const val PROFILE = "profile"
    const val ACCOUNT = "settings/account"
    const val ADDRESSES = "settings/addresses"
    const val PAYMENT_METHODS = "settings/payment-methods"
    const val THEME = "settings/theme"
    const val NEARBY = "nearby"
    const val NOTIFICATIONS = "notifications"
    const val BECOME_SELLER = "become-seller"
    const val AI_PERSONALIZATION = "ai-personalization"
    const val CMS = "cms/{slug}"
    const val SELLER_STORE_SETTING_DETAIL = "seller/store-settings/{section}"
    const val BUYER_PROFILE_SETTINGS = "settings/buyer-profile"
    const val ADD_PRODUCT = "seller/add-product"
    const val EDIT_PRODUCT = "seller/product/{id}/edit"
    const val CART = "cart"
    const val ORDERS = "orders"
    const val SAVED_PRODUCTS = "saved-products"
    const val REFUNDS = "refunds"
    const val SUPPORT = "support"

    fun product(id: String) = "product/$id"
    fun editProduct(id: String) = "seller/product/$id/edit"
    fun thread(conversationId: String) = "thread/$conversationId"
    fun cms(slug: String) = "cms/$slug"
    fun sellerStoreSetting(section: String) = "seller/store-settings/$section"

    /** The right home for the signed-in role. */
    fun homeForRole(role: String?): String = if (role == "seller") SELLER_HOME else BUYER_HOME
}

/** Root nav host. Welcome decides where to land based on the session. */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Every launch opens on the brand splash, the way other apps do. What
    // comes after it depends on whether the intro has been seen before: first
    // run continues to the three intro screens, later runs go straight to the
    // login section.
    val afterSplash = if (UserPrefs.onboardingSeenSafe()) Routes.WELCOME else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(afterSplash) {
                        // The splash must not be reachable with Back.
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    UserPrefs.markOnboardingSeen()
                    navController.navigate(Routes.WELCOME) {
                        // The intro must not be reachable with Back.
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onLogin = { role -> navController.navigate(Routes.login(role)) },
                onSignUp = { role -> navController.navigate(Routes.signup(role)) },
                onOpenLegal = { slug -> navController.navigate(Routes.cms(slug)) },
            )
        }
        composable(
            Routes.LOGIN,
            arguments = listOf(
                navArgument("role") {
                    type = NavType.StringType
                    defaultValue = "buyer"
                },
            ),
        ) { entry ->
            // Only buyer/seller are accepted; anything else falls back to
            // buyer so a hand-crafted deep link cannot pick a role the
            // backend would reject.
            val requested = entry.arguments?.getString("role")
            val loginRole = if (requested == "seller") "seller" else "buyer"
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoggedIn = { role -> navigateHome(navController, role) },
                onGoSignUp = { navController.navigate(Routes.signup(loginRole)) },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                initialRole = loginRole,
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.SIGNUP,
            arguments = listOf(
                navArgument("role") {
                    type = NavType.StringType
                    defaultValue = "buyer"
                },
            ),
        ) { entry ->
            // Only buyer/seller are accepted; anything else falls back to
            // buyer so a hand-crafted deep link cannot pick a role the
            // backend would reject.
            val requested = entry.arguments?.getString("role")
            val signupRole = if (requested == "seller") "seller" else "buyer"
            SignUpScreen(
                onBack = { navController.popBackStack() },
                onLoggedIn = { role -> navigateHome(navController, role) },
                initialRole = signupRole,
            )
        }
        composable(Routes.VERIFY_EMAIL) {
            VerifyEmailScreen(
                onVerified = { role ->
                    navController.navigate(Routes.homeForRole(role)) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSignOut = {
                    SessionCache.clear()
                    navController.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.BUYER_HOME) {
            BuyerHomeScreen(
                onProductClick = { id -> navController.navigate(Routes.product(id)) },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(Routes.SELLER_HOME) {
            SellerHomeScreen(
                onProductClick = { id -> navController.navigate(Routes.product(id)) },
                onAddProduct = { navController.navigate(Routes.ADD_PRODUCT) },
                onEditProduct = { id -> navController.navigate(Routes.editProduct(id)) },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(
            route = Routes.PRODUCT,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            val id = it.arguments?.getString("id") ?: ""
            ProductDetailScreen(
                productId = id,
                onBack = { navController.popBackStack() },
                onMessageSeller = { sellerId ->
                    val conv = com.scottsx.app.data.remote.V2Client.openConversation(sellerId, id)
                    if (conv != null) navController.navigate(Routes.thread(conv))
                },
                onViewCart = { navController.navigate(Routes.CART) },
                onEditProduct = { productId -> navController.navigate(Routes.editProduct(productId)) },
                onDeleted = {
                    // Rebuild the seller home so the inventory refetches and
                    // the deleted card is gone instead of stale.
                    navController.navigate(Routes.SELLER_HOME) {
                        popUpTo(Routes.SELLER_HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MESSAGES) {
            MessagesScreen(onThreadClick = { navController.navigate(Routes.thread(it)) })
        }
        composable(
            route = Routes.THREAD,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) {
            val conversationId = it.arguments?.getString("conversationId") ?: ""
            MessageThreadScreen(conversationId = conversationId, onBack = { navController.popBackStack() })
        }
        composable(Routes.AI) { RealAiChatScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SELLER_AI) { SellerAIAssistantScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SELLER_MESSAGES) {
            SellerMessagesScreen(onThreadClick = { navController.navigate(Routes.thread(it)) })
        }
        composable(Routes.SELLER_ANALYTICS) { SellerAnalyticsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                    SessionCache.clear()
                    com.scottsx.app.data.firebase.FirebaseBridge.signOut()
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ACCOUNT) { AccountSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ADDRESSES) { AddressesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PAYMENT_METHODS) { PaymentMethodsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.THEME) { ThemeScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NEARBY) { NearbyScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.BECOME_SELLER) {
            BecomeSellerScreen(
                onBack = { navController.popBackStack() },
                onDone = {
                    SessionCache.user.value?.let { u ->
                        if (u.role == "seller") {
                            navController.navigate(Routes.SELLER_HOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        } else navController.popBackStack()
                    } ?: navController.popBackStack()
                },
            )
        }
        composable(Routes.AI_PERSONALIZATION) { AiPersonalizationScreen(onBack = { navController.popBackStack() }) }
        composable(
            route = Routes.CMS,
            arguments = listOf(navArgument("slug") { type = NavType.StringType }),
        ) {
            val slug = it.arguments?.getString("slug") ?: "about"
            CmsScreen(slug = slug, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.SELLER_STORE_SETTING_DETAIL,
            arguments = listOf(navArgument("section") { type = NavType.StringType }),
        ) {
            val section = it.arguments?.getString("section") ?: "store-profile"
            StoreSettingsDetailScreen(section = section, onBack = { navController.popBackStack() })
        }
        composable(Routes.BUYER_PROFILE_SETTINGS) { ProfileSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ADD_PRODUCT) {
            // Leaving the wizard goes to a REBUILT seller home so the new
            // listing appears in the inventory immediately.
            AddProductScreen(onBack = {
                navController.navigate(Routes.SELLER_HOME) {
                    popUpTo(Routes.SELLER_HOME) { inclusive = true }
                }
            })
        }
        composable(
            route = Routes.EDIT_PRODUCT,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            val id = it.arguments?.getString("id") ?: ""
            EditProductScreen(
                productId = id,
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate(Routes.SELLER_HOME) {
                        popUpTo(Routes.SELLER_HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CART) {
            CartScreen(
                onBack = { navController.popBackStack() },
                onProductClick = { id -> navController.navigate(Routes.product(id)) },
                onBrowse = {
                    navController.navigate(Routes.BUYER_HOME) { popUpTo(0) { inclusive = true } }
                },
                onOrderPlaced = {
                    // Land on Orders, and don't leave the emptied cart behind
                    // for the back button.
                    navController.navigate(Routes.ORDERS) { popUpTo(Routes.CART) { inclusive = true } }
                },
            )
        }
        composable(Routes.ORDERS) { OrdersScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SAVED_PRODUCTS) { SavedProductsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.REFUNDS) { RefundsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SUPPORT) { SupportScreen(onBack = { navController.popBackStack() }) }
    }
}

/**
 * Send a freshly-authenticated user to the right place.
 *
 * The backend refuses every private route with 403 EMAIL_NOT_VERIFIED until the
 * address is proven, so dropping an unverified account on a home screen would
 * show it a wall of errors it has no way to clear. Route it to the gate
 * instead. This is the single funnel both sign-in and sign-up use, so the check
 * belongs here rather than duplicated in each screen.
 */
private fun navigateHome(navController: NavHostController, role: String?) {
    val destination = if (SessionCache.user.value?.emailVerified == false) {
        Routes.VERIFY_EMAIL
    } else {
        Routes.homeForRole(role)
    }
    navController.navigate(destination) {
        popUpTo(0) { inclusive = true }
    }
}
