package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.MealLog;
import com.example.fitlog.R;

import java.time.LocalDate;
import java.util.List;

/** Bugünün öğünleri ve silme. */
public class MealListViewModel extends BaseViewModel {

    private final MutableLiveData<Resource<List<MealLog>>> meals = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> deleted = new MutableLiveData<>();

    public MealListViewModel(@NonNull Application app) { super(app); }

    public LiveData<Resource<List<MealLog>>> meals() { return meals; }
    /** Silinen kaydın log_id'si. */
    public LiveData<Event<Integer>> deleted() { return deleted; }

    public void refresh() {
        load(meals, () -> repo.getMeals(LocalDate.now()));
    }

    public void delete(int logId) {
        run(() -> { repo.deleteMeal(logId); return logId; }, id -> {
            postMessage(R.string.msg_meal_deleted);
            deleted.setValue(new Event<>(id));
        });
    }
}
