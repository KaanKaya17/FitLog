package com.example.fitlog;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class EditProfile extends AppCompatActivity {

    private EditText etFirstName, etLastName, etHeight, etWeight;
    private LinearLayout cardMale, cardFemale;
    private Button btnSave;

    private String selectedGender = "";

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
        btnSave     = findViewById(R.id.btnSave);

        // Pre-fill existing data (Name)
        PrefManager pref = new PrefManager(this);
        etFirstName.setText(pref.getFirstName());
        etLastName.setText(pref.getLastName());

        cardMale.setOnClickListener(v -> selectGender("Male"));
        cardFemale.setOnClickListener(v -> selectGender("Female"));

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

        // Successfully validated!
        Toast.makeText(this, getString(R.string.msg_design_done_db_later), Toast.LENGTH_LONG).show();
        finish();
    }

    public void btnBack(View view) {
        finish();
    }
}
