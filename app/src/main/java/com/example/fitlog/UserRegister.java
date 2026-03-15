package com.example.fitlog;

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
        Toast.makeText(this, "Başarılı", Toast.LENGTH_SHORT).show();
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

    public void addUser(String userName,String userMail,String userPassword,String userFirstName, String userLastName){
        new Thread(() -> {
            try {
                // Retrofit örneğini oluştur
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

                // Kendi API anahtarını buraya yapıştır
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                User newUser = new User(userName,userMail,userPassword,userFirstName,userLastName);

                // Retrofit isteğini senkron çalıştır (Test için)
                retrofit2.Response<Void> response = api.insertUser(
                        apiKey,
                        authHeader,
                        "application/json",
                        "return=minimal",
                        newUser
                ).execute();

                if (response.isSuccessful()) {
                    Log.d("SUPABASE_TEST", "Başarılı! Veri veritabanına eklendi.");
                } else {
                    Log.e("SUPABASE_TEST", "Hata: " + response.errorBody().string());
                }
            } catch (Exception e) {
                Log.e("SUPABASE_TEST", "Bağlantı Hatası: " + e.getMessage());
            }
        }).start();
    }


}