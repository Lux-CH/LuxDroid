package ch.cclerc.luxapp

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ch.cclerc.luxapp.core.CacheCleaner
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.NetworkMonitor
import ch.cclerc.luxapp.data.AppDirectories
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.ConnectionService
import ch.cclerc.luxapp.domain.intelligence.DepartureAlertPlanner
import ch.cclerc.luxapp.domain.onboard.OnboardLiveActivityController
import ch.cclerc.luxapp.domain.search.SearchResultVisualStyleStore
import ch.cclerc.luxcom.net.ApiClient
import ch.cclerc.luxcom.relay.RelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LuxApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppDirectories.init(this)
        Settings.init(this)
        HapticFeedback.init(this)
        LocationService.init(this)
        NetworkMonitor.init(this)
        ConnectionService.init(this)
        ApiClient.warmUp()
        OnboardLiveActivityController.endAll(this)
        if (Settings.appLaunchCount % 15 == 0) {
            appScope.launch { CacheCleaner.performCleanup(this@LuxApplication) }
        }
        Settings.appLaunchCount += 1
        DepartureAlertPlanner.init(this)
        DepartureAlertPlanner.refresh()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                RelayClient.shared.onAppForeground()
                DepartureAlertPlanner.refresh()
            }

            override fun onStop(owner: LifecycleOwner) {
                RelayClient.shared.onAppBackground()
                SearchResultVisualStyleStore.flush()
            }
        })
    }
}
