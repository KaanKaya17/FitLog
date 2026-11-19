package com.example.fitlog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.example.fitlog.ui.AccountViewModel;
import com.example.fitlog.ui.BaseFragment;

import java.util.Locale;

public class ProfileFragment extends BaseFragment {

    private TextView tvFullName, tvUsername, tvEmail, tvFirstName, tvLastName;
    private TextView tvHeight, tvWeight, tvGender;
    private AccountViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(AccountViewModel.class);
        bindCommon(vm);

        tvFullName  = view.findViewById(R.id.tvFullName);
        tvUsername  = view.findViewById(R.id.tvUsername);
        tvEmail     = view.findViewById(R.id.tvEmail);
        tvFirstName = view.findViewById(R.id.tvFirstName);
        tvLastName  = view.findViewById(R.id.tvLastName);
        tvHeight    = view.findViewById(R.id.tvHeight);
        tvWeight    = view.findViewById(R.id.tvWeight);
        tvGender    = view.findViewById(R.id.tvGender);

        view.findViewById(R.id.btnEditProfile).setOnClickListener(v -> navigate(R.id.editProfileFragment));
        view.findViewById(R.id.btnChangePassword).setOnClickListener(v -> navigate(R.id.changePasswordFragment));
        view.findViewById(R.id.btnSettings).setOnClickListener(v -> navigate(R.id.settingsFragment));
        view.findViewById(R.id.btnWeight).setOnClickListener(v -> navigate(R.id.weightFragment));

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            vm.logout();
            toast(R.string.msg_logged_out);
            Navigation.toLoginAndClear(requireActivity());
        });

        View btnDelete = view.findViewById(R.id.btnDeleteAccount);
        btnDelete.setOnClickListener(v -> confirmDelete());
        vm.busy().observe(getViewLifecycleOwner(), b -> btnDelete.setEnabled(!Boolean.TRUE.equals(b)));
        vm.deleted().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() == null) return;
            toast(R.string.delete_account_done);
            Navigation.toLoginAndClear(requireActivity());
        });

        loadProfileFromPrefs();
    }

    @Override
    public void onResume() {
        super.onResume();
        UserStatusChecker.check(requireActivity());
        loadProfileFromPrefs();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setPositiveButton(R.string.delete_account_confirm, (d, w) -> vm.deleteAccount())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void loadProfileFromPrefs() {
        if (getContext() == null) return;
        PrefManager pref = new PrefManager(getContext());

        String firstName = pref.getFirstName();
        String lastName  = pref.getLastName();

        tvFullName.setText((firstName + " " + lastName).trim());
        tvUsername.setText("@" + pref.getUsername());
        tvEmail.setText(pref.getEmail());
        tvFirstName.setText(firstName.isEmpty() ? "—" : firstName);
        tvLastName.setText(lastName.isEmpty()  ? "—" : lastName);

        float height = pref.getHeight();
        float weight = pref.getWeight();
        tvHeight.setText(height > 0 ? String.format(Locale.getDefault(), "%.0f cm", height) : "— cm");
        tvWeight.setText(weight > 0 ? String.format(Locale.getDefault(), "%.1f kg", weight) : "— kg");

        String gender = pref.getGender();
        if (gender == null || gender.isEmpty()) tvGender.setText("—");
        else if (gender.equals("Male")) tvGender.setText(R.string.gender_male);
        else if (gender.equals("Female")) tvGender.setText(R.string.gender_female);
        else tvGender.setText(gender);
    }
}
