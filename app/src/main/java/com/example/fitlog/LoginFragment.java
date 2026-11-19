package com.example.fitlog;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

import com.example.fitlog.data.AppExecutors;
import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.ErrorMessages;
import com.google.firebase.analytics.FirebaseAnalytics;

public class LoginFragment extends BaseFragment {

    private FirebaseAnalytics analytics;
    private EditText editTextEmail;
    private EditText editTextPassword;
    private View btnLogin;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        analytics = FirebaseAnalytics.getInstance(requireContext());
        editTextEmail    = view.findViewById(R.id.editTextEmail);
        editTextPassword = view.findViewById(R.id.editTextPassword);
        btnLogin         = view.findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
        view.findViewById(R.id.btnGoogle).setOnClickListener(v -> startGoogle());

        View tvForgot = view.findViewById(R.id.tvForgotPassword);
        if (tvForgot != null) tvForgot.setOnClickListener(v -> sendPasswordReset());
        View tvRegister = view.findViewById(R.id.tvGoToRegister);
        if (tvRegister != null) tvRegister.setOnClickListener(v -> navigate(R.id.registerFragment));
    }

    private void attemptLogin() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString();
        if (!Validator.isValidEmail(email)) { toast(R.string.error_invalid_email); return; }
        if (password.isEmpty()) { toast(R.string.error_password_required); return; }

        btnLogin.setEnabled(false);
        Context appCtx = requireContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                User user = AuthManager.signIn(appCtx, email, password);
                AppExecutors.main(() -> onLoggedIn(user, "email"));
            } catch (Exception e) {
                if (!(e instanceof AuthManager.AuthException)) Log.w("LOGIN", "Login failed", e);
                AppExecutors.main(() -> {
                    if (!isAdded()) return;
                    btnLogin.setEnabled(true);
                    ErrorMessages.toast(requireContext(), e);
                });
            }
        });
    }

    private void onLoggedIn(User user, String method) {
        if (!isAdded()) return;
        Bundle b = new Bundle();
        b.putString(FirebaseAnalytics.Param.METHOD, method);
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN, b);
        String name = user.getFirst_name() != null ? user.getFirst_name() : "";
        toast(getString(R.string.login_welcome, name));
        Navigation.toHomeAndClear(requireActivity());
    }

    /** Google girişini Custom Tab'de başlatır; dönüş MainActivity'ye deep link ile gelir. */
    private void startGoogle() {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) { toast(R.string.error_network); return; }
        String url = AuthManager.googleAuthorizeUrl(requireContext());
        try {
            new CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(requireContext(), Uri.parse(url));
        } catch (Exception e) {
            toast(R.string.error_generic);
        }
    }

    private void sendPasswordReset() {
        String email = editTextEmail.getText().toString().trim();
        if (!Validator.isValidEmail(email)) { toast(R.string.error_enter_email_first); return; }
        Context appCtx = requireContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                AuthManager.sendPasswordReset(appCtx, email);
            } catch (Exception e) {
                Log.w("LOGIN", "Password reset request failed", e);
            }
            // Hesap var/yok bilgisini sızdırmamak için her durumda aynı mesaj.
            AppExecutors.main(() -> { if (isAdded()) toast(R.string.password_reset_sent); });
        });
    }
}
