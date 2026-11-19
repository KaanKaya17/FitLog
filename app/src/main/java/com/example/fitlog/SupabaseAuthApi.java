package com.example.fitlog;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;

/** Supabase Auth (GoTrue) uç noktaları. */
public interface SupabaseAuthApi {

    @POST("auth/v1/signup")
    Call<AuthResponse> signUp(@Body SignUpRequest body);

    @POST("auth/v1/token?grant_type=password")
    Call<AuthResponse> signInWithPassword(@Body PasswordGrant body);

    @POST("auth/v1/token?grant_type=refresh_token")
    Call<AuthResponse> refresh(@Body RefreshGrant body);

    @POST("auth/v1/token?grant_type=pkce")
    Call<AuthResponse> exchangeCode(@Body PkceGrant body);

    @POST("auth/v1/recover")
    Call<Void> recover(@Body Map<String, Object> body);

    @PUT("auth/v1/user")
    Call<AuthUser> updateUser(@Header("Authorization") String bearer, @Body Map<String, Object> body);

    @POST("auth/v1/logout")
    Call<Void> logout(@Header("Authorization") String bearer);

    // ── DTO'lar ────────────────────────────────────────────────────────

    class PasswordGrant {
        final String email;
        final String password;
        PasswordGrant(String email, String password) { this.email = email; this.password = password; }
    }

    class PkceGrant {
        @SerializedName("auth_code") final String authCode;
        @SerializedName("code_verifier") final String codeVerifier;
        PkceGrant(String authCode, String codeVerifier) { this.authCode = authCode; this.codeVerifier = codeVerifier; }
    }

    class RefreshGrant {
        @SerializedName("refresh_token") final String refreshToken;
        RefreshGrant(String refreshToken) { this.refreshToken = refreshToken; }
    }

    class SignUpRequest {
        final String email;
        final String password;
        final Map<String, String> data;
        SignUpRequest(String email, String password, Map<String, String> data) {
            this.email = email; this.password = password; this.data = data;
        }
    }

    class AuthUser {
        String id;
        String email;
    }

    class AuthResponse {
        @SerializedName("access_token")  String accessToken;
        @SerializedName("refresh_token") String refreshToken;
        @SerializedName("expires_in")    long expiresIn;
        @SerializedName("expires_at")    long expiresAt;
        AuthUser user;
        // E-posta onayı açıksa signup yanıtı oturum yerine doğrudan kullanıcıyı döndürür.
        String id;
    }
}
