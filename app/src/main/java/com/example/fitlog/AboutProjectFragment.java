package com.example.fitlog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AboutProjectFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about_project, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Social link buttons - using actual IDs from activity_about_project.xml
        setClickIfExists(view, R.id.websiteLink,   "https://kayakaan.com/");
        setClickIfExists(view, R.id.githubLink,    "https://github.com/KaanKaya17");
        setClickIfExists(view, R.id.instagramLink, "https://www.instagram.com/kaan.kaya17/");
        setClickIfExists(view, R.id.linkedinLink,  "https://www.linkedin.com/in/kaan-mustafa-kaya-719b60307/");
    }

    private void setClickIfExists(View root, int viewId, String url) {
        View btn = root.findViewById(viewId);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            });
        }
    }
}
