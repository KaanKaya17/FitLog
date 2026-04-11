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

public class LoginFragment extends Fragment {

    private FirebaseAnalytics firebaseAnalytics;
    private EditText editTextEmail;
    private EditText editTextPassword;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getContext() == null) return;

        firebaseAnalytics = FirebaseAnalytics.getInstance(getContext());
        editTextEmail    = view.findViewById(R.id.editTextEmail);
        editTextPassword = view.findViewById(R.id.editTextPassword);

        // btnLogin has android:id in XML
        view.findViewById(R.id.btnLogin).setOnClickListener(v -> btnLogin());

        // "Register" link uses android:onClick="btnUserRegister" - handled by MainActivity
        // But we also set click on tvGoToRegister (the actual ID in XML)
        View tvGoToRegister = view.findViewById(R.id.tvGoToRegister);
        if (tvGoToRegister != null) {
            tvGoToRegister.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.registerFragment));
        }
    }

    private void btnLogin() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (!Validator.isValidEmail(email)) {
            Toast.makeText(getContext(), "Mail is incorrect!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Validator.isValidPassword(password)) {
            Toast.makeText(getContext(), "Password format is incorrect!", Toast.LENGTH_SHORT).show();
            return;
        }
        loginUser(email, password);
    }

    private void loginUser(String userMail, String userPassword) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<List<User>> response = api.getUserByEmail(apiKey, authHeader, "eq." + userMail, "application/json").execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<User> users = response.body();
                    if (users.isEmpty()) {
                        showOnUi("Bu mail adresi kayıtlı değil.");
                        return;
                    }
                    User foundUser = users.get(0);
                    if (foundUser.getPassword_hash().equals(userPassword)) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Bundle loginBundle = new Bundle();
                                loginBundle.putString(FirebaseAnalytics.Param.METHOD, "email");
                                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, loginBundle);

                                Toast.makeText(getContext(), "Hoş geldin " + foundUser.getFirst_name(), Toast.LENGTH_SHORT).show();
                                PrefManager manager = new PrefManager(getContext());
                                manager.saveUser(foundUser);
                                // Navigate to home
                                Navigation.findNavController(requireView()).navigate(R.id.homeFragment);
                            });
                        }
                    } else {
                        showOnUi("Şifre hatalı.");
                    }
                } else {
                    showOnUi("Bir hata oluştu.");
                }
            } catch (Exception e) {
                Log.e("LOGIN", "Error: " + e.getMessage());
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
