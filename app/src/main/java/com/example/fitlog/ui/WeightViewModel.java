package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.PrefManager;
import com.example.fitlog.R;
import com.example.fitlog.WeightLog;

import java.util.List;

public class WeightViewModel extends BaseViewModel {

    static final int HISTORY_LIMIT = 365;

    private final MutableLiveData<Resource<List<WeightLog>>> entries = new MutableLiveData<>();

    public WeightViewModel(@NonNull Application app) { super(app); }

    public LiveData<Resource<List<WeightLog>>> entries() { return entries; }

    public void refresh() {
        load(entries, () -> repo.getWeights(HISTORY_LIMIT));
    }

    public void add(float kg) {
        run(() -> repo.addWeight(kg), result -> {
            new PrefManager(getApplication()).setWeight(kg);
            postMessage(result.queued ? R.string.msg_saved_offline : R.string.weight_saved);
            refresh();
        });
    }

    public void delete(long id) {
        run(() -> { repo.deleteWeight(id); return null; }, ignored -> refresh());
    }
}
