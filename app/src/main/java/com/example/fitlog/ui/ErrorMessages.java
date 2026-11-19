package com.example.fitlog.ui;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.StringRes;

import com.example.fitlog.AuthManager;
import com.example.fitlog.R;
import com.example.fitlog.data.ApiException;

import java.io.IOException;

/** Hataları kullanıcıya gösterilecek yerelleştirilmiş mesajlara çevirir. */
public final class ErrorMessages {

    private ErrorMessages() {}

    @StringRes
    public static int of(Throwable t) {
        if (t instanceof AuthManager.AuthException) return ((AuthManager.AuthException) t).messageRes;
        if (t instanceof IOException) return R.string.error_network;
        if (t instanceof ApiException) {
            if (((ApiException) t).httpCode == 422) return R.string.ai_error_response;
            switch (((ApiException) t).kind) {
                case UNAUTHORIZED: return R.string.error_session_expired;
                case FORBIDDEN:    return R.string.error_forbidden;
                case QUOTA:        return R.string.error_ai_quota;
                case NOT_FOUND:    return R.string.error_not_found;
                case SERVER:       return R.string.error_server;
                default:           return R.string.error_generic;
            }
        }
        if (t instanceof IllegalStateException) return R.string.error_not_logged_in;
        return R.string.error_generic;
    }

    public static String message(Context ctx, Throwable t) {
        return ctx.getString(of(t));
    }

    public static void toast(Context ctx, Throwable t) {
        if (ctx != null) Toast.makeText(ctx, message(ctx, t), Toast.LENGTH_SHORT).show();
    }
}
