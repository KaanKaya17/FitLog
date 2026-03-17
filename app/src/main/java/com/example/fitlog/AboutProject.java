package com.example.fitlog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AboutProject extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_about_project);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public void navHomePage(View view) {
        Navigation.toMain(this);
        finish();
    }

    public void navProfile(View view) {
        PrefManager manager = new PrefManager(this);
        int userId = manager.getId();
        if (userId != -1) {
            Navigation.toProfile(this);
        } else {
            Navigation.toRegister(this);
        }
        finish();
    }

    public void navMealList(View view){
        Navigation.toMealList(this);
        finish();
    }

    public void navStatistic(View view){
        Navigation.toHistory(this);
        finish();
    }
    public void btnWebsite(View view){
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://kayakaan.com/"));
        startActivity(intent);
    }
    public void btnGithub(View view){
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KaanKaya17"));
        startActivity(intent);
    }
    public void btnInstagram(View view){
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/kaan.kaya17/"));
        startActivity(intent);
    }
    public void btnLinkedin(View view){
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/kaan-mustafa-kaya-719b60307/"));
        startActivity(intent);
    }
}