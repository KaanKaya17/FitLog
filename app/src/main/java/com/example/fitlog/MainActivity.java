package com.example.fitlog;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        //InformationCard.getInstance().init(this);
        //InformationCard.getInstance().show("Kalori Nedir?", "Kalori, besinlerin içerdiği enerji miktarını ölçen birimdir.");

        PrefManager manager = new PrefManager(this);
        Toast.makeText(this, "Id : " + manager.getId(), Toast.LENGTH_LONG).show();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public void navHomePage(View view){
        Intent intent = new Intent(this,AddMeal.class);
        startActivity(intent);
    }

    public void navProfile(View view){
        PrefManager manager = new PrefManager(this);
        int userId = manager.getId();
        if(userId != -1){
            Intent intent = new Intent(this, Profile.class);
            startActivity(intent);
        }
        else{
            Intent intent = new Intent(this,UserRegister.class);
            startActivity(intent);
        }
    }

    public void closeInformation(View view){
        InformationCard.getInstance().hide();
    }

    public void navStatistic(View view){
        Intent intent = new Intent(this,WaterTrack.class);
        startActivity(intent);
    }
}