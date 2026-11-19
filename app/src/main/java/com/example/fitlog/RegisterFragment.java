package com.example.fitlog;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.fitlog.data.AppExecutors;
import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.ErrorMessages;
import com.google.firebase.analytics.FirebaseAnalytics;

public class RegisterFragment extends BaseFragment {

    private FirebaseAnalytics analytics;
    private EditText editTextEmail, editTextPassword, editTextFirstName, editTextLastName, editTextUserName;
    private View btnAddUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        analytics = FirebaseAnalytics.getInstance(requireContext());

        editTextEmail     = view.findViewById(R.id.editTextEmail);
        editTextPassword  = view.findViewById(R.id.editTextPassword);
        editTextFirstName = view.findViewById(R.id.editTextFirstName);
        editTextLastName  = view.findViewById(R.id.editTextLastName);
        editTextUserName  = view.findViewById(R.id.editTextName);
        btnAddUser        = view.findViewById(R.id.btnAddUser);

        btnAddUser.setOnClickListener(v -> attemptRegister());
        View tvGoToLogin = view.findViewById(R.id.tvGoToLogin);
        if (tvGoToLogin != null) tvGoToLogin.setOnClickListener(v -> navigate(R.id.loginFragment));
    }

    private void attemptRegister() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString();
        String firstName = editTextFirstName.getText().toString().trim();
        String lastName = editTextLastName.getText().toString().trim();
        String userName = editTextUserName.getText().toString().trim();

        if (!Validator.isValidEmail(email)) { toast(R.string.error_invalid_email); return; }
        if (!Validator.isValidPassword(password)) { toast(R.string.error_password_rules); return; }
        if (!Validator.isValidFirstName(firstName)) { toast(R.string.error_invalid_first_name); return; }
        if (!Validator.isValidLastName(lastName)) { toast(R.string.error_invalid_last_name); return; }
        if (!Validator.isValidUserName(userName)) { toast(R.string.error_invalid_username); return; }

        btnAddUser.setEnabled(false);
        Context appCtx = requireContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                // Şifre Supabase Auth'ta hash'lenerek saklanır; public.users satırını trigger oluşturur.
                AuthManager.signUp(appCtx, email, password, userName, firstName, lastName);
                AppExecutors.main(() -> {
                    if (!isAdded()) return;
                    Bundle b = new Bundle();
                    b.putString(FirebaseAnalytics.Param.METHOD, "email");
                    analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, b);
                    toast(R.string.register_success);
                    navigate(R.id.loginFragment);
                });
            } catch (Exception e) {
                if (!(e instanceof AuthManager.AuthException)) Log.w("REGISTER", "Register failed", e);
                AppExecutors.main(() -> {
                    if (!isAdded()) return;
                    btnAddUser.setEnabled(true);
                    ErrorMessages.toast(requireContext(), e);
                });
            }
        });
    }
}
