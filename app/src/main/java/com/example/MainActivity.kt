package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.IconButton
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AppLanguage
import com.example.data.JaranwalaRepository
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DriverScreen
import com.example.ui.screens.PassengerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RideHailingRequestScreen
import com.example.ui.screens.RideHistoryAndPaymentsScreen
import com.example.ui.screens.RidesDashboardScreen
import com.example.ui.screens.WeddingBaratScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.RideNotificationManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay

object AppRoutes {
    const val AUTH = "auth"
    const val MAIN_HOME = "main_home"
    const val REQUEST_RIDE = "request_ride"
    const val RIDES_DASHBOARD = "rides_dashboard"
    const val HISTORY_PAYMENTS = "history_payments"
    const val PROFILE = "profile"
}

enum class MainTab(
    val title: String,
    val urduTitle: String
) {
    PASSENGER("Rider", "سواری"),
    DRIVER("Captain", "کپتان"),
    WEDDING("Wedding", "شادی کار"),
    ADMIN("Admin", "ایڈمن")
}

private const val APP_CHECK_TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Must use synchronous commit() before DebugAppCheckProviderFactory reads it
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(APP_CHECK_TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(APP_CHECK_TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(APP_CHECK_TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(APP_CHECK_TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(APP_CHECK_TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(APP_CHECK_TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(APP_CHECK_TAG, "Failed to initialize Firebase App Check", e)
    }
}

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var deepLinkTabState by mutableIntStateOf(-1)
    private var deepLinkRideIdState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        extractDeepLink(intent)
        com.example.ui.components.ensureOsmDroidConfigured(applicationContext)

        val repository = JaranwalaRepository.getInstance()
        repository.initRoomDatabase(applicationContext)

        setContent {
            MyApplicationTheme {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    OfficialSplashScreen(onTimeout = { showSplash = false })
                } else {
                    MainAppScreen(
                        repository = repository,
                        initialDeepLinkTab = deepLinkTabState,
                        deepLinkRideId = deepLinkRideIdState,
                        onConsumeDeepLink = {
                            deepLinkTabState = -1
                            deepLinkRideIdState = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?) {
        if (intent == null) return
        val tab = intent.getIntExtra(RideNotificationManager.EXTRA_TARGET_TAB, -1)
        if (tab in 0..3) {
            deepLinkTabState = tab
        }
        val rideId = intent.getStringExtra(RideNotificationManager.EXTRA_RIDE_ID)
        if (!rideId.isNullOrBlank()) {
            deepLinkRideIdState = rideId
        }
    }
}

@Composable
fun OfficialSplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200L)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF031A12))
            .clickable { onTimeout() }
            .testTag("official_splash_screen")
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_screen_jaranwala_ride),
            contentDescription = "Jaranwala Ride Official Splash Screen - Created by MAHI",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    repository: JaranwalaRepository,
    initialDeepLinkTab: Int = -1,
    deepLinkRideId: String? = null,
    onConsumeDeepLink: () -> Unit = {}
) {
    val currentUser by repository.currentUser.collectAsState()
    val appLang by repository.appLanguage.collectAsState()
    val incomingReq by repository.incomingDriverRequest.collectAsState()
    val activeRide by repository.currentPassengerRide.collectAsState()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val initialRoute = remember { if (currentUser == null) AppRoutes.AUTH else AppRoutes.MAIN_HOME }

    // Role-based routing: automatically select correct dashboard based on verified user role or deep link
    var selectedTab by remember {
        mutableIntStateOf(
            if (initialDeepLinkTab in 0..3) {
                initialDeepLinkTab
            } else {
                when (currentUser?.role) {
                    com.example.data.UserRole.DRIVER -> 1
                    com.example.data.UserRole.ADMIN -> 3
                    else -> 0
                }
            }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(initialDeepLinkTab, deepLinkRideId) {
        if (initialDeepLinkTab in 0..3) {
            selectedTab = initialDeepLinkTab
        }
        if (!deepLinkRideId.isNullOrBlank()) {
            repository.listenToActivePassengerRide(deepLinkRideId)
            if (initialDeepLinkTab !in 0..3) {
                selectedTab = 0
            }
        }
        if ((initialDeepLinkTab in 0..3 || !deepLinkRideId.isNullOrBlank()) && currentUser != null) {
            navController.navigate(AppRoutes.MAIN_HOME) {
                popUpTo(AppRoutes.MAIN_HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
        if (initialDeepLinkTab in 0..3 || !deepLinkRideId.isNullOrBlank()) {
            onConsumeDeepLink()
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val auth = try { com.google.firebase.auth.FirebaseAuth.getInstance() } catch (_: Throwable) { null }
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            val fbUser = firebaseAuth.currentUser
            if (fbUser != null) {
                repository.syncUserFromFirestore(fbUser.uid, context)
                try {
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        if (!token.isNullOrBlank()) {
                            repository.saveFcmToken(fbUser.uid, token, context)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
        auth?.addAuthStateListener(listener)
        onDispose {
            auth?.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(currentUser?.uid, currentUser?.role) {
        val user = currentUser
        if (user != null) {
            selectedTab = when (user.role) {
                com.example.data.UserRole.DRIVER -> 1
                com.example.data.UserRole.ADMIN -> 3
                else -> 0
            }
            val fbUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
            if (!fbUid.isNullOrBlank()) {
                try {
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        if (!token.isNullOrBlank()) {
                            repository.saveFcmToken(fbUid, token, context)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    // Subscribe to role-based FCM topics and real-time /notification_events for cross-device alerts
    androidx.compose.runtime.DisposableEffect(currentUser?.uid, currentUser?.role, currentUser?.isGuest) {
        val user = currentUser
        val listenerReg = if (user != null && !user.isGuest) {
            repository.registerFcmTopics(user.role)
            repository.listenToUserNotificationEvents(user.uid, context)
        } else {
            null
        }
        onDispose {
            listenerReg?.remove()
        }
    }

    var weddingModeIsBarat by remember { mutableStateOf(false) }

    // Keep Navigation Compose backstack synchronized with auth session changes
    LaunchedEffect(currentUser?.uid, currentUser?.isGuest) {
        val user = currentUser
        val destRoute = navController.currentDestination?.route
        if (user == null) {
            if (destRoute != null && destRoute != AppRoutes.AUTH) {
                navController.navigate(AppRoutes.AUTH) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } else if (destRoute == AppRoutes.AUTH && navController.previousBackStackEntry == null) {
            navController.navigate(AppRoutes.MAIN_HOME) {
                popUpTo(AppRoutes.AUTH) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Request notification permission on Android 13+ (API 33+)
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        com.example.util.RideNotificationManager.createNotificationChannel(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Push notification system: alerts users whenever ride status changes (Driver Assigned -> Arrived -> In Progress -> Completed)
    LaunchedEffect(activeRide?.id, activeRide?.status) {
        val ride = activeRide
        if (ride != null && ride.status != com.example.data.RideStatus.IDLE && ride.status != com.example.data.RideStatus.SEARCHING) {
            com.example.util.RideNotificationManager.notifyRideStatusChange(context, ride)
        }
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(AppRoutes.AUTH) {
            val canDismissAuth = currentUser != null && navController.previousBackStackEntry != null
            AuthScreen(
                repository = repository,
                onAuthComplete = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(AppRoutes.MAIN_HOME) {
                            popUpTo(AppRoutes.AUTH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onDismiss = if (canDismissAuth) {
                    { navController.popBackStack() }
                } else {
                    null
                }
            )
        }

        composable(AppRoutes.REQUEST_RIDE) {
            RideHailingRequestScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onRideRequested = {
                    // Remain on RideHailingRequestScreen so passenger tracks live OSMDroid marker updates
                }
            )
        }

        composable(AppRoutes.RIDES_DASHBOARD) {
            RidesDashboardScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onRebookRide = { _, _, _ ->
                    selectedTab = 0
                    navController.popBackStack(AppRoutes.MAIN_HOME, inclusive = false)
                },
                onTrackRideOnMap = { _ ->
                    selectedTab = 0
                    navController.popBackStack(AppRoutes.MAIN_HOME, inclusive = false)
                }
            )
        }

        composable(AppRoutes.HISTORY_PAYMENTS) {
            RideHistoryAndPaymentsScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenAuth = {
                    navController.navigate(AppRoutes.AUTH) {
                        launchSingleTop = true
                    }
                },
                onRebookRide = { _, _, _ ->
                    selectedTab = 0
                    navController.popBackStack(AppRoutes.MAIN_HOME, inclusive = false)
                }
            )
        }

        composable(AppRoutes.PROFILE) {
            ProfileScreen(
                repository = repository,
                onOpenAuth = {
                    navController.navigate(AppRoutes.AUTH) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.MAIN_HOME) {
            // Handle back press to return to Passenger home
            BackHandler(enabled = selectedTab != 0) {
                selectedTab = 0
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF064E3B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_jaranwala_ride_logo),
                                        contentDescription = "Jaranwala Ride Logo",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Jaranwala Ride",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFFF59E0B),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "PKR ₨",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (appLang == AppLanguage.URDU) "آپ کا شہر، آپ کی سواری • ماہی" else "Your City, Your Ride • Created by MAHI",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    navController.navigate(AppRoutes.HISTORY_PAYMENTS) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.testTag("btn_top_history_payments")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = "Ride History and Payments",
                                    tint = Color(0xFFF59E0B)
                                )
                            }
                            IconButton(
                                onClick = {
                                    navController.navigate(AppRoutes.RIDES_DASHBOARD) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.testTag("btn_top_rides_dashboard")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dashboard,
                                    contentDescription = "Rides Dashboard - Active and Past Rides",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    navController.navigate(AppRoutes.PROFILE) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.testTag("btn_top_profile")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "My Profile and Emergency Contact",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        navController.navigate(AppRoutes.AUTH) {
                                            launchSingleTop = true
                                        }
                                    }
                                    .testTag("btn_top_auth"),
                                color = if (currentUser != null) Color(0xFF064E3B) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Account",
                                        tint = if (currentUser != null) Color(0xFF10B981) else Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = currentUser?.name?.take(10) ?: (if (appLang == AppLanguage.URDU) "لاگ ان" else "Login"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .testTag("main_bottom_nav"),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        // 1. Rider (Passenger)
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                if (activeRide != null) {
                                    BadgedBox(badge = { Badge { Text("1") } }) {
                                        Icon(Icons.Default.DirectionsCar, contentDescription = "Rider")
                                    }
                                } else {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = "Rider")
                                }
                            },
                            label = {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "سواری" else "Rider",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_item_passenger")
                        )

                        // 2. Captain (Driver)
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                if (incomingReq != null) {
                                    BadgedBox(badge = { Badge(containerColor = Color(0xFFEF4444)) { Text("!") } }) {
                                        Icon(Icons.Default.Speed, contentDescription = "Captain")
                                    }
                                } else {
                                    Icon(Icons.Default.Speed, contentDescription = "Captain")
                                }
                            },
                            label = {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "کپتان" else "Captain",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_item_driver")
                        )

                        // 3. Wedding & Barat
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Wedding")
                            },
                            label = {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "شادی کار" else "Wedding",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_item_wedding")
                        )

                        // 4. Admin (Merged with MAHI Owner Portal)
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin")
                            },
                            label = {
                                Text(
                                    text = if (appLang == AppLanguage.URDU) "ایڈمن" else "Admin",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_item_admin")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        0 -> PassengerScreen(
                            repository = repository,
                            onNavigateToWedding = { isBarat ->
                                weddingModeIsBarat = isBarat
                                selectedTab = 2
                            },
                            onOpenRidesDashboard = {
                                navController.navigate(AppRoutes.RIDES_DASHBOARD) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenHistoryAndPayments = {
                                navController.navigate(AppRoutes.HISTORY_PAYMENTS) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenRequestRide = {
                                navController.navigate(AppRoutes.REQUEST_RIDE) {
                                    launchSingleTop = true
                                }
                            }
                        )
                        1 -> DriverScreen(repository = repository)
                        2 -> WeddingBaratScreen(
                            repository = repository,
                            initialIsBarat = weddingModeIsBarat
                        )
                        3 -> AdminPanelScreen(repository = repository)
                    }
                }
            }
        }
    }
}
