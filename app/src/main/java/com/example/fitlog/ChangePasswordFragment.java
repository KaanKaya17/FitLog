package com.example.fitlog;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        String currentPass = editTextCurrentPassword.getText().toString().trim();
        String newPass     = editTextNewPassword.getText().toString().trim();
        String confirmPass = editTextConfirmPassword.getText().toString().trim();

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(getContext(), "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.equals(confirmPass)) {
            Toast.makeText(getContext(), "Yeni şifreler eşleşmiyor.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Validator.isValidPassword(newPass)) {
            Toast.makeText(getContext(), "Şifre en az 6 karakter olmalıdır.", Toast.LENGTH_SHORT).show();
            return;
        }

        PrefManager prefManager = new PrefManager(getContext());
        int userId = prefManager.getId();
        if (userId == -1) {
            Toast.makeText(getContext(), "Kullanıcı bilgisi bulunamadı.", Toast.LENGTH_SHORT).show();
            return;
        }

        checkCurrentPasswordAndUpdate(userId, currentPass, newPass);
    }

    private void checkCurrentPasswordAndUpdate(int userId, String currentPass, String newPass) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;
                String userIdFilter = "eq." + userId;

                retrofit2.Response<List<User>> response = api.getUserById(apiKey, authHeader, "application/json", userIdFilter).execute();

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    User user = response.body().get(0);
                    if (user.getPassword_hash().equals(currentPass)) {
                        updatePasswordInApi(api, apiKey, authHeader, userIdFilter, newPass);
                    } else {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> {
                            if (isAdded()) Toast.makeText(getContext(), "Mevcut şifreniz yanlış.", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        if (isAdded()) Toast.makeText(getContext(), "Kullanıcı bilgisi alınamadı.", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e("ChangePassword", "Error", e);
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    if (isAdded()) Toast.makeText(getContext(), "Bir hata oluştu.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updatePasswordInApi(SupabaseApi api, String apiKey, String authHeader, String userIdFilter, String newPass) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("password_hash", newPass);

            retrofit2.Response<Void> updateResponse = api.updateUserPassword(apiKey, authHeader, "application/json", "return=minimal", userIdFilter, body).execute();

            if (updateResponse.isSuccessful()) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Şifreniz başarıyla güncellendi.", Toast.LENGTH_SHORT).show();
                        requireActivity().onBackPressed();
                    }
                });
            } else {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    if (isAdded()) Toast.makeText(getContext(), "Şifre güncellenirken hata oluştu.", Toast.LENGTH_SHORT).show();
                });
            }
        } catch (Exception e) {
            Log.e("ChangePassword", "Error updating", e);
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (isAdded()) Toast.makeText(getContext(), "Bir hata oluştu.", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
