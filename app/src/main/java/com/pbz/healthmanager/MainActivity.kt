package com.pbz.healthmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import com.pbz.healthmanager.ui.screens.LoginScreen
import com.pbz.healthmanager.ui.screens.MedicineScreen
import com.pbz.healthmanager.ui.screens.DeviceScreen

import com.pbz.healthmanager.ui.screens.ReportScreen
import com.pbz.healthmanager.ui.screens.DietScreen
import com.pbz.healthmanager.ui.theme.Emerald600
import com.pbz.healthmanager.ui.screens.ScanScreen
import com.pbz.healthmanager.ui.screens.MedicineResultScreen

import com.pbz.healthmanager.ui.screens.DietScanScreen
import com.pbz.healthmanager.ui.screens.DishResultScreen

import com.pbz.healthmanager.ui.screens.ClockScreen
import com.pbz.healthmanager.ui.screens.OldLoginScreen
import com.pbz.healthmanager.ui.screens.KidLoginScreen
import com.pbz.healthmanager.ui.screens.OldRegisterScreen
import com.pbz.healthmanager.ui.screens.KidRegisterScreen
import com.pbz.healthmanager.ui.screens.MonitorReviewScreen
import com.pbz.healthmanager.ui.screens.MonitorAccountScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pbz.healthmanager.viewmodel.HealthViewModel
import com.pbz.healthmanager.viewmodel.HealthViewModelFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.pbz.healthmanager.data.remote.pb.LoginTarget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthManagerTheme {
                HealthManagerApp()
            }
        }
    }
}


private enum class AppScreen {
    Main,
    Login,
    OldLogin,
    KidLogin,
    OldRegister,
    KidRegister,
    MonitorReview,
    MonitorAccount,
    Scan,
    MedicineResult,
    DietScan,
    DietResult,
    Clock
}

