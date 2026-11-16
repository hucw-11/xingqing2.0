package com.shangkeschedule

import com.shangkeschedule.data.di.SharedModule
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication as KoinApplicationConfig
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.koin.plugin.module.dsl.startKoin

@Module(includes = [SharedModule::class])
@ComponentScan("com.shangkeschedule")
class DesktopModule {
    @Single
    @Named("AppVersionCode")
    fun provideAppVersionCode(): Int = DesktopBuildInfo.VERSION_CODE

    @Single
    @Named("AppVersionName")
    fun provideAppVersionName(): String = DesktopBuildInfo.VERSION_NAME
}

@KoinApplicationConfig(modules = [DesktopModule::class])
class DesktopAppConfig

/** 必须在 Compose 创建窗口、获取任何 ViewModel 之前调用。 */
internal fun startDesktopDependencies(): KoinApplication = startKoin<DesktopAppConfig> { }
