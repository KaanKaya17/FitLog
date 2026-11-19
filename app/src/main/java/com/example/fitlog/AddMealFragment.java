package com.example.fitlog;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.ViewModelProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.example.fitlog.data.AppExecutors;
import com.example.fitlog.domain.FoodMath;
import com.example.fitlog.domain.MealType;
import com.example.fitlog.ui.AddMealViewModel;
import com.example.fitlog.ui.BaseFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class AddMealFragment extends BaseFragment {

    private static final int MAX_IMAGE_SIDE = 1024;

    private AddMealViewModel vm;
    private FirebaseAnalytics analytics;

    private EditText etFood;
    private Button btnAnalyze, btnSave;
    private MaterialButton btnFavorite;
    private LottieAnimationView lottie;
    private LinearLayout cardResult, layoutInput, layoutQuickAdd, layoutQuickAddSection;
    private View includeNoInternet;
    private ImageView ivPreview;
    private FrameLayout layoutPreview;
    private TextView tvFoodName, tvServingSize, tvConfidence;
    private TextView tvCalories, tvProtein, tvCarbs, tvFat, tvFiber;
    private TextView[] typeChips, portionChips;
    private static final double[] PORTIONS = {0.5, 1.0, 1.5, 2.0};

    private String base64Image;
    private Uri photoUri;
    private LocalDate selectedDate;

    private ActivityResultLauncher<Uri> takePicture;
    private ActivityResultLauncher<String> cameraPermission;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_meal, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) openCamera(); else toast(R.string.add_meal_camera_permission);
        });
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && photoUri != null) loadPhoto(photoUri);
        });
        if (savedInstanceState != null) {
            String uri = savedInstanceState.getString("photo_uri");
            if (uri != null) photoUri = Uri.parse(uri);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (photoUri != null) outState.putString("photo_uri", photoUri.toString());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(AddMealViewModel.class);
        bindCommon(vm);
        analytics = FirebaseAnalytics.getInstance(requireContext());

        if (getArguments() != null && getArguments().getString("selected_date") != null) {
            try { selectedDate = LocalDate.parse(getArguments().getString("selected_date")); }
            catch (Exception ignored) { selectedDate = null; }
        }

        bindViews(view);
        setupMealTypeChips();
        setupPortionChips();

        view.findViewById(R.id.btnCamera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) openCamera();
            else cameraPermission.launch(android.Manifest.permission.CAMERA);
        });
        view.findViewById(R.id.btnBarcode).setOnClickListener(v -> scanBarcode());
        view.findViewById(R.id.btnRemoveImage).setOnClickListener(v -> clearImage());
        view.findViewById(R.id.btnEditResult).setOnClickListener(v -> showEditDialog());
        btnFavorite.setOnClickListener(v -> vm.toggleFavorite());
        includeNoInternet.findViewById(R.id.btnRetryInternet).setOnClickListener(v -> checkInternetState());

        btnAnalyze.setOnClickListener(v -> {
            String food = etFood.getText().toString().trim();
            if (food.isEmpty() && base64Image == null) { toast(R.string.add_meal_enter_or_photo); return; }
            if (!NetworkUtils.isNetworkAvailable(requireContext())) { checkInternetState(); return; }
            if (base64Image != null) vm.analyzeImage(base64Image); else vm.analyzeText(food);
        });
        btnSave.setOnClickListener(v -> vm.save(selectedDate));

        vm.analyzing().observe(getViewLifecycleOwner(), busy -> {
            btnAnalyze.setEnabled(!busy);
            lottie.setVisibility(busy ? View.VISIBLE : View.GONE);
            if (busy) cardResult.setVisibility(View.GONE);
        });
        vm.saving().observe(getViewLifecycleOwner(), busy -> btnSave.setEnabled(!busy));
        vm.result().observe(getViewLifecycleOwner(), this::showResult);
        vm.portion().observe(getViewLifecycleOwner(), p -> {
            for (int i = 0; i < PORTIONS.length; i++) portionChips[i].setSelected(PORTIONS[i] == p);
        });
        vm.mealType().observe(getViewLifecycleOwner(), t -> {
            MealType[] all = MealType.values();
            for (int i = 0; i < all.length; i++) typeChips[i].setSelected(all[i] == t);
        });
        vm.quickItems().observe(getViewLifecycleOwner(), this::renderQuickItems);
        vm.saved().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() == null) return;
            etFood.setText("");
            clearImage();
            Bundle b = new Bundle();
            b.putString("meal_type", vm.mealType().getValue() != null ? vm.mealType().getValue().key : "");
            analytics.logEvent("meal_saved", b);
            if (selectedDate != null) navigateUp();
        });

        checkInternetState();
        vm.loadQuickItems();
    }

    private void bindViews(View view) {
        etFood = view.findViewById(R.id.etFood);
        btnAnalyze = view.findViewById(R.id.btnAnalyze);
        btnSave = view.findViewById(R.id.btnSave);
        btnFavorite = view.findViewById(R.id.btnFavorite);
        lottie = view.findViewById(R.id.lottieAnimationView);
        cardResult = view.findViewById(R.id.cardResult);
        layoutInput = view.findViewById(R.id.layoutInput);
        layoutQuickAdd = view.findViewById(R.id.layoutQuickAdd);
        layoutQuickAddSection = view.findViewById(R.id.layoutQuickAddSection);
        includeNoInternet = view.findViewById(R.id.includeNoInternet);
        ivPreview = view.findViewById(R.id.ivPreview);
        layoutPreview = view.findViewById(R.id.layoutPreview);
        tvFoodName = view.findViewById(R.id.tvFoodName);
        tvServingSize = view.findViewById(R.id.tvServingSize);
        tvConfidence = view.findViewById(R.id.tvConfidence);
        tvCalories = view.findViewById(R.id.tvCalories);
        tvProtein = view.findViewById(R.id.tvProtein);
        tvCarbs = view.findViewById(R.id.tvCarbs);
        tvFat = view.findViewById(R.id.tvFat);
        tvFiber = view.findViewById(R.id.tvFiber);
        typeChips = new TextView[]{view.findViewById(R.id.chipBreakfast), view.findViewById(R.id.chipLunch),
                view.findViewById(R.id.chipDinner), view.findViewById(R.id.chipSnack)};
        portionChips = new TextView[]{view.findViewById(R.id.chipPortionHalf), view.findViewById(R.id.chipPortion1),
                view.findViewById(R.id.chipPortion15), view.findViewById(R.id.chipPortion2)};
    }

    private void setupMealTypeChips() {
        MealType[] all = MealType.values();
        for (int i = 0; i < all.length; i++) {
            MealType t = all[i];
            typeChips[i].setOnClickListener(v -> vm.setMealType(t));
        }
    }

    private void setupPortionChips() {
        for (int i = 0; i < PORTIONS.length; i++) {
            double p = PORTIONS[i];
            portionChips[i].setOnClickListener(v -> vm.setPortion(p));
        }
    }

    // ── Hızlı ekleme ───────────────────────────────────────────────────

    private void renderQuickItems(List<AddMealViewModel.QuickItem> items) {
        layoutQuickAdd.removeAllViews();
        layoutQuickAddSection.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;
        for (AddMealViewModel.QuickItem item : items) {
            TextView chip = new TextView(requireContext());
            chip.setBackgroundResource(R.drawable.chip_bg);
            chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text));
            chip.setTextSize(13);
            chip.setMaxLines(1);
            chip.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (8 * density));
            chip.setText((item.favorite ? "⭐ " : "") + item.data.foodName + " · " + item.data.calories + " kcal");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int) (8 * density));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                clearImage();
                vm.useQuickItem(item);
            });
            layoutQuickAdd.addView(chip);
        }
    }

    // ── Sonuç ──────────────────────────────────────────────────────────

    private void showResult(@Nullable NutritionData data) {
        if (data == null) {
            cardResult.setVisibility(View.GONE);
            btnSave.setVisibility(View.GONE);
            return;
        }
        tvFoodName.setText(data.foodName);
        tvServingSize.setText(data.servingSize);
        tvCalories.setText(String.valueOf(data.calories));
        tvProtein.setText(fmtG(data.protein));
        tvCarbs.setText(fmtG(data.carbohydrates));
        tvFat.setText(fmtG(data.fat));
        tvFiber.setText(fmtG(data.fiber));

        tvConfidence.setText(confidenceLabel(data.confidence));
        int color;
        if ("high".equalsIgnoreCase(data.confidence)) color = R.color.brand_green;
        else if ("medium".equalsIgnoreCase(data.confidence)) color = R.color.accent_orange;
        else color = R.color.error;
        tvConfidence.setTextColor(ContextCompat.getColor(requireContext(), color));

        btnFavorite.setText(vm.isFavorite(data) ? R.string.add_meal_unfavorite : R.string.add_meal_favorite);
        cardResult.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
        if (FoodMath.isInconsistent(data)) toast(R.string.add_meal_inconsistent);

        Bundle b = new Bundle();
        b.putString("confidence", data.confidence);
        analytics.logEvent("meal_analyzed", b);
    }

    private String confidenceLabel(String c) {
        if ("high".equalsIgnoreCase(c)) return getString(R.string.confidence_high);
        if ("medium".equalsIgnoreCase(c)) return getString(R.string.confidence_medium);
        return getString(R.string.confidence_low);
    }

    private static String fmtG(float v) {
        return String.format(Locale.getDefault(), "%.1fg", v);
    }

    private void showEditDialog() {
        NutritionData cur = vm.result().getValue();
        if (cur == null) return;
        View dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_meal, null);
        EditText name = dv.findViewById(R.id.etEditName), serving = dv.findViewById(R.id.etEditServing),
                cal = dv.findViewById(R.id.etEditCalories), pro = dv.findViewById(R.id.etEditProtein),
                carb = dv.findViewById(R.id.etEditCarbs), fat = dv.findViewById(R.id.etEditFat),
                fib = dv.findViewById(R.id.etEditFiber);
        name.setText(cur.foodName);
        serving.setText(cur.servingSize);
        cal.setText(String.valueOf(cur.calories));
        pro.setText(num(cur.protein)); carb.setText(num(cur.carbohydrates));
        fat.setText(num(cur.fat)); fib.setText(num(cur.fiber));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_meal_edit)
                .setView(dv)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    NutritionData e = new NutritionData();
                    e.foodName = name.getText().toString().trim();
                    e.servingSize = serving.getText().toString().trim();
                    try {
                        e.calories = Integer.parseInt(cal.getText().toString().trim());
                        e.protein = parseF(pro); e.carbohydrates = parseF(carb);
                        e.fat = parseF(fat); e.fiber = parseF(fib);
                    } catch (NumberFormatException ex) {
                        toast(R.string.edit_meal_invalid); return;
                    }
                    if (e.foodName.isEmpty() || e.calories < 0 || e.calories > 10000) {
                        toast(R.string.edit_meal_invalid); return;
                    }
                    vm.applyEdit(e);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static String num(float f) {
        return String.format(Locale.ROOT, "%.1f", f);
    }

    private static float parseF(EditText et) {
        String s = et.getText().toString().trim().replace(',', '.');
        if (s.isEmpty()) return 0;
        float v = Float.parseFloat(s);
        if (v < 0 || v > 2000) throw new NumberFormatException();
        return v;
    }

    // ── Kamera ─────────────────────────────────────────────────────────

    private void openCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException();
            File file = new File(dir, "meal.jpg");
            photoUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            takePicture.launch(photoUri);
        } catch (Exception e) {
            toast(R.string.add_meal_no_camera);
        }
    }

    /** Fotoğrafı küçültüp (en fazla 1024 px) JPEG/base64'e çevirir. */
    private void loadPhoto(Uri uri) {
        android.content.Context ctx = requireContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                Bitmap bmp = decodeScaled(ctx, uri, MAX_IMAGE_SIDE);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, out);
                String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                AppExecutors.main(() -> {
                    if (!isAdded()) return;
                    base64Image = b64;
                    ivPreview.setImageBitmap(bmp);
                    layoutPreview.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                AppExecutors.main(() -> { if (isAdded()) toast(R.string.add_meal_photo_failed); });
            }
        });
    }

    static Bitmap decodeScaled(android.content.Context ctx, Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IllegalStateException("decode");

        int rotation = 0;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
        } catch (Exception ignored) {}

        float scale = Math.min(1f, maxSide / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
        if (scale < 1f || rotation != 0) {
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(rotation);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        return bmp;
    }

    private void clearImage() {
        base64Image = null;
        ivPreview.setImageBitmap(null);
        layoutPreview.setVisibility(View.GONE);
    }

    // ── Barkod ─────────────────────────────────────────────────────────

    private void scanBarcode() {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) { checkInternetState(); return; }
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanning.getClient(requireContext(), options).startScan()
                .addOnSuccessListener(code -> {
                    if (!isAdded()) return;
                    String raw = code.getRawValue();
                    if (raw == null || !raw.matches("\\d{6,14}")) { toast(R.string.barcode_not_found); return; }
                    clearImage();
                    vm.lookupBarcode(raw);
                })
                .addOnFailureListener(e -> { if (isAdded()) toast(R.string.barcode_scan_failed); });
    }

    // ── İnternet ───────────────────────────────────────────────────────

    private void checkInternetState() {
        boolean online = NetworkUtils.isNetworkAvailable(requireContext());
        includeNoInternet.setVisibility(online ? View.GONE : View.VISIBLE);
        layoutInput.setVisibility(online ? View.VISIBLE : View.GONE);
        // Çevrimdışıyken AI analizi yapılamaz; hızlı ekleme ve kaydetme (kuyruğa) çalışmaya devam eder.
        if (!online) lottie.setVisibility(View.GONE);
    }
}
