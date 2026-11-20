package com.example.fitlog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.fitlog.domain.NutritionCalculator;
import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.ProfileViewModel;
import com.example.fitlog.ui.TargetsDialog;

import java.time.Year;

public class EditProfileFragment extends BaseFragment {

    private static final NutritionCalculator.Activity[] ACTIVITIES = NutritionCalculator.Activity.values();
    private static final int[] ACTIVITY_LABELS = {
            R.string.activity_sedentary, R.string.activity_light, R.string.activity_moderate,
            R.string.activity_active, R.string.activity_very_active
    };

    private EditText etFirstName, etLastName, etHeight, etWeight, etBirthYear, etTargetWeight;
    private Spinner spinnerActivity;
    private LinearLayout cardMale, cardFemale;
    private LinearLayout cardGoalLose, cardGoalMaintain, cardGoalGain;
    private Button btnSave;
    private String selectedGender = "";
    private String selectedGoal = "";
    private ProfileViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(ProfileViewModel.class);
        bindCommon(vm);

        etFirstName = view.findViewById(R.id.etFirstName);
        etLastName  = view.findViewById(R.id.etLastName);
        etHeight    = view.findViewById(R.id.etHeight);
        etWeight    = view.findViewById(R.id.etWeight);
        etBirthYear = view.findViewById(R.id.etBirthYear);
        etTargetWeight = view.findViewById(R.id.etTargetWeight);
        spinnerActivity = view.findViewById(R.id.spinnerActivity);
        cardMale    = view.findViewById(R.id.cardMale);
        cardFemale  = view.findViewById(R.id.cardFemale);
        cardGoalLose = view.findViewById(R.id.cardGoalLose);
        cardGoalMaintain = view.findViewById(R.id.cardGoalMaintain);
        cardGoalGain = view.findViewById(R.id.cardGoalGain);
        btnSave     = view.findViewById(R.id.btnSave);

        String[] labels = new String[ACTIVITY_LABELS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(ACTIVITY_LABELS[i]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerActivity.setAdapter(adapter);

        if (savedInstanceState == null) fillFromPrefs();

        cardMale.setOnClickListener(v -> selectGender("Male"));
        cardFemale.setOnClickListener(v -> selectGender("Female"));
        cardGoalLose.setOnClickListener(v -> selectGoal("lose"));
        cardGoalMaintain.setOnClickListener(v -> selectGoal("maintain"));
        cardGoalGain.setOnClickListener(v -> selectGoal("gain"));
        btnSave.setOnClickListener(v -> saveProfile());

        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> navigateUp());

        vm.saved().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() == null) return;
            btnSave.setEnabled(true);
            toast(R.string.profile_saved);
            NutritionCalculator.Targets t = vm.suggestTargets();
            if (t != null) TargetsDialog.show(requireContext(), t, () -> vm.applyTargets(t), this::navigateUp);
            else navigateUp();
        });
        vm.targetsApplied().observe(getViewLifecycleOwner(), e -> { if (e.consume() != null) navigateUp(); });
        vm.errors().observe(getViewLifecycleOwner(), e -> btnSave.setEnabled(true));
    }

    private void fillFromPrefs() {
        PrefManager pref = new PrefManager(requireContext());
        etFirstName.setText(pref.getFirstName());
        etLastName.setText(pref.getLastName());
        if (pref.getHeight() > 0) etHeight.setText(trim(pref.getHeight()));
        if (pref.getWeight() > 0) etWeight.setText(trim(pref.getWeight()));
        if (pref.getBirthYear() > 0) etBirthYear.setText(String.valueOf(pref.getBirthYear()));
        if (pref.getTargetWeight() > 0) etTargetWeight.setText(trim(pref.getTargetWeight()));
        spinnerActivity.setSelection(NutritionCalculator.Activity.fromKey(pref.getActivityLevel()).ordinal());
        if (!pref.getGender().isEmpty()) selectGender(pref.getGender());
        if (!pref.getGoal().isEmpty()) selectGoal(pref.getGoal());
    }

    private static String trim(float f) {
        return f == Math.rint(f) ? String.valueOf((int) f) : String.valueOf(f);
    }

    private void selectGender(String gender) {
        selectedGender = gender;
        boolean male = "Male".equalsIgnoreCase(gender);
        cardMale.setBackgroundResource(male ? R.drawable.badge_blue : R.drawable.edittext_bg);
        cardFemale.setBackgroundResource(male ? R.drawable.edittext_bg : R.drawable.badge_pink);
    }

    private void selectGoal(String goal) {
        selectedGoal = goal;
        cardGoalLose.setBackgroundResource("lose".equals(goal) ? R.drawable.badge_blue : R.drawable.edittext_bg);
        cardGoalMaintain.setBackgroundResource("maintain".equals(goal) ? R.drawable.badge_blue : R.drawable.edittext_bg);
        cardGoalGain.setBackgroundResource("gain".equals(goal) ? R.drawable.badge_blue : R.drawable.edittext_bg);
    }

    private void saveProfile() {
        ProfileViewModel.ProfileForm f = new ProfileViewModel.ProfileForm();
        f.firstName = etFirstName.getText().toString().trim();
        f.lastName  = etLastName.getText().toString().trim();
        f.gender = selectedGender;
        f.goal = selectedGoal;
        f.activity = ACTIVITIES[spinnerActivity.getSelectedItemPosition()].key();

        if (f.firstName.isEmpty() || f.lastName.isEmpty()) { toast(R.string.error_empty_name); return; }
        if (f.gender.isEmpty()) { toast(R.string.error_select_gender); return; }
        if (f.goal.isEmpty()) { toast(R.string.error_select_goal); return; }

        Float h = parse(etHeight);
        if (h == null || h < 50 || h > 300) { toast(R.string.error_invalid_height); return; }
        Float w = parse(etWeight);
        if (w == null || w < 20 || w > 300) { toast(R.string.error_invalid_weight); return; }
        f.heightCm = h;
        f.weightKg = w;

        String by = etBirthYear.getText().toString().trim();
        if (!by.isEmpty()) {
            int year;
            try { year = Integer.parseInt(by); } catch (NumberFormatException e) { year = -1; }
            int age = Year.now().getValue() - year;
            if (age < 13 || age > 100) { toast(R.string.error_invalid_birth_year); return; }
            f.birthYear = year;
        }
        String tw = etTargetWeight.getText().toString().trim();
        if (!tw.isEmpty()) {
            Float t = parse(etTargetWeight);
            if (t == null || t < 20 || t > 400) { toast(R.string.error_invalid_target_weight); return; }
            f.targetWeight = t;
        }

        btnSave.setEnabled(false);
        vm.save(f);
    }

    private static Float parse(EditText et) {
        try {
            return Float.parseFloat(et.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
