package com.example.fitlog.ui;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.example.fitlog.R;
import com.example.fitlog.domain.NutritionCalculator;

/** Hesaplanan hedefleri gösterip uygulama onayı ister. */
public final class TargetsDialog {

    private TargetsDialog() {}

    public static void show(Context ctx, NutritionCalculator.Targets t, Runnable onApply, Runnable onDismiss) {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.targets_dialog_title)
                .setMessage(ctx.getString(R.string.targets_dialog_message,
                        t.calories, t.proteinG, t.carbsG, t.fatG, t.waterMl, t.bmr, t.tdee))
                .setPositiveButton(R.string.action_apply, (d, w) -> onApply.run())
                .setNegativeButton(R.string.action_not_now, (d, w) -> { if (onDismiss != null) onDismiss.run(); })
                .setOnCancelListener(d -> { if (onDismiss != null) onDismiss.run(); })
                .show();
    }
}
