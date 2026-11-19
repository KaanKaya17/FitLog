package com.example.fitlog;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import com.example.fitlog.data.AppExecutors;
import com.example.fitlog.data.ServiceLocator;
import com.example.fitlog.ui.ErrorMessages;

/** Hızlı ayarlar paneline "+250 ml su" kutucuğu: uygulamayı açmadan su ekler. */
public class WaterTileService extends TileService {

    static final int AMOUNT_ML = 250;

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean loggedIn = new PrefManager(this).getId() != -1 && AuthManager.hasSession(this);
        tile.setState(loggedIn ? Tile.STATE_INACTIVE : Tile.STATE_UNAVAILABLE);
        tile.setLabel(getString(R.string.tile_add_water));
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (new PrefManager(this).getId() == -1 || !AuthManager.hasSession(this)) return;
        android.content.Context app = getApplicationContext();
        AppExecutors.io().execute(() -> {
            String msg;
            try {
                boolean queued = ServiceLocator.repository(app).addWater(AMOUNT_ML).queued;
                msg = queued ? app.getString(R.string.msg_saved_offline)
                        : app.getString(R.string.water_added_format, AMOUNT_ML);
            } catch (Exception e) {
                msg = ErrorMessages.message(app, e);
            }
            final String m = msg;
            AppExecutors.main(() -> Toast.makeText(app, m, Toast.LENGTH_SHORT).show());
        });
    }
}
