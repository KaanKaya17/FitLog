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
        View tvForgotPassword = view.findViewById(R.id.tvForgotPassword);
        if (tvForgotPassword != null) tvForgotPassword.setOnClickListener(v -> sendPasswordReset());

        View tvGoToRegister = view.findViewById(R.id.tvGoToRegister);
        if (tvGoToRegister != null) {
            tvGoToRegister.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.registerFragment));
        }
    }

    private void btnLogin() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString();

        if (!Validator.isValidEmail(email)) {
            Toast.makeText(getContext(), "Mail is incorrect!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty()) {
            Toast.makeText(getContext(), "Password is required!", Toast.LENGTH_SHORT).show();
            return;
        }
        loginUser(email, password);
    }

    private void sendPasswordReset() {
        String email = editTextEmail.getText().toString().trim();
        if (!Validator.isValidEmail(email)) {
            Toast.makeText(getContext(), "Önce geçerli bir e-posta adresi girin.", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                AuthManager.sendPasswordReset(email);
            } catch (Exception e) {
                Log.w("LOGIN", "Password reset request failed", e);
            }
            // Hesap var/yok bilgisini sızdırmamak için her durumda aynı mesaj.
            showOnUi("Bu adres kayıtlıysa şifre sıfırlama bağlantısı gönderildi.");
        }).start();
    }

    private void loginUser(String userMail, String userPassword) {
        android.content.Context appCtx = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                // Şifre doğrulaması Supabase Auth sunucusunda yapılır.
                User foundUser = AuthManager.signIn(appCtx, userMail, userPassword);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Bundle loginBundle = new Bundle();
                        loginBundle.putString(FirebaseAnalytics.Param.METHOD, "email");
                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, loginBundle);

                        Toast.makeText(getContext(), "Hoş geldin " + foundUser.getFirst_name(), Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(requireView()).navigate(R.id.homeFragment);
                    });
                }
            } catch (AuthManager.AuthException e) {
                showOnUi(e.getMessage());
            } catch (Exception e) {
                Log.e("LOGIN", "Login failed", e);
                showOnUi("Bağlantı hatası. Lütfen tekrar deneyin.");
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
