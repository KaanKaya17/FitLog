package com.example.fitlog;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

public class UserLogin extends AppCompatActivity {

    EditText editTextEmail;
    EditText editTextPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
    }

    public void btnUserRegister(View view){
        Navigation.toRegister(this);
    }

    public void btnLogin(View view){
        if(!Validator.isValidEmail(getUserEmail())){
            showError("Mail is incorrect!");
            return;
        }

        if(!Validator.isValidPassword(getUserPassword())){
            showError("Password format is incorrect!");
            return;
        }

        checkUserInformation();
    }

    public String getUserPassword(){
        return editTextPassword.getText().toString().trim();
    }

    public String getUserEmail(){
        return editTextEmail.getText().toString().trim();
    }

    public void showError(String errorMessage){
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    public void checkUserInformation(){
        loginUser(getUserEmail(),getUserPassword());
    }

    public void loginUser(String userMail, String userPassword) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;
                String emailFilter = "eq." + userMail;

                retrofit2.Response<List<User>> response = api.getUserByEmail(
                        apiKey,
                        authHeader,
                        emailFilter,
                        "application/json"
                ).execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<User> users = response.body();

                    if (users.isEmpty()) {
                        runOnUiThread(() ->
                                Toast.makeText(this, "Bu mail adresi kayıtlı değil.", Toast.LENGTH_SHORT).show()
                        );
                        return;
                    }

                    User foundUser = users.get(0);

                    if (foundUser.getPassword_hash().equals(userPassword)) {
                        // Giriş başarılı
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Hoş geldin " + foundUser.getFirst_name(), Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.putExtra("username", foundUser.getUsername());
                            int userId = foundUser.getId(); //getid
                            PrefManager manager = new PrefManager(this);
                            manager.saveUser(foundUser);
                            startActivity(intent);
                            finish();
                        });
                    } else {
                        runOnUiThread(() ->
                                Toast.makeText(this, "Şifre hatalı.", Toast.LENGTH_SHORT).show()
                        );
                    }

                } else {
                    Log.e("SUPABASE_LOGIN", "Hata: " + response.errorBody().string());
                    runOnUiThread(() ->
                            Toast.makeText(this, "Bir hata oluştu.", Toast.LENGTH_SHORT).show()
                    );
                }

            } catch (Exception e) {
                Log.e("SUPABASE_LOGIN", "Bağlantı Hatası: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(this, "Bağlantı hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}