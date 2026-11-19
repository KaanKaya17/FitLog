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

        // "Login" link
        View tvGoToLogin = view.findViewById(R.id.tvGoToLogin);
        if (tvGoToLogin != null) {
            tvGoToLogin.setOnClickListener(v -> androidx.navigation.Navigation.findNavController(v).navigate(R.id.loginFragment));
        }
    }

    private void btnAddUser() {
        if (getContext() == null) return;
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString();
        String firstName = editTextFirstName.getText().toString().trim();
        String lastName = editTextLastName.getText().toString().trim();
        String userName = editTextUserUserName.getText().toString().trim();

        if (!Validator.isValidEmail(email)) { showError("Geçerli bir mail giriniz"); return; }
        if (!Validator.isValidPassword(password)) { showError(getString(R.string.error_password_rules)); return; }
        if (!Validator.isValidFirstName(firstName)) { showError("İsim geçersiz"); return; }
        if (!Validator.isValidLastName(lastName)) { showError("Soyisim geçersiz"); return; }
        if (!Validator.isValidUserName(userName)) { showError("Kullanıcı adı geçersiz (3-30 karakter: harf, rakam, _ veya .)"); return; }

        addUser(userName, email, password, firstName, lastName);
    }

    private void showError(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void addUser(String userName, String userMail, String userPassword, String userFirstName, String userLastName) {
        android.content.Context appCtx = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                // Şifre Supabase Auth'ta hash'lenerek saklanır; public.users satırını
                // sunucudaki trigger oluşturur.
                AuthManager.signUp(appCtx, userMail, userPassword, userName, userFirstName, userLastName);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Bundle registerBundle = new Bundle();
                        registerBundle.putString(FirebaseAnalytics.Param.METHOD, "email");
                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, registerBundle);
                        Toast.makeText(getContext(),
                                "Kayıt başarılı! E-posta onayı istendiyse gelen kutunuzu kontrol edin.",
                                Toast.LENGTH_LONG).show();
                        Navigation.findNavController(requireView()).navigate(R.id.loginFragment);
                    });
                }
            } catch (AuthManager.AuthException e) {
                showOnUi(e.getMessage());
            } catch (Exception e) {
                Log.e("REGISTER", "Register failed", e);
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
