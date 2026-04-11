package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileFragment extends Fragment {

    private EditText etFirstName, etLastName, etHeight, etWeight;
    private LinearLayout cardMale, cardFemale;
    private LinearLayout cardGoalLose, cardGoalMaintain, cardGoalGain;
    private Button btnSave;
    private String selectedGender = "";
    private String selectedGoal = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getContext() == null) return;

        etFirstName = view.findViewById(R.id.etFirstName);
        etLastName  = view.findViewById(R.id.etLastName);
        etHeight    = view.findViewById(R.id.etHeight);
        etWeight    = view.findViewById(R.id.etWeight);
        cardMale    = view.findViewById(R.id.cardMale);
        cardFemale  = view.findViewById(R.id.cardFemale);
        cardGoalLose = view.findViewById(R.id.cardGoalLose);
        cardGoalMaintain = view.findViewById(R.id.cardGoalMaintain);
        cardGoalGain = view.findViewById(R.id.cardGoalGain);
        btnSave     = view.findViewById(R.id.btnSave);

        PrefManager pref = new PrefManager(getContext());
        etFirstName.setText(pref.getFirstName());
        etLastName.setText(pref.getLastName());

        float currentHeight = pref.getHeight();
        if (currentHeight > 0) etHeight.setText(String.valueOf(currentHeight));
        float currentWeight = pref.getWeight();
        if (currentWeight > 0) etWeight.setText(String.valueOf(currentWeight));

        String currentGender = pref.getGender();
        if (currentGender != null && !currentGender.isEmpty()) selectGender(currentGender);
        String currentGoal = pref.getGoal();
        if (currentGoal != null && !currentGoal.isEmpty()) selectGoal(currentGoal);

        cardMale.setOnClickListener(v -> selectGender("Male"));
        cardFemale.setOnClickListener(v -> selectGender("Female"));
        cardGoalLose.setOnClickListener(v -> selectGoal("lose"));
        cardGoalMaintain.setOnClickListener(v -> selectGoal("maintain"));
        cardGoalGain.setOnClickListener(v -> selectGoal("gain"));
        btnSave.setOnClickListener(v -> saveProfile());

        // Back button
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void selectGender(String gender) {
        selectedGender = gender;
        if (gender.equals("Male")) {
            cardMale.setBackgroundResource(R.drawable.badge_blue);
            cardFemale.setBackgroundResource(R.drawable.edittext_bg);
        } else {
            cardFemale.setBackgroundResource(R.drawable.badge_pink);
            cardMale.setBackgroundResource(R.drawable.edittext_bg);
        }
    }

    private void selectGoal(String goal) {
        selectedGoal = goal;
        cardGoalLose.setBackgroundResource(R.drawable.edittext_bg);
        cardGoalMaintain.setBackgroundResource(R.drawable.edittext_bg);
        cardGoalGain.setBackgroundResource(R.drawable.edittext_bg);
        switch (goal) {
            case "lose":     cardGoalLose.setBackgroundResource(R.drawable.badge_blue); break;
            case "maintain": cardGoalMaintain.setBackgroundResource(R.drawable.badge_blue); break;
            case "gain":     cardGoalGain.setBackgroundResource(R.drawable.badge_blue); break;
        }
    }

    private void saveProfile() {
        if (getContext() == null) return;
        String fName = etFirstName.getText().toString().trim();
        String lName = etLastName.getText().toString().trim();
        String hStr  = etHeight.getText().toString().trim();
        String wStr  = etWeight.getText().toString().trim();

        if (fName.isEmpty() || lName.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.error_empty_name), Toast.LENGTH_SHORT).show(); return;
        }
        if (selectedGender.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.error_select_gender), Toast.LENGTH_SHORT).show(); return;
        }
        if (selectedGoal.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.error_select_goal), Toast.LENGTH_SHORT).show(); return;
        }
        try {
            float h = Float.parseFloat(hStr);
            if (h < 50 || h > 300) { Toast.makeText(getContext(), getString(R.string.error_invalid_height), Toast.LENGTH_SHORT).show(); return; }
        } catch (NumberFormatException e) { Toast.makeText(getContext(), getString(R.string.error_invalid_height), Toast.LENGTH_SHORT).show(); return; }
        try {
            float w = Float.parseFloat(wStr);
            if (w < 20 || w > 300) { Toast.makeText(getContext(), getString(R.string.error_invalid_weight), Toast.LENGTH_SHORT).show(); return; }
        } catch (NumberFormatException e) { Toast.makeText(getContext(), getString(R.string.error_invalid_weight), Toast.LENGTH_SHORT).show(); return; }

        float finalHeight = Float.parseFloat(hStr);
        float finalWeight = Float.parseFloat(wStr);
        btnSave.setEnabled(false);

        PrefManager pref = new PrefManager(getContext());
        int userId = pref.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("first_name", fName); updates.put("last_name", lName);
        updates.put("gender", selectedGender); updates.put("goal", selectedGoal);
        updates.put("height_cm", finalHeight); updates.put("weight_kg", finalWeight);

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        String apiKey = BuildConfig.SUPABASE_API_KEY;

        api.updateUserProfile(apiKey, "Bearer " + apiKey, "application/json", "return=minimal", "eq." + userId, updates)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!isAdded()) return;
                        btnSave.setEnabled(true);
                        if (response.isSuccessful()) {
                            pref.setFirstName(fName); pref.setLastName(lName);
                            pref.setGender(selectedGender); pref.setGoal(selectedGoal);
                            pref.setHeight(finalHeight); pref.setWeight(finalWeight);
                            Toast.makeText(getContext(), "Profil bilgileri kaydedildi!", Toast.LENGTH_SHORT).show();
                            requireActivity().onBackPressed();
                        } else {
                            Toast.makeText(getContext(), "Kayıt başarısız!", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        if (!isAdded()) return;
                        btnSave.setEnabled(true);
                        Toast.makeText(getContext(), "Bağlantı hatası!", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
