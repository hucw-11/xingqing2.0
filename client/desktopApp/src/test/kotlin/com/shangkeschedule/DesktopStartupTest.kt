package com.shangkeschedule

import androidx.lifecycle.ViewModelStore
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.model.StartScreen
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.DiaryRepository
import com.shangkeschedule.data.repository.DiaryTagRepository
import com.shangkeschedule.data.repository.MoodRepository
import com.shangkeschedule.tool.DesktopAppDirs
import com.shangkeschedule.ui.agenda.AgendaViewModel
import com.shangkeschedule.ui.diary.DiaryViewModel
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
            // 日记模块依赖：DAO、仓库与 ViewModel 必须都能从真实容器取到
            dependencies.koin.get<DiaryRepository>()
            dependencies.koin.get<MoodRepository>()
            dependencies.koin.get<DiaryTagRepository>()
            viewModels.put("diary", dependencies.koin.get<DiaryViewModel>())
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