@Composable
fun HealthManagerApp() {
    val context = LocalContext.current
    val viewModel: HealthViewModel = viewModel(
        factory = HealthViewModelFactory(
            (context.applicationContext as HealthApplication).repository,
            context.applicationContext
        )
    )

    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Login) }
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.Medicine) }

    val captureSuccess by viewModel.captureSuccess.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    // 统一处理识别成功后的跳转逻辑（如果 Screen 内部没处理的话，这里作为兜底或主控）
    LaunchedEffect(captureSuccess) {
        if (captureSuccess) {
            if (currentScreen == AppScreen.Scan) {
                currentScreen = AppScreen.MedicineResult
            } else if (currentScreen == AppScreen.DietScan) {
                currentScreen = AppScreen.DietResult
            }
            viewModel.resetCaptureStatus()
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.Login) {
            viewModel.clearLoginError()
        }
    }

    when (currentScreen) {
        AppScreen.Login -> {
            LoginScreen(
                onElderlyModeClick = { currentScreen = AppScreen.OldLogin },
                onGuardianModeClick = { currentScreen = AppScreen.KidLogin }
            )
        }
        AppScreen.OldLogin -> {
            OldLoginScreen(
                onBackClick = { currentScreen = AppScreen.Login },
                onLoginClick = { account, password ->
                    viewModel.loginByCloud(account, password, LoginTarget.ELDER) {
                        currentScreen = AppScreen.Main
                        selectedTab = BottomTab.Medicine
                    }
                },
                onGoRegister = { currentScreen = AppScreen.OldRegister },
                loginError = loginState.error,
                isLoading = loginState.loading
            )
        }
        AppScreen.KidLogin -> {
            KidLoginScreen(
                onBackClick = { currentScreen = AppScreen.Login },
                onLoginClick = { account, password ->
                    viewModel.loginByCloud(account, password, LoginTarget.GUARDIAN) {
                        currentScreen = AppScreen.MonitorReview
                    }
                },
                onGoRegister = { currentScreen = AppScreen.KidRegister },
                loginError = loginState.error,
                isLoading = loginState.loading
            )
        }
        AppScreen.OldRegister -> {
            OldRegisterScreen(
                onBackClick = { currentScreen = AppScreen.OldLogin },
                onRegisterClick = { account, password ->
                    viewModel.registerByCloud(account, password, LoginTarget.ELDER) {
                        android.widget.Toast.makeText(context, "注册成功，请登录", android.widget.Toast.LENGTH_SHORT).show()
                        currentScreen = AppScreen.OldLogin
                    }
                },
                registerError = loginState.error,
                isLoading = loginState.loading
            )
        }
        AppScreen.KidRegister -> {
            KidRegisterScreen(
                onBackClick = { currentScreen = AppScreen.KidLogin },
                onRegisterClick = { account, password ->
                    viewModel.registerByCloud(account, password, LoginTarget.GUARDIAN) {
                        android.widget.Toast.makeText(context, "注册成功，请登录", android.widget.Toast.LENGTH_SHORT).show()
                        currentScreen = AppScreen.KidLogin
                    }
                },
                registerError = loginState.error,
                isLoading = loginState.loading
            )
        }
        AppScreen.MonitorReview -> {
            MonitorReviewScreen(
                viewModel = viewModel,
                onAccountClick = { currentScreen = AppScreen.MonitorAccount }
            )
        }
        AppScreen.MonitorAccount -> {
            MonitorAccountScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = AppScreen.MonitorReview },
                onLogoutClick = {
                    viewModel.logout()
                    currentScreen = AppScreen.Login
                }
            )
        }
        AppScreen.Main -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.systemBars,
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.navigationBarsPadding(),
                        containerColor = Color.White
                    ) {
                        BottomTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == selectedTab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Emerald600,
                                    selectedTextColor = Emerald600,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = Emerald600.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (selectedTab) {
                        BottomTab.Medicine -> MedicineScreen(
                            viewModel = viewModel,
                            onScanClick = { currentScreen = AppScreen.Scan }
                        )
                        BottomTab.Diet -> DietScreen(
                            viewModel = viewModel,
                            onScanClick = { currentScreen = AppScreen.DietScan },
                            onBackClick = { /* TODO: back navigation */ }
                        )
                        BottomTab.Devices -> DeviceScreen(
                            viewModel = viewModel,
                            onBackClick = { /* TODO: back navigation */ },
                            onUpdateClick = { /* TODO: update devices */ }
                        )
                        BottomTab.Library -> ReportScreen(
                            viewModel = viewModel,
                            onSwitchFamilyClick = {
                                viewModel.logout()
                                currentScreen = AppScreen.Login
                            },
                            onAddMedicineClick = { /* TODO: add medicine */ },
                            onTabChange = { /* TODO: handle tab change */ }
                        )
                    }
                }
            }
        }
        AppScreen.Scan -> {
            ScanScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = AppScreen.Main }
            )
        }
        AppScreen.MedicineResult -> {
            MedicineResultScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = AppScreen.Main },
                onManualSetClick = { currentScreen = AppScreen.Clock }
            )
        }
        AppScreen.DietScan -> {
            DietScanScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = AppScreen.Main }
            )
        }
        AppScreen.DietResult -> {
            DishResultScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = AppScreen.Main }
            )
        }
        AppScreen.Clock -> {
            ClockScreen(
                onBackClick = { currentScreen = AppScreen.MedicineResult },
                healthViewModel = viewModel
            )
        }
    }
}

@Composable
private fun PlaceholderPage(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}

private enum class BottomTab(
    val label: String,
    val icon: ImageVector
) {
    Medicine(
        label = "药品识别",
        icon = Icons.Outlined.MedicalServices
    ),
    Diet(
        label = "饮食建议",
        icon = Icons.Outlined.Restaurant
    ),
    Devices(
        label = "设备管理",
        icon = Icons.Outlined.DevicesOther
    ),
    Library(
        label = "健康库",
        icon = Icons.Outlined.Assessment
    );
}

@Preview(showBackground = true)
@Composable
private fun HealthManagerAppPreview() {
    HealthManagerTheme {
        HealthManagerApp()
    }
}
