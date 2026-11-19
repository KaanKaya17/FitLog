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
        @androidx.annotation.StringRes public final int messageRes;
        AuthException(String code, @androidx.annotation.StringRes int messageRes) {
            super(code);
            this.code = code;
            this.messageRes = messageRes;
        }
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
        return completeSignIn(ctx, r.body());
    }

    // ── Google ile giriş (OAuth + PKCE) ────────────────────────────────

    public static final String OAUTH_REDIRECT = "fitlog://auth-callback";

    /** fitlog://auth-callback dönüşünün hangi akıştan geldiği (PKCE verifier ile saklanır). */
    public static final String FLOW_OAUTH = "oauth";
    public static final String FLOW_RECOVERY = "recovery";
    public static final String FLOW_SIGNUP = "signup";

    /**
     * Şifre sıfırlama bağlantısından gelen geçici oturum. Yeni şifre belirlenene
     * kadar diske yazılmaz; uygulama kapanırsa kullanıcı yeni bağlantı ister.
     */
    private static volatile SupabaseAuthApi.AuthResponse pendingRecovery;

    /** Dönüş işlendiğinde ne olduğunu anlatır. */
    public static final class CallbackResult {
        /** Giriş tamamlandıysa kullanıcı; şifre sıfırlamada null. */
        public final User user;
        public final boolean recovery;
        CallbackResult(User user, boolean recovery) { this.user = user; this.recovery = recovery; }
    }

    /** Bekleyen dönüşün türü; bilinmiyorsa Google girişi kabul edilir. */
    public static String pendingFlow(Context ctx) {
        String f = new PrefManager(ctx).getPkceFlow();
        return f != null ? f : FLOW_OAUTH;
    }

    /**
     * fitlog://auth-callback?code=... dönüşünü işler: Google girişi, e-posta onayı
     * ya da şifre sıfırlama.
     */
    public static CallbackResult handleAuthCallback(Context ctx, String code) throws IOException, AuthException {
        if (FLOW_RECOVERY.equals(pendingFlow(ctx))) {
            pendingRecovery = exchange(ctx, code);
            return new CallbackResult(null, true);
        }
        return new CallbackResult(completeSignIn(ctx, exchange(ctx, code)), false);
    }

    public static boolean hasPendingRecovery() { return pendingRecovery != null; }

    /** Şifre sıfırlama bağlantısıyla gelen oturumla yeni şifreyi ayarlar ve giriş yapar. */
    public static User completePasswordRecovery(Context ctx, String newPassword) throws IOException, AuthException {
        SupabaseAuthApi.AuthResponse session = pendingRecovery;
        if (session == null) throw new AuthException("recovery_missing", R.string.auth_error_link_expired);
        Map<String, Object> body = new HashMap<>();
        body.put("password", newPassword);
        Response<SupabaseAuthApi.AuthUser> r =
                api().updateUser("Bearer " + session.accessToken, body).execute();
        if (!r.isSuccessful()) throw toAuthException(r);
        pendingRecovery = null;
        return completeSignIn(ctx, session);
    }

    /**
     * Tarayıcıda açılacak Google giriş adresini üretir. code_verifier cihazda
     * saklanır; token'lar URL'de taşınmaz, yalnızca tek kullanımlık kod döner.
     */
    public static String googleAuthorizeUrl(Context ctx) {
        String verifier = com.example.fitlog.domain.Pkce.newVerifier();
        new PrefManager(ctx).setPkceVerifier(verifier, FLOW_OAUTH);
        return okhttp3.HttpUrl.get(BuildConfig.SUPABASE_URL + "/auth/v1/authorize").newBuilder()
                .addQueryParameter("provider", "google")
                .addQueryParameter("redirect_to", OAUTH_REDIRECT)
                .addQueryParameter("code_challenge", com.example.fitlog.domain.Pkce.challenge(verifier))
                .addQueryParameter("code_challenge_method", "s256")
                .build().toString();
    }

    /** Dönüşteki tek kullanımlık kodu, cihazdaki verifier ile oturuma çevirir. */
    private static SupabaseAuthApi.AuthResponse exchange(Context ctx, String code) throws IOException, AuthException {
        String verifier = new PrefManager(ctx).takePkceVerifier();
        if (verifier == null || code == null || code.isEmpty()) {
            throw new AuthException("pkce_missing", R.string.auth_error_oauth_expired);
        }
        Response<SupabaseAuthApi.AuthResponse> r =
                api().exchangeCode(new SupabaseAuthApi.PkceGrant(code, verifier)).execute();
        if (!r.isSuccessful() || r.body() == null || r.body().accessToken == null) throw toAuthException(r);
        return r.body();
    }

    private static User completeSignIn(Context ctx, SupabaseAuthApi.AuthResponse session)
            throws IOException, AuthException {
        saveSession(ctx, session);
        String authId = session.user != null ? session.user.id : null;
        User user = fetchProfile(authId);
        if (user == null) {
            clearLocalSession(ctx);
            throw new AuthException("profile_missing", R.string.auth_error_profile_missing);
        }
        if (user.isBlocked()) {
            signOut(ctx);
            throw new AuthException("user_blocked", R.string.auth_error_blocked);
        }
        new PrefManager(ctx).saveUser(user);
        try { com.example.fitlog.data.WeeklySummaryWorker.schedule(ctx); } catch (RuntimeException ignored) {}
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

        // E-posta onayı açıksa onay bağlantısı uygulamaya döner ve oturumu açar.
        String verifier = com.example.fitlog.domain.Pkce.newVerifier();
        PrefManager pref = new PrefManager(ctx);
        pref.setPkceVerifier(verifier, FLOW_SIGNUP);

        Response<SupabaseAuthApi.AuthResponse> r = api().signUp(OAUTH_REDIRECT,
                new SupabaseAuthApi.SignUpRequest(email, password, meta,
                        com.example.fitlog.domain.Pkce.challenge(verifier))).execute();
        if (!r.isSuccessful() || r.body() == null) {
            pref.takePkceVerifier();
            throw toAuthException(r);
        }

        // Kayıt sonrası kullanıcıyı login ekranına yönlendiriyoruz; açık oturumu tutmuyoruz.
        boolean sessionOpened = r.body().accessToken != null;
        if (sessionOpened) pref.takePkceVerifier(); // onay gerekmiyor, bağlantı gelmeyecek
        return sessionOpened;
    }

    /**
     * Şifre sıfırlama e-postası gönderir. E-postadaki bağlantı
     * fitlog://auth-callback?code=... ile uygulamaya döner ve yeni şifre ekranı açılır.
     */
    public static void sendPasswordReset(Context ctx, String email) throws IOException, AuthException {
        String verifier = com.example.fitlog.domain.Pkce.newVerifier();
        new PrefManager(ctx).setPkceVerifier(verifier, FLOW_RECOVERY);
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("code_challenge", com.example.fitlog.domain.Pkce.challenge(verifier));
        body.put("code_challenge_method", "s256");
        Response<Void> r = api().recover(OAUTH_REDIRECT, body).execute();
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
            throw new AuthException("invalid_credentials", R.string.auth_error_current_password);
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
        try { com.example.fitlog.data.WeeklySummaryWorker.cancel(ctx); } catch (RuntimeException ignored) {}
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

        return mapError(code, r.code());
    }

    /** Sunucu hata kodunu yerelleştirilmiş mesaja eşler (test edilebilir). */
    static AuthException mapError(String code, int httpCode) {
        switch (code) {
            case "invalid_credentials":
            case "invalid_grant":
                return new AuthException(code, R.string.auth_error_invalid_credentials);
            case "email_not_confirmed":
                return new AuthException(code, R.string.auth_error_email_not_confirmed);
            case "user_already_exists":
            case "email_exists":
                return new AuthException(code, R.string.auth_error_email_exists);
            case "weak_password":
                return new AuthException(code, R.string.auth_error_weak_password);
            case "over_request_rate_limit":
            case "over_email_send_rate_limit":
                return new AuthException(code, R.string.auth_error_rate_limited);
            case "otp_expired":
                return new AuthException(code, R.string.auth_error_link_expired);
            case "same_password":
                return new AuthException(code, R.string.error_password_same);
            case "flow_state_not_found":
            case "flow_state_expired":
            case "bad_code_verifier":
                return new AuthException(code, R.string.auth_error_oauth_expired);
            default:
                return new AuthException(code.isEmpty() ? "http_" + httpCode : code,
                        httpCode >= 500 ? R.string.error_server : R.string.error_generic);
        }
    }
}
