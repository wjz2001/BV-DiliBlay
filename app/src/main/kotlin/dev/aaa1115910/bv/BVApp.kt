package dev.aaa1115910.bv

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.webkit.WebViewCompat
import de.schnettler.datastore.manager.DataStoreManager
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.BiliHttpProxyApi
import dev.aaa1115910.biliapi.http.util.BiliAppConf
import dev.aaa1115910.biliapi.http.util.BiliWebConf
import dev.aaa1115910.biliapi.repositories.AuthRepository
import dev.aaa1115910.biliapi.repositories.BiliApiModule
import dev.aaa1115910.biliapi.repositories.ChannelRepository
import dev.aaa1115910.bv.block.BlockManager
import dev.aaa1115910.bv.dao.AppDatabase
import dev.aaa1115910.bv.util.LogCatcherUtil
import dev.aaa1115910.bv.util.Prefs
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.logger.Level
import org.koin.plugin.module.dsl.startKoin
import org.slf4j.impl.HandroidLoggerAdapter

/**
 * Koin Compiler Plugin 迁移后：
 * - 不再 import org.koin.ksp.generated.*
 * - 不再使用 AppModule().module
 * - 改用 @KoinApplication + startKoin<T>()
 *
 * 迁移指南：Koin 官方 “KSP to Compiler Plugin” <!--citation:5-->
 */
@KoinApplication(modules = [AppModule::class])
class BVGeneratedApp

class BVApp : Application(), KoinComponent {
    private val channelRepository: ChannelRepository by inject()
    private val authRepository: AuthRepository by inject()

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
            private set

        lateinit var dataStoreManager: DataStoreManager
            private set

        lateinit var koinApplication: org.koin.core.KoinApplication
            private set

        @SuppressLint("StaticFieldLeak")
        var instance: BVApp? = null
            private set

        fun getAppDatabase(context: Context = this.context) = AppDatabase.getDatabase(context)
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
        context = this.applicationContext

        initCoreLibraries()
        Prefs.init()
        AppCompatDelegate.setDefaultNightMode(Prefs.themeMode.toNightMode())
        BlockManager.reloadFromPrefs() // 启动即从本地恢复，保证过滤一直有效

        initDeviceInfo()
        initRepository()
        initProxy()

        BiliHttpApi.init(buvid3 = Prefs.buvid3)
    }

    private fun initCoreLibraries() {
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG
        LogCatcherUtil.installLogCatcher()

        dataStoreManager = DataStoreManager(applicationContext.dataStore)

        // Koin Compiler Plugin：typed API 启动（不再 modules(AppModule().module)）
        koinApplication = startKoin<BVGeneratedApp> {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@BVApp)
        }
    }

    fun initRepository() {
        channelRepository.initDefaultChannel(Prefs.accessToken, Prefs.buvid)

        authRepository.apply {
            sessionData = Prefs.sessData.takeIf { it.isNotEmpty() }
            biliJct = Prefs.biliJct.takeIf { it.isNotEmpty() }
            accessToken = Prefs.accessToken.takeIf { it.isNotEmpty() }
            mid = Prefs.uid.takeIf { it != 0L }
            buvid3 = Prefs.buvid3
            buvid = Prefs.buvid
        }
    }

    fun initProxy() {
        if (!Prefs.enableProxy) return

        BiliHttpProxyApi.createClient(Prefs.proxyHttpServer)

        runCatching {
            channelRepository.initProxyChannel(
                Prefs.accessToken,
                Prefs.buvid,
                Prefs.proxyGRPCServer
            )
        }
    }

    fun initDeviceInfo() {
        BiliAppConf.osVersion = Build.VERSION.RELEASE
        BiliAppConf.model = Build.MODEL
        BiliWebConf.webViewVersion = runCatching {
            WebViewCompat.getCurrentWebViewPackage(applicationContext)
                ?.versionName
                ?.substringBefore(".")
                ?.toInt()
        }.getOrDefault(144) ?: 144
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Settings")

@Module(includes = [BiliApiModule::class])
@ComponentScan("dev.aaa1115910.bv")
class AppModule
