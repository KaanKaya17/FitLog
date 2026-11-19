package com.example.fitlog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.example.fitlog.ui.AiCoachViewModel;
import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.ErrorMessages;
import com.google.firebase.analytics.FirebaseAnalytics;

public class AiCoachFragment extends BaseFragment {

    private LottieAnimationView lottieAnimationView;
    private TextView tvLoading;
    private LinearLayout llPlanContainer;
    private NestedScrollView scrollPlan;
    private FrameLayout bottomBar;
    private View includeNoInternet;
    private PrefManager prefManager;
    private FirebaseAnalytics analytics;
    private AiCoachViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_coach, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(AiCoachViewModel.class);
        analytics = FirebaseAnalytics.getInstance(requireContext());
        prefManager = new PrefManager(requireContext());

        lottieAnimationView = view.findViewById(R.id.lottieAnimationView);
        tvLoading       = view.findViewById(R.id.tvLoading);
        llPlanContainer = view.findViewById(R.id.llPlanContainer);
        scrollPlan      = view.findViewById(R.id.scrollPlan);
        bottomBar       = view.findViewById(R.id.bottomBar);
        includeNoInternet = view.findViewById(R.id.includeNoInternet);
        Button btnRegenerate = view.findViewById(R.id.btnRegenerate);

        btnRegenerate.setOnClickListener(v -> start(true));
        view.findViewById(R.id.btnAskCoach).setOnClickListener(v -> navigate(R.id.aiChatFragment));
        includeNoInternet.findViewById(R.id.btnRetryInternet).setOnClickListener(v -> start(false));
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> navigateUp());

        vm.plan().observe(getViewLifecycleOwner(), res -> {
            if (res.isLoading()) { showLoading(true); return; }
            showLoading(false);
            if (res.isError()) {
                ErrorMessages.toast(requireContext(), res.error);
                if (res.data == null) bottomBar.setVisibility(View.VISIBLE);
            }
            if (res.data != null) renderPlanToCards(res.data);
        });

        if (vm.plan().getValue() == null) start(false);
    }

    private void start(boolean regenerate) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) { showNoInternet(); return; }
        if (prefManager.getId() == -1) { toast(R.string.error_not_logged_in); navigateUp(); return; }

        String gender = prefManager.getGender();
        float weight = prefManager.getWeight();
        float height = prefManager.getHeight();
        String goal = prefManager.getGoal();
        if (gender.isEmpty() || weight == 0 || height == 0 || goal.isEmpty()) {
            toast(R.string.ai_error_empty_profile);
            navigateUp();
            return;
        }

        AiCoachViewModel.Prompt prompt = new AiCoachViewModel.Prompt(
                getString(R.string.ai_coach_prompt, prefManager.getFirstName(), gender,
                        String.valueOf(height), String.valueOf(weight), goal),
                getString(R.string.ai_coach_weekly_context),
                prefManager.getCalorieGoal(), prefManager.getWaterGoal());

        if (regenerate) vm.regenerate(prompt); else vm.loadOrGenerate(prompt);

        Bundle b = new Bundle();
        b.putString("goal", goal);
        analytics.logEvent("ai_coach_used", b);
    }

    private void showLoading(boolean isLoading) {
        includeNoInternet.setVisibility(View.GONE);
        lottieAnimationView.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        tvLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        scrollPlan.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    private void showNoInternet() {
        lottieAnimationView.setVisibility(View.GONE);
        tvLoading.setVisibility(View.GONE);
        scrollPlan.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        includeNoInternet.setVisibility(View.VISIBLE);
    }

    private void renderPlanToCards(String markdownText) {
        llPlanContainer.removeAllViews();
        String[] lines = markdownText.split("\n");
        StringBuilder currentContent = new StringBuilder();
        String currentTitle = getString(R.string.ai_coach_default_title);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        boolean hasTitle = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                if (hasTitle || currentContent.length() > 0) {
                    addCard(inflater, currentTitle, currentContent.toString().trim());
                    currentContent.setLength(0);
                }
                currentTitle = trimmed.replaceAll("^#+\\s*", "").replaceAll("\\*\\*", "");
                hasTitle = true;
            } else if (!trimmed.isEmpty()) {
                currentContent.append(line).append("\n");
            }
        }
        if (hasTitle || currentContent.length() > 0) addCard(inflater, currentTitle, currentContent.toString().trim());
    }

    private void addCard(LayoutInflater inflater, String title, String content) {
        if (title.isEmpty() && content.isEmpty()) return;
        View card = inflater.inflate(R.layout.item_ai_coach_card, llPlanContainer, false);
        ((TextView) card.findViewById(R.id.tvCardTitle)).setText(title);
        ((TextView) card.findViewById(R.id.tvCardContent)).setText(
                content.replaceAll("\\*\\*(.*?)\\*\\*", "$1").replaceAll("\\*(.*?)\\*", "$1"));
        llPlanContainer.addView(card);
    }
}
