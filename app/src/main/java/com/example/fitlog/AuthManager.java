package com.example.fitlog;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

/**
 * Supabase Auth ile oturum yönetimi.
 *
 * Şifreler artık uygulamada ya da public.users tablosunda tutulmaz/karşılaştırılmaz;
 * doğrulama tamamen Supabase Auth sunucusunda yapılır. Uygulama yalnızca
 * access/refresh token'larını şifreli depoda saklar.
 *
 * Bu sınıftaki metotlar ağ çağrısı yapar: ana thread'den ÇAĞIRMAYIN.
 */
public final class AuthManager {

    private static final Object REFRESH_LOCK = new Object();
    private static final long EXPIRY_MARGIN_SECONDS = 60;

    private AuthManager() {}

    /** Kullanıcıya gösterilebilir hata. */
    public static class AuthException extends Exception {
        public final String code;
        AuthException(String code, String message) { super(message); this.code = code; }
    }

    private static SupabaseAuthApi api() {
        return RetrofitClient.getAuthClient().create(SupabaseAuthApi.class);
    }

    // ── Giriş / Kayıt ──────────────────────────────────────────────────

    /** Giriş yapar, oturumu kaydeder ve kullanıcının profil satırını döndürür. */
    public static User signIn(Context ctx, String email, String password) throws IOException, AuthException {
        Response<SupabaseAuthApi.AuthResponse> r =
                api().signInWithPassword(new SupabaseAuthApi.PasswordGrant(email, password)).execute();
        if (!r.isSuccessful() || r.body() == null || r.body().accessToken == null) {
            throw toAuthException(r);
        }
        saveSession(ctx, r.body());

        String authId = r.body().user != null ? r.body().user.id : null;
        User user = fetchProfile(authId);
        if (user == null) {
            clearLocalSession(ctx);
            throw new AuthException("profile_missing", "Profil bulunamadı.");
        }
        if (user.isBlocked()) {
            signOut(ctx);
            throw new AuthException("user_blocked", "Hesabınız engellenmiştir.");
        }
        new PrefManager(ctx).saveUser(user);
        return user;
    }

    /**
     * Kayıt olur. public.users satırı sunucudaki trigger tarafından oluşturulur.
     * @return true: oturum açıldı, false: e-posta onayı bekleniyor.
     */
    public static boolean signUp(Context ctx, String email, String password,
                                 String username, String firstName, String lastName)
            throws IOException, AuthException {
        Map<String, String> meta = new HashMap<>();
        meta.put("username", username);
        meta.put("first_name", firstName);
        meta.put("last_name", lastName);

        Response<SupabaseAuthApi.AuthResponse> r =
                api().signUp(new SupabaseAuthApi.SignUpRequest(email, password, meta)).execute();
        if (!r.isSuccessful() || r.body() == null) throw toAuthException(r);

        // Kayıt sonrası kullanıcıyı login ekranına yönlendiriyoruz; açık oturumu tutmuyoruz.
        return r.body().accessToken != null;
    }

    /** Şifre sıfırlama e-postası gönderir. */
    public static void sendPasswordReset(String email) throws IOException, AuthException {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        Response<Void> r = api().recover(body).execute();
        if (!r.isSuccessful()) throw toAuthException(r);
    }

    /** Mevcut şifreyi sunucuda doğrular, ardından yeni şifreyi ayarlar. */
    public static void changePassword(Context ctx, String currentPassword, String newPassword)
            throws IOException, AuthException {
        PrefManager pref = new PrefManager(ctx);
        String email = pref.getEmail();

        Response<SupabaseAuthApi.AuthResponse> check =
                api().signInWithPassword(new SupabaseAuthApi.PasswordGrant(email, currentPassword)).execute();
        if (!check.isSuccessful() || check.body() == null || check.body().accessToken == null) {
            throw new AuthException("invalid_credentials", "Mevcut şifreniz yanlış.");
        }
        saveSession(ctx, check.body());

        Map<String, Object> body = new HashMap<>();
        body.put("password", newPassword);
        Response<SupabaseAuthApi.AuthUser> r =
                api().updateUser("Bearer " + check.body().accessToken, body).execute();
        if (!r.isSuccessful()) throw toAuthException(r);
    }

    /** Sunucudaki oturumu kapatır (en iyi çaba) ve yerel verileri siler. */
    public static void signOut(Context ctx) {
        String token = new PrefManager(ctx).getAccessToken();
        if (token != null) {
            try { api().logout("Bearer " + token).execute(); } catch (Exception ignored) {}
        }
        clearLocalSession(ctx);
    }

