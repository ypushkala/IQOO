package com.callguard.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.callguard.CallGuardService
import com.callguard.CallGuardState
import com.callguard.alert.AppPrefs
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/**
 * The ambient "am I protected" signal in the phone's own Quick Settings panel: one glance, one tap, same two states as
 * the ongoing notification. Needs no extra permission — the binding to the system is granted by Android itself.
 */
class ProtectionTileService : TileService() {
    override fun onStartListening() { super.onStartListening(); refresh() }

    override fun onClick() {
        super.onClick()
        val on = CallGuardState.state.monitoring
        if (on) startService(Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_STOP))
        else {
            val i = Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = CallGuardState.state.monitoring
        val lang = AppPrefs(this).screenLanguage
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = UiStrings.get(if (on) Ui.TILE_ON else Ui.TILE_OFF, lang)
        tile.updateTile()
    }
}
