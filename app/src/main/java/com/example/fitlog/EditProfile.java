package com.example.fitlog;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class EditProfile extends AppCompatActivity {

    private EditText etFirstName, etLastName, etHeight, etWeight;
    private LinearLayout cardMale, cardFemale;
    private LinearLayout cardGoalLose, cardGoalMaintain, cardGoalGain;
    private Button btnSave;

    private String selectedGender = "";
    private String selectedGoal = "";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName  = findViewById(R.id.etLastName);
        etHeight    = findViewById(R.id.etHeight);
        etWeight    = findViewById(R.id.etWeight);
        cardMale    = findViewById(R.id.cardMale);
        cardFemale  = findViewById(R.id.cardFemale);
        cardGoalLose = findViewById(R.id.cardGoalLose);
        cardGoalMaintain = findViewById(R.id.cardGoalMaintain);
        cardGoalGain = findViewById(R.id.cardGoalGain);
        btnSave     = findViewById(R.id.btnSave);

        // Pre-fill existing data (Name)
        PrefManager pref = new PrefManager(this);
        etFirstName.setText(pref.getFirstName());
        etLastName.setText(pref.getLastName());

        float currentHeight = pref.getHeight();
        if(currentHeight > 0) etHeight.setText(String.valueOf(currentHeight));
        
        float currentWeight = pref.getWeight();
        if(currentWeight > 0) etWeight.setText(String.valueOf(currentWeight));

        String currentGender = pref.getGender();
        if(currentGender != null && !currentGender.isEmpty()) {
            selectGender(currentGender);
        }

        String currentGoal = pref.getGoal();
        if(currentGoal != null && !currentGoal.isEmpty()) {
            selectGoal(currentGoal);
        }

        cardMale.setOnClickListener(v -> selectGender("Male"));
        cardFemale.setOnClickListener(v -> selectGender("Female"));

        cardGoalLose.setOnClickListener(v -> selectGoal("lose"));
        cardGoalMaintain.setOnClickListener(v -> selectGoal("maintain"));
        cardGoalGain.setOnClickListener(v -> selectGoal("gain"));

        btnSave.setOnClickListener(v -> saveProfile());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
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
            case "lose": cardGoalLose.setBackgroundResource(R.drawable.badge_blue); break;
            case "maintain": cardGoalMaintain.setBackgroundResource(R.drawable.badge_blue); break;
            case "gain": cardGoalGain.setBackgroundResource(R.drawable.badge_blue); break;
        }
    }

    private void saveProfile() {
        String fName = etFirstName.getText().toString().trim();
        String lName = etLastName.getText().toString().trim();
        String hStr  = etHeight.getText().toString().trim();
        String wStr  = etWeight.getText().toString().trim();

        if (fName.isEmpty() || lName.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_name), Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedGender.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_select_gender), Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedGoal.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_select_goal), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float height = Float.parseFloat(hStr);
            if (height < 50 || height > 300) {
                Toast.makeText(this, getString(R.string.error_invalid_height), Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.error_invalid_height), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float weight = Float.parseFloat(wStr);
            if (weight < 20 || weight > 300) {
                Toast.makeText(this, getString(R.string.error_invalid_weight), Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.error_invalid_weight), Toast.LENGTH_SHORT).show();
            return;
        }

        float finalHeight = Float.parseFloat(hStr);
        float finalWeight = Float.parseFloat(wStr);

        btnSave.setEnabled(false);
        Toast.makeText(this, "Değişiklikler kaydediliyor...", Toast.LENGTH_SHORT).show();

        PrefManager pref = new PrefManager(this);
        int userId = pref.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("first_name", fName);
        updates.put("last_name", lName);
        updates.put("gender", selectedGender);
        updates.put("goal", selectedGoal);
        updates.put("height_cm", finalHeight);
        updates.put("weight_kg", finalWeight);

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        String apiKey = BuildConfig.SUPABASE_API_KEY;
        String authHeader = "Bearer " + apiKey;
        String userIdFilter = "eq." + userId;

        api.updateUserProfile(apiKey, authHeader, "application/json", "return=minimal", userIdFilter, updates)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        btnSave.setEnabled(true);
                        if(response.isSuccessful()){
                            pref.setFirstName(fName);
                            pref.setLastName(lName);
                            pref.setGender(selectedGender);
                            pref.setGoal(selectedGoal);
                            pref.setHeight(finalHeight);
                            pref.setWeight(finalWeight);

                            Toast.makeText(EditProfile.this, "Profil bilgileri kaydedildi!", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(EditProfile.this, "Kayıt başarısız!", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        btnSave.setEnabled(true);
                        Toast.makeText(EditProfile.this, "Bağlantı hatası!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void btnBack(View view) {
        finish();
    }
}
