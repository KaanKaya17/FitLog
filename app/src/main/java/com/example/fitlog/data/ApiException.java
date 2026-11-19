package com.example.fitlog.data;

import java.io.IOException;

import retrofit2.Response;

/** Sunucunun başarısız HTTP yanıtı. */
public class ApiException extends Exception {

    public enum Kind { UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, QUOTA, CLIENT, SERVER }

    public final int httpCode;
    public final Kind kind;

    public ApiException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
        this.kind = kindOf(httpCode);
    }

    public static Kind kindOf(int code) {
        if (code == 401) return Kind.UNAUTHORIZED;
        if (code == 403) return Kind.FORBIDDEN;
        if (code == 404) return Kind.NOT_FOUND;
        if (code == 409) return Kind.CONFLICT;
        if (code == 429) return Kind.QUOTA;
        if (code >= 500) return Kind.SERVER;
        return Kind.CLIENT;
    }

    /** Geçici mi (yeniden denemeye değer mi)? */
    public boolean isTransient() {
        return kind == Kind.SERVER || kind == Kind.UNAUTHORIZED || httpCode == 408;
    }

    /** Başarılıysa gövdeyi döndürür, değilse ApiException fırlatır. */
    public static <T> T bodyOrThrow(Response<T> r) throws ApiException {
        if (r.isSuccessful()) return r.body();
        throw from(r);
    }

    public static void checkOk(Response<?> r) throws ApiException {
        if (!r.isSuccessful()) throw from(r);
    }

    public static ApiException from(Response<?> r) {
        String msg = "HTTP " + r.code();
        try {
            if (r.errorBody() != null) {
                String b = r.errorBody().string();
                if (b != null && !b.isEmpty()) msg = msg + ": " + (b.length() > 300 ? b.substring(0, 300) : b);
            }
        } catch (IOException ignored) {}
        return new ApiException(r.code(), msg);
    }
}
