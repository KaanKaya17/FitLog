package com.example.fitlog;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Profile extends AppCompatActivity {

    private TextView tvFullName, tvUsername, tvEmail, tvFirstName, tvLastName;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        tvFullName  = findViewById(R.id.tvFullName);
        tvUsername  = findViewById(R.id.tvUsername);
        tvEmail     = findViewById(R.id.tvEmail);
        tvFirstName = findViewById(R.id.tvFirstName);
        tvLastName  = findViewById(R.id.tvLastName);

        loadProfileFromPrefs();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        UserStatusChecker.check(this);
    }

    private void loadProfileFromPrefs() {
        PrefManager pref = new PrefManager(this);

        String firstName = pref.getFirstName();
        String lastName  = pref.getLastName();

        tvFullName.setText(firstName + " " + lastName);
        tvUsername.setText("@" + pref.getUsername());
        tvEmail.setText(pref.getEmail());
        tvFirstName.setText(firstName.isEmpty() ? "—" : firstName);
        tvLastName.setText(lastName.isEmpty()  ? "—" : lastName);
    }

    public void btnUserLogOut(View view){
        PrefManager manager = new PrefManager(this);
        manager.clearUser();
        Navigation.toLoginAndClear(this);
    }

    public void navHomePage(View view) {
        Navigation.toMain(this);
        finish();
    }

    public void navProfile(View view) {
        // Zaten Profile'dayız, tekrar açma
    }

    public void btnSettings(View view){
        Navigation.toSettings(this);
    }

    public void navMealList(View view){
        Navigation.toMealList(this);
        finish();
    }

    public void navStatistic(View view){
        Navigation.toHistory(this);
        finish();
    }
}