package com.example.fitlog;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    private TextView tvFullName, tvUsername, tvEmail, tvFirstName, tvLastName;
    private TextView tvHeight, tvWeight, tvGender;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvFullName  = view.findViewById(R.id.tvFullName);
        tvUsername  = view.findViewById(R.id.tvUsername);
        tvEmail     = view.findViewById(R.id.tvEmail);
        tvFirstName = view.findViewById(R.id.tvFirstName);
        tvLastName  = view.findViewById(R.id.tvLastName);
        tvHeight    = view.findViewById(R.id.tvHeight);
        tvWeight    = view.findViewById(R.id.tvWeight);
        tvGender    = view.findViewById(R.id.tvGender);

        view.findViewById(R.id.btnEditProfile).setOnClickListener(v -> 
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.editProfileFragment));
            
        view.findViewById(R.id.btnChangePassword).setOnClickListener(v -> 
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.changePasswordFragment));
            
        view.findViewById(R.id.btnSettings).setOnClickListener(v -> 
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.settingsFragment));
            
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            android.content.Context appCtx = requireContext().getApplicationContext();
            // Sunucudaki oturumu (refresh token) arka planda iptal et, yereli hemen sil.
            String token = new PrefManager(appCtx).getAccessToken();
            AuthManager.clearLocalSession(appCtx);
            AuthManager.revokeInBackground(token);
            Toast.makeText(getContext(), "Çıkış Yapıldı", Toast.LENGTH_SHORT).show();
            Navigation.toLoginAndClear(requireActivity());
        });

        loadProfileFromPrefs();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) UserStatusChecker.check(requireActivity());
        loadProfileFromPrefs();
    }

    private void loadProfileFromPrefs() {
        if (getContext() == null) return;
        PrefManager pref = new PrefManager(getContext());

        String firstName = pref.getFirstName();
        String lastName  = pref.getLastName();

        tvFullName.setText(firstName + " " + lastName);
        tvUsername.setText("@" + pref.getUsername());
        tvEmail.setText(pref.getEmail());
        tvFirstName.setText(firstName.isEmpty() ? "—" : firstName);
        tvLastName.setText(lastName.isEmpty()  ? "—" : lastName);

        float height = pref.getHeight();
        float weight = pref.getWeight();
        String gender = pref.getGender();

        tvHeight.setText((height > 0) ? (height + " cm") : "— cm");
        tvWeight.setText((weight > 0) ? (weight + " kg") : "— kg");

        if (gender == null || gender.isEmpty()) {
            tvGender.setText("—");
        } else if (gender.equals("Male")) {
            tvGender.setText(getString(R.string.gender_male));
        } else if (gender.equals("Female")) {
            tvGender.setText(getString(R.string.gender_female));
        } else {
            tvGender.setText(gender);
        }
    }
}
