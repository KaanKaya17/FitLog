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

public class UserRegister extends AppCompatActivity {

    EditText editTextEmail;
    EditText editTextPassword;
    EditText editTextFirstName;
    EditText editTextLastName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // EditText bağlantıları
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextFirstName = findViewById(R.id.editTextFirstName);
        editTextLastName = findViewById(R.id.editTextLastName);
    }

    public void btnAddUser(View view){

        String email = getUserMail();
        String password = getUserPassword();
        String firstName = getUserFirstName();
        String lastName = getUserLastName();

        if(!Validator.isValidEmail(email)){
            showError("Geçerli bir mail giriniz");
            return;
        }

        if(!Validator.isValidPassword(password)){
            showError("Şifre geçersiz");
            return;
        }

        if(!Validator.isValidFirstName(firstName)){
            showError("İsim geçersiz");
            return;
        }

        if(!Validator.isValidLastName(lastName)){
            showError("Soyisim geçersiz");
            return;
        }

        addUser("TestUserName", email, password, firstName, lastName);
        //Toast.makeText(this, "Başarılı", Toast.LENGTH_SHORT).show();
    }

    public String getUserMail(){
        return editTextEmail.getText().toString().trim();
    }

    public String getUserFirstName(){
        return editTextFirstName.getText().toString().trim();
    }

    public String getUserLastName(){
        return editTextLastName.getText().toString().trim();
    }

    public String getUserPassword(){
        return editTextPassword.getText().toString().trim();
    }

    public void showError(String errorMessage){
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    public void addUser(String userName, String userMail, String userPassword, String userFirstName, String userLastName) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                // Önce mail kontrolü
                retrofit2.Response<List<User>> checkResponse = api.getUserByEmail(
                        apiKey,
                        authHeader,
                        "eq." + userMail,
                        "application/json"
                ).execute();

                if (checkResponse.isSuccessful() && checkResponse.body() != null) {
                    if (!checkResponse.body().isEmpty()) {
                        // Mail zaten kayıtlı
                        runOnUiThread(() ->
                                Toast.makeText(this, "Bu mail adresi zaten kayıtlı.", Toast.LENGTH_SHORT).show()
                        );
                        return; // işlemi durdur
                    }
                }

                // Mail yoksa kayıt işlemine devam et
                User newUser = new User(userName, userMail, userPassword, userFirstName, userLastName);

                retrofit2.Response<Void> response = api.insertUser(
                        apiKey,
                        authHeader,
                        "application/json",
                        "return=minimal",
                        newUser
                ).execute();

                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Kayıt başarılı!", Toast.LENGTH_SHORT).show();
                        Navigation.toLogin(this);
                        finish();
                    });
                } else {
                    Log.e("SUPABASE_REGISTER", "Hata: " + response.errorBody().string());
                    runOnUiThread(() ->
                            Toast.makeText(this, "Kayıt sırasında hata oluştu.", Toast.LENGTH_SHORT).show()
                    );
                }

            } catch (Exception e) {
                Log.e("SUPABASE_REGISTER", "Bağlantı Hatası: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(this, "Bağlantı hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    public void btnUserLogin(View view){
        Navigation.toLogin(this);
    }

}