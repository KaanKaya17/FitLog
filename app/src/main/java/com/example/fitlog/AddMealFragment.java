package com.example.fitlog;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.analytics.FirebaseAnalytics;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import static android.app.Activity.RESULT_OK;

public class AddMealFragment extends Fragment {

    private FirebaseAnalytics firebaseAnalytics;
    private EditText etFood;
    private Button btnAnalyze, btnSave;
    private LottieAnimationView lottieAnimationView;
    private LinearLayout cardResult;
    private GeminiNutritionService service;
    private View includeNoInternet;
    private Button btnRetryInternet;
    private LinearLayout layoutInput;

    private ImageButton btnCamera;
    private ImageView ivPreview;
    private android.widget.FrameLayout layoutPreview;
    private ImageButton btnRemoveImage;
    private String base64Image = null;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private TextView tvFoodName, tvServingSize, tvConfidence;
    private TextView tvCalories, tvProtein, tvCarbs, tvFat, tvFiber;

    private NutritionData lastResult = null;
    private String selectedDate = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_meal, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register launchers in onCreate (before onViewCreated)
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                openCamera();
            } else {
                Toast.makeText(getContext(), "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show();
            }
        });

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Bundle extras = result.getData().getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    if (imageBitmap != null) {
                        ivPreview.setImageBitmap(imageBitmap);
                        layoutPreview.setVisibility(View.VISIBLE);
                        base64Image = bitmapToBase64(imageBitmap);
                    }
                }
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getContext() == null) return;

        firebaseAnalytics = FirebaseAnalytics.getInstance(getContext());

        // Get selected_date from arguments if passed
        if (getArguments() != null) {
            selectedDate = getArguments().getString("selected_date");
        }

        etFood      = view.findViewById(R.id.etFood);
        btnAnalyze  = view.findViewById(R.id.btnAnalyze);
        btnSave     = view.findViewById(R.id.btnSave);
        lottieAnimationView = view.findViewById(R.id.lottieAnimationView);
        cardResult  = view.findViewById(R.id.cardResult);
        includeNoInternet = view.findViewById(R.id.includeNoInternet);
        btnRetryInternet  = includeNoInternet.findViewById(R.id.btnRetryInternet);
        layoutInput = view.findViewById(R.id.layoutInput);

        btnRetryInternet.setOnClickListener(v -> checkInternetState());

        btnCamera      = view.findViewById(R.id.btnCamera);
        ivPreview      = view.findViewById(R.id.ivPreview);
        layoutPreview  = view.findViewById(R.id.layoutPreview);
        btnRemoveImage = view.findViewById(R.id.btnRemoveImage);

        btnRemoveImage.setOnClickListener(v -> {
            ivPreview.setImageBitmap(null);
            layoutPreview.setVisibility(View.GONE);
            base64Image = null;
        });

        tvFoodName    = view.findViewById(R.id.tvFoodName);
        tvServingSize = view.findViewById(R.id.tvServingSize);
        tvConfidence  = view.findViewById(R.id.tvConfidence);
        tvCalories    = view.findViewById(R.id.tvCalories);
        tvProtein     = view.findViewById(R.id.tvProtein);
        tvCarbs       = view.findViewById(R.id.tvCarbs);
        tvFat         = view.findViewById(R.id.tvFat);
        tvFiber       = view.findViewById(R.id.tvFiber);

        service = new GeminiNutritionService(getContext());

        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        });

        btnAnalyze.setOnClickListener(v -> {
            String food = etFood.getText().toString().trim();
            if (food.isEmpty() && base64Image == null) {
                Toast.makeText(getContext(), "Please Enter A Meal Or Take a Photo", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!NetworkUtils.isNetworkAvailable(requireContext())) { checkInternetState(); return; }
            startLoading();

            if (base64Image != null) {
                service.analyzeImageFood(base64Image, new NutritionCallback() {
                    @Override public void onSuccess(NutritionData data) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> handleAnalyzeSuccess(data));
                    }
                    @Override public void onError(String errorMessage) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> handleAnalyzeError(errorMessage));
                    }
                });
            } else {
                service.analyzeTextFood(food, new NutritionCallback() {
                    @Override public void onSuccess(NutritionData data) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> handleAnalyzeSuccess(data));
                    }
                    @Override public void onError(String errorMessage) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> handleAnalyzeError(errorMessage));
                    }
                });
            }
        });

        btnSave.setOnClickListener(v -> {
            if (!NetworkUtils.isNetworkAvailable(requireContext())) { checkInternetState(); return; }
            if (lastResult != null) saveMealToSupabase(lastResult);
        });

        checkInternetState();
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        } else {
            Toast.makeText(getContext(), "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
        byte[] byteArray = outputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    private void handleAnalyzeSuccess(NutritionData data) {
        if (!isAdded()) return;
        stopLoading();
        lastResult = data;
        showResult(data);

        Bundle analyticsBundle = new Bundle();
        analyticsBundle.putString("food_name", data.foodName);
        analyticsBundle.putInt("calories", data.calories);
        analyticsBundle.putString("confidence", data.confidence);
        firebaseAnalytics.logEvent("meal_analyzed", analyticsBundle);
    }

    private void handleAnalyzeError(String errorMessage) {
        if (!isAdded()) return;
        stopLoading();
        Toast.makeText(getContext(), "Error: " + errorMessage, Toast.LENGTH_LONG).show();
    }

    private void checkInternetState() {
        if (getContext() == null) return;
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            if (layoutInput != null) layoutInput.setVisibility(View.GONE);
            if (cardResult != null) cardResult.setVisibility(View.GONE);
            if (btnSave != null) btnSave.setVisibility(View.GONE);
            if (lottieAnimationView != null) lottieAnimationView.setVisibility(View.GONE);
            if (includeNoInternet != null) includeNoInternet.setVisibility(View.VISIBLE);
        } else {
            if (includeNoInternet != null) includeNoInternet.setVisibility(View.GONE);
            if (layoutInput != null) layoutInput.setVisibility(View.VISIBLE);
        }
    }

    private void showResult(NutritionData data) {
        tvFoodName.setText(data.foodName);
        tvServingSize.setText(data.servingSize);
        tvCalories.setText(String.valueOf(data.calories));
        tvProtein.setText(data.protein + "g");
        tvCarbs.setText(data.carbohydrates + "g");
        tvFat.setText(data.fat + "g");
        tvFiber.setText(data.fiber + "g");

        tvConfidence.setText(data.confidence);
        if ("high".equalsIgnoreCase(data.confidence)) {
            tvConfidence.setTextColor(requireContext().getColor(R.color.green));
        } else if ("medium".equalsIgnoreCase(data.confidence)) {
            tvConfidence.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark));
        } else {
            tvConfidence.setTextColor(requireContext().getColor(android.R.color.holo_red_dark));
        }

        cardResult.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
    }

    private void startLoading() {
        btnAnalyze.setEnabled(false);
        lottieAnimationView.setVisibility(View.VISIBLE);
        cardResult.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        lastResult = null;
    }

    private void stopLoading() {
        btnAnalyze.setEnabled(true);
        lottieAnimationView.setVisibility(View.GONE);
    }

    private void saveMealToSupabase(NutritionData data) {
        btnSave.setEnabled(false);

        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                PrefManager pref = new PrefManager(requireContext());
                MealInsertDto mealLog;
                if (selectedDate != null) {
                    mealLog = new MealInsertDto(pref.getId(), data, selectedDate + "T12:00:00Z");
                } else {
                    mealLog = new MealInsertDto(pref.getId(), data);
                }

                retrofit2.Response<Void> response = api.insertMealLog(apiKey, authHeader, "application/json", "return=minimal", mealLog).execute();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        btnSave.setEnabled(true);
                        if (response.isSuccessful()) {
                            Toast.makeText(getContext(), "✅ Saved Successfully", Toast.LENGTH_SHORT).show();

                            Bundle saveBundle = new Bundle();
                            saveBundle.putString("food_name", data.foodName);
                            saveBundle.putInt("calories", data.calories);
                            firebaseAnalytics.logEvent("meal_saved", saveBundle);

                            etFood.setText("");
                            if (layoutPreview != null) layoutPreview.setVisibility(View.GONE);
                            if (ivPreview != null) ivPreview.setImageBitmap(null);
                            base64Image = null;
                            cardResult.setVisibility(View.GONE);
                            btnSave.setVisibility(View.GONE);
                            lastResult = null;
                        } else {
                            Toast.makeText(getContext(), "Error While Saving", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        btnSave.setEnabled(true);
                        Toast.makeText(getContext(), "Connection Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }
}
