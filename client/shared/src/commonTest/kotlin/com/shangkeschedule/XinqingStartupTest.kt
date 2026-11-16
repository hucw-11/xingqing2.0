package com.shangkeschedule

import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.StartScreen
import kotlin.test.Test
import kotlin.test.assertEquals

class XinqingStartupTest {
    @Test
    fun newInstallationOpensCombinedAgenda() {
        assertEquals(StartScreen.AGENDA, AppSettingsModel().startScreen)
        assertEquals(StartScreen.AGENDA, StartScreen.fromString(null))
        assertEquals(StartScreen.AGENDA, StartScreen.fromString("unknown"))
    }

    @Test
    fun explicitStartPagePreferencesRemainReadable() {
        StartScreen.entries.forEach { screen ->
            assertEquals(screen, StartScreen.fromString(screen.value))
        }
    }

}
