package com.shangkeschedule

import androidx.lifecycle.ViewModelStore
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.model.StartScreen
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.tool.DesktopAppDirs
import com.shangkeschedule.ui.agenda.AgendaViewModel
import com.shangkeschedule.ui.settings.SettingsViewModel
import java.awt.GraphicsEnvironment
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopStartupTest {
    @Test
    fun realDesktopContainerLoadsSettingsAndAgendaWithoutOpeningWindows() = runBlocking {
        assertTrue(GraphicsEnvironment.isHeadless(), "启动回归必须在无窗口模式运行")
        val isolatedDataDir = requireNotNull(System.getProperty("xinqing.dataDir"))
        assertEquals(File(isolatedDataDir).canonicalFile, DesktopAppDirs.root.canonicalFile)

        val dependencies = startDesktopDependencies()
        val viewModels = ViewModelStore()
        try {
            // 与 App() 首帧获取相同的真实对象；未启动或缺失绑定均应使测试失败。
            val settings = dependencies.koin.get<SettingsViewModel>()
            viewModels.put("settings", settings)
            viewModels.put("agenda", dependencies.koin.get<AgendaViewModel>())
            val stored = withTimeout(30_000) {
                dependencies.koin.get<AppSettingsRepository>().getAppSettingsOnce()
            }
            assertEquals(StartScreen.AGENDA, stored.startScreen)
            withTimeout(30_000) { settings.startGate.first { it.isReady } }
        } finally {
            viewModels.clear()
            dependencies.koin.get<MainAppDatabase>().close()
            dependencies.close()
        }
        Unit
    }
}
