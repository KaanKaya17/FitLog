package com.example.fitlog;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.fitlog.data.AppExecutors;
import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.ErrorMessages;

public class ChangePasswordFragment extends BaseFragment {

    private EditText editTextCurrentPassword, editTextNewPassword, editTextConfirmPassword;
    private Button btnUpdatePassword;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_change_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        editTextCurrentPassword = view.findViewById(R.id.editTextCurrentPassword);
        editTextNewPassword = view.findViewById(R.id.editTextNewPassword);
        editTextConfirmPassword = view.findViewById(R.id.editTextConfirmPassword);
        btnUpdatePassword = view.findViewById(R.id.btnUpdatePassword);
        btnUpdatePassword.setOnClickListener(v -> updatePassword());
    }

    private void updatePassword() {
        String currentPass = editTextCurrentPassword.getText().toString();
        String newPass     = editTextNewPassword.getText().toString();
        String confirmPass = editTextConfirmPassword.getText().toString();

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) { toast(R.string.error_fill_all_fields); return; }
        if (!newPass.equals(confirmPass)) { toast(R.string.error_password_mismatch); return; }
        if (!Validator.isValidPassword(newPass)) { toast(R.string.error_password_rules); return; }
        if (newPass.equals(currentPass)) { toast(R.string.error_password_same); return; }
        if (new PrefManager(requireContext()).getId() == -1) { toast(R.string.error_not_logged_in); return; }

        btnUpdatePassword.setEnabled(false);
        Context appCtx = requireContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                // Mevcut şifre sunucuda doğrulanır, yeni şifre Supabase Auth'ta hash'lenir.
                AuthManager.changePassword(appCtx, currentPass, newPass);
                AppExecutors.main(() -> {
                    if (!isAdded()) return;
                    btnUpdatePassword.setEnabled(true);
                    toast(R.string.password_changed);
                    navigateUp();
                });
            } catch (Exception e) {
                if (!(e instanceof AuthManager.AuthException)) Log.w("ChangePassword", "Error", e);
                AppExecutors.main(() -> {
                    if (!isAdded()) return;
                    btnUpdatePassword.setEnabled(true);
                    ErrorMessages.toast(requireContext(), e);
                });
            }
        });
    }
}
