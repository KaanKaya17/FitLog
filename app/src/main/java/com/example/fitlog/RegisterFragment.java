package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import java.util.List;

public class RegisterFragment extends Fragment {

    private FirebaseAnalytics firebaseAnalytics;
    private EditText editTextEmail, editTextPassword, editTextFirstName, editTextLastName, editTextUserUserName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getContext() == null) return;

        firebaseAnalytics = FirebaseAnalytics.getInstance(getContext());

        editTextEmail        = view.findViewById(R.id.editTextEmail);
        editTextPassword     = view.findViewById(R.id.editTextPassword);
        editTextFirstName    = view.findViewById(R.id.editTextFirstName);
        editTextLastName     = view.findViewById(R.id.editTextLastName);
        editTextUserUserName = view.findViewById(R.id.editTextName);

        // btnAddUser has android:id in XML
        view.findViewById(R.id.btnAddUser).setOnClickListener(v -> btnAddUser());

        // "Login" link uses android:onClick="btnUserLogin" - handled by MainActivity
        // We don't need to set it here since MainActivity handles it via navigateTo
    }

    private void btnAddUser() {
        if (getContext() == null) return;
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String firstName = editTextFirstName.getText().toString().trim();
        String lastName = editTextLastName.getText().toString().trim();
        String userName = editTextUserUserName.getText().toString().trim();

        if (!Validator.isValidEmail(email)) { showError("Geçerli bir mail giriniz"); return; }
        if (!Validator.isValidPassword(password)) { showError("Şifre geçersiz"); return; }
        if (!Validator.isValidFirstName(firstName)) { showError("İsim geçersiz"); return; }
        if (!Validator.isValidLastName(lastName)) { showError("Soyisim geçersiz"); return; }
        if (!Validator.isValidFirstName(userName)) { showError("Kullanici Adi Gecersiz"); return; }

        addUser(userName, email, password, firstName, lastName);
    }

    private void showError(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void addUser(String userName, String userMail, String userPassword, String userFirstName, String userLastName) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<List<User>> checkResponse = api.getUserByEmail(apiKey, authHeader, "eq." + userMail, "application/json").execute();

                if (checkResponse.isSuccessful() && checkResponse.body() != null && !checkResponse.body().isEmpty()) {
                    showOnUi("Bu mail adresi zaten kayıtlı.");
                    return;
                }

                UserInsertDto newUser = new UserInsertDto(userName, userMail, userPassword, userFirstName, userLastName);
                retrofit2.Response<Void> response = api.insertUser(apiKey, authHeader, "application/json", "return=minimal", newUser).execute();

                if (response.isSuccessful()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            Bundle registerBundle = new Bundle();
                            registerBundle.putString(FirebaseAnalytics.Param.METHOD, "email");
                            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, registerBundle);
                            Toast.makeText(getContext(), "Kayıt başarılı!", Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(requireView()).navigate(R.id.loginFragment);
                        });
                    }
                } else {
                    showOnUi("Kayıt sırasında hata oluştu.");
                }
            } catch (Exception e) {
                Log.e("REGISTER", "Error: " + e.getMessage());
                showOnUi("Bağlantı hatası: " + e.getMessage());
            }
        }).start();
    }

    private void showOnUi(String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (isAdded() && getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            });
        }
    }
}
