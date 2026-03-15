package com.example.fitlog;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class InformationCard {

    private static InformationCard instance;
    private AppCompatActivity activity;

    private InformationCard() {}

    public static InformationCard getInstance() {
        if (instance == null) {
            instance = new InformationCard();
        }
        return instance;
    }

    public InformationCard init(AppCompatActivity activity) {
        this.activity = activity;
        return this;
    }

    public void show(String title, String description) {
        FrameLayout root_layout = activity.findViewById(R.id.root_layout);
        TextView cardTitle = activity.findViewById(R.id.cardTitle);
        TextView infoDescription = activity.findViewById(R.id.informationDescription);

        cardTitle.setText(title);
        infoDescription.setText(description);

        Animation slideInFromTop = AnimationUtils.loadAnimation(activity, R.anim.slide_in_from_top);
        root_layout.setVisibility(View.VISIBLE);
        root_layout.startAnimation(slideInFromTop);
        root_layout.bringToFront();
    }

    public void hide() {
        FrameLayout root_layout = activity.findViewById(R.id.root_layout);

        Animation slideOutToBottom = AnimationUtils.loadAnimation(activity, R.anim.slide_out_to_bottom);
        slideOutToBottom.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                root_layout.setVisibility(View.INVISIBLE);
            }
        });

        root_layout.startAnimation(slideOutToBottom);
    }
}