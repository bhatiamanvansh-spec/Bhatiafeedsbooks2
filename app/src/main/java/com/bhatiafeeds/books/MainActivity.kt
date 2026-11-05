package com.bhatiafeeds.books

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bhatiafeeds.books.ui.AppTheme
import com.bhatiafeeds.books.ui.CustomerDetailScreen
import com.bhatiafeeds.books.ui.CustomersScreen
import com.bhatiafeeds.books.ui.DataScreen
import com.bhatiafeeds.books.ui.DriversScreen
import com.bhatiafeeds.books.ui.HomeScreen
import com.bhatiafeeds.books.ui.MoreScreen
import com.bhatiafeeds.books.ui.Nav
import com.bhatiafeeds.books.ui.PaymentFormScreen
import com.bhatiafeeds.books.ui.PendingFormScreen
import com.bhatiafeeds.books.ui.PaymentsScreen
import com.bhatiafeeds.books.ui.ProductsScreen
import com.bhatiafeeds.books.ui.ReportsScreen
import com.bhatiafeeds.books.ui.SaleDetailScreen
import com.bhatiafeeds.books.ui.CompaniesScreen
import com.bhatiafeeds.books.ui.CompanyPaymentFormScreen
import com.bhatiafeeds.books.ui.CompanyScreen
import com.bhatiafeeds.books.ui.DoDetailScreen
import com.bhatiafeeds.books.ui.DoFormScreen
import com.bhatiafeeds.books.ui.SalesScreen
import com.bhatiafeeds.books.ui.Screen
import com.bhatiafeeds.books.ui.SettingsScreen
import com.bhatiafeeds.books.ui.TrashScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderWorker.schedule(this)
        setContent {
            AppTheme { Root(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDay()
    }
}

private val tabs = listOf(
    "Home" to Icons.Filled.Home,
    "Sales" to Icons.Filled.ShoppingCart,
    "Payments" to Icons.Filled.CheckCircle,
    "Customers" to Icons.Filled.Person,
    "More" to Icons.Filled.Menu
)

@Composable
private fun Root(vm: MainViewModel) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Screen>() }

    val nav = remember {
        Nav(
            push = { stack.add(it) },
            pop = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
            goTab = { tab = it; stack.clear() },
            message = { m ->
                scope.launch {
                    snack.currentSnackbarData?.dismiss()
                    snack.showSnackbar(m)
                }
            },
            undo = { m, action ->
                scope.launch {
                    snack.currentSnackbarData?.dismiss()
                    val r = snack.showSnackbar(m, actionLabel = "Undo", duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) action()
                }
            }
        )
    }

    // Ask once for permission to show payment reminders (Android 13+).
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !vm.prefs.askedNotificationPermission) {
            vm.prefs.askedNotificationPermission = true
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BackHandler(enabled = stack.isNotEmpty()) { nav.pop() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (stack.isEmpty()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { nav.goTab(i) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val top = stack.lastOrNull()
            if (top == null) {
                when (tab) {
                    0 -> HomeScreen(vm, nav)
                    1 -> SalesScreen(vm, nav)
                    2 -> PaymentsScreen(vm, nav)
                    3 -> CustomersScreen(vm, nav)
                    else -> MoreScreen(nav)
                }
            } else {
                key(stack.size, top) {
                    when (top) {
                        is Screen.SaleDetail -> SaleDetailScreen(vm, nav, top.id)
                        is Screen.DoForm -> DoFormScreen(vm, nav, top.doNumber, top.companyId, top.customerId)
                        is Screen.PendingForm -> PendingFormScreen(vm, nav, top.id)
                        is Screen.DoDetail -> DoDetailScreen(vm, nav, top.doNumber, top.companyId)
                        is Screen.CompanyPaymentForm -> CompanyPaymentFormScreen(vm, nav, top.id, top.companyId)
                        is Screen.Company -> CompanyScreen(vm, nav, top.id)
                        Screen.Companies -> CompaniesScreen(vm, nav)
                        is Screen.PaymentForm -> PaymentFormScreen(vm, nav, top.id, top.customerId)
                        is Screen.CustomerDetail -> CustomerDetailScreen(vm, nav, top.id)
                        Screen.Products -> ProductsScreen(vm, nav)
                        Screen.Drivers -> DriversScreen(vm, nav)
                        Screen.Reports -> ReportsScreen(vm, nav)
                        Screen.Trash -> TrashScreen(vm, nav)
                        Screen.Data -> DataScreen(vm, nav)
                        Screen.Settings -> SettingsScreen(vm, nav)
                    }
                }
            }
        }
    }
}