    /** Verilen access token'ın sunucu oturumunu arka planda kapatır. */
    public static void revokeInBackground(String accessToken) {
        if (accessToken == null) return;
        new Thread(() -> {
            try { api().logout("Bearer " + accessToken).execute(); } catch (Exception ignored) {}
        }).start();
    }

    public static void clearLocalSession(Context ctx) {
        new PrefManager(ctx).clearUser();
    }

    public static boolean hasSession(Context ctx) {
        return new PrefManager(ctx).getRefreshToken() != null;
    }

    // ── Token yönetimi ─────────────────────────────────────────────────

    /**
     * Geçerli bir access token döndürür; süresi dolmak üzereyse yeniler.
     * Oturum yoksa ya da yenilenemiyorsa null döner.
     */
    public static String getValidAccessToken(Context ctx) {
        PrefManager pref = new PrefManager(ctx);
        String token = pref.getAccessToken();
        if (token != null && !isExpiring(pref.getTokenExpiresAt())) return token;
        return refreshSession(ctx, token);
    }

    /**
     * Oturumu yeniler. Birden fazla thread aynı anda çağırırsa yalnızca biri
     * yeniler; diğerleri yeni token'ı kullanır.
     * @param staleToken çağıranın elindeki (geçersiz) token
     */
    public static String refreshSession(Context ctx, String staleToken) {
        synchronized (REFRESH_LOCK) {
            PrefManager pref = new PrefManager(ctx);
            String current = pref.getAccessToken();
            if (current != null && !current.equals(staleToken) && !isExpiring(pref.getTokenExpiresAt())) {
                return current; // başka bir thread zaten yeniledi
            }
            String refreshToken = pref.getRefreshToken();
            if (refreshToken == null) return null;
            try {
                Response<SupabaseAuthApi.AuthResponse> r =
                        api().refresh(new SupabaseAuthApi.RefreshGrant(refreshToken)).execute();
                if (r.isSuccessful() && r.body() != null && r.body().accessToken != null) {
                    saveSession(ctx, r.body());
                    return r.body().accessToken;
                }
                if (r.code() == 400 || r.code() == 401) {
                    // Refresh token geçersiz/iptal edilmiş: oturumu kapat.
                    clearLocalSession(ctx);
                }
            } catch (IOException ignored) {
                // Ağ hatası: oturumu silme, sonraki istekte tekrar denenir.
            }
            return null;
        }
    }

    private static boolean isExpiring(long expiresAtEpochSeconds) {
        long now = System.currentTimeMillis() / 1000;
        return expiresAtEpochSeconds - EXPIRY_MARGIN_SECONDS <= now;
    }

    private static void saveSession(Context ctx, SupabaseAuthApi.AuthResponse s) {
        long expiresAt = s.expiresAt > 0
                ? s.expiresAt
                : System.currentTimeMillis() / 1000 + s.expiresIn;
        String authId = s.user != null ? s.user.id : null;
        new PrefManager(ctx).saveSession(s.accessToken, s.refreshToken, expiresAt, authId);
    }

    private static User fetchProfile(String authId) throws IOException {
        if (authId == null) return null;
        SupabaseApi rest = RetrofitClient.getClient().create(SupabaseApi.class);
        Response<List<User>> r = rest.getUserByAuthId("eq." + authId).execute();
        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) return r.body().get(0);
        return null;
    }

    // ── Hata çevirisi ──────────────────────────────────────────────────

    private static AuthException toAuthException(Response<?> r) {
        String code = "";
        String msg = "";
        try {
            if (r.errorBody() != null) {
                JsonObject o = JsonParser.parseString(r.errorBody().string()).getAsJsonObject();
                if (o.has("error_code")) code = o.get("error_code").getAsString();
                else if (o.has("error")) code = o.get("error").getAsString();
                if (o.has("msg")) msg = o.get("msg").getAsString();
                else if (o.has("error_description")) msg = o.get("error_description").getAsString();
            }
        } catch (Exception ignored) {}

        switch (code) {
            case "invalid_credentials":
            case "invalid_grant":
                return new AuthException(code, "E-posta veya şifre hatalı.");
            case "email_not_confirmed":
                return new AuthException(code, "Lütfen önce e-posta adresinizi onaylayın.");
            case "user_already_exists":
            case "email_exists":
                return new AuthException(code, "Bu mail adresi zaten kayıtlı.");
            case "weak_password":
                return new AuthException(code, "Şifre çok zayıf.");
            case "over_request_rate_limit":
            case "over_email_send_rate_limit":
                return new AuthException(code, "Çok fazla deneme yapıldı, lütfen biraz bekleyin.");
            default:
                return new AuthException(code, msg.isEmpty() ? "Bir hata oluştu (" + r.code() + ")." : msg);
        }
    }
}
