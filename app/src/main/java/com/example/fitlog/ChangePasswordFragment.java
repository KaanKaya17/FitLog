package com.example.fitlog;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class ChangePasswordFragment extends Fragment {

    private EditText editTextCurrentPassword;
    private EditText editTextNewPassword;
    private EditText editTextConfirmPassword;
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
        if (getContext() == null) return;
        String currentPass = editTextCurrentPassword.getText().toString();
        String newPass     = editTextNewPassword.getText().toString();
        String confirmPass = editTextConfirmPassword.getText().toString();

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            toast("Lütfen tüm alanları doldurun.");
            return;
        }
        if (!newPass.equals(confirmPass)) {
            toast("Yeni şifreler eşleşmiyor.");
            return;
        }
        if (!Validator.isValidPassword(newPass)) {
            toast(getString(R.string.error_password_rules));
            return;
        }
        if (newPass.equals(currentPass)) {
            toast("Yeni şifre mevcut şifreyle aynı olamaz.");
            return;
        }
        if (new PrefManager(getContext()).getId() == -1) {
            toast("Kullanıcı bilgisi bulunamadı.");
            return;
        }

        btnUpdatePassword.setEnabled(false);
        Context appCtx = requireContext().getApplicationContext();
        new Thread(() -> {
            String message;
            boolean success = false;
            try {
                // Mevcut şifre sunucuda doğrulanır, yeni şifre Supabase Auth'ta hash'lenir.
                AuthManager.changePassword(appCtx, currentPass, newPass);
                message = "Şifreniz başarıyla güncellendi.";
                success = true;
            } catch (AuthManager.AuthException e) {
                message = e.getMessage();
            } catch (Exception e) {
                Log.e("ChangePassword", "Error", e);
                message = "Bir hata oluştu.";
            }

            final String fMessage = message;
            final boolean fSuccess = success;
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                btnUpdatePassword.setEnabled(true);
                toast(fMessage);
                if (fSuccess) NavHostFragment.findNavController(this).navigateUp();
            });
        }).start();
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
