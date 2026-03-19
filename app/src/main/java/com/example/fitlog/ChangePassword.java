package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangePassword extends AppCompatActivity {

    private EditText editTextCurrentPassword;
    private EditText editTextNewPassword;
    private EditText editTextConfirmPassword;
    private Button btnUpdatePassword;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editTextCurrentPassword = findViewById(R.id.editTextCurrentPassword);
        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);

        btnUpdatePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatePassword();
            }
        });
    }

    private void updatePassword() {
        String currentPass = editTextCurrentPassword.getText().toString().trim();
        String newPass = editTextNewPassword.getText().toString().trim();
        String confirmPass = editTextConfirmPassword.getText().toString().trim();

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPass.equals(confirmPass)) {
            Toast.makeText(this, "Yeni şifreler eşleşmiyor.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Validator.isValidPassword(newPass)) {
            Toast.makeText(this, "Şifre en az 6 karakter olmalıdır.", Toast.LENGTH_SHORT).show();
            return;
        }

        PrefManager prefManager = new PrefManager(this);
        int userId = prefManager.getId();
        if (userId == -1) {
            Toast.makeText(this, "Kullanıcı bilgisi bulunamadı. Lütfen tekrar giriş yapın.", Toast.LENGTH_SHORT).show();
            return;
        }

        checkCurrentPasswordAndUpdate(userId, currentPass, newPass);
    }

    private void checkCurrentPasswordAndUpdate(int userId, String currentPass, String newPass) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;
                String userIdFilter = "eq." + userId;

                retrofit2.Response<List<User>> response = api.getUserById(
                        apiKey,
                        authHeader,
                        "application/json",
                        userIdFilter
                ).execute();

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    User user = response.body().get(0);
                    if (user.getPassword_hash().equals(currentPass)) {
                        // Current password is correct, proceed to update
                        updatePasswordInApi(api, apiKey, authHeader, userIdFilter, newPass);
                    } else {
                        runOnUiThread(() -> Toast.makeText(ChangePassword.this, "Mevcut şifreniz yanlış.", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(ChangePassword.this, "Kullanıcı bilgisi alınamadı.", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e("ChangePassword", "Error checking password", e);
                runOnUiThread(() -> Toast.makeText(ChangePassword.this, "Bir hata oluştu.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updatePasswordInApi(SupabaseApi api, String apiKey, String authHeader, String userIdFilter, String newPass) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("password_hash", newPass);

            retrofit2.Response<Void> updateResponse = api.updateUserPassword(
                    apiKey,
                    authHeader,
                    "application/json",
                    "return=minimal",
                    userIdFilter,
                    body
            ).execute();

            if (updateResponse.isSuccessful()) {
                runOnUiThread(() -> {
                    Toast.makeText(ChangePassword.this, "Şifreniz başarıyla güncellendi.", Toast.LENGTH_SHORT).show();
                    finish(); // Or navigate to Profile
                });
            } else {
                runOnUiThread(() -> Toast.makeText(ChangePassword.this, "Şifre güncellenirken hata oluştu.", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e("ChangePassword", "Error updating password", e);
            runOnUiThread(() -> Toast.makeText(ChangePassword.this, "Bir hata oluştu.", Toast.LENGTH_SHORT).show());
        }
    }
}