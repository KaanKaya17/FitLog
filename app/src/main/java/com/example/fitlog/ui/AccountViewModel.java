package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.AuthManager;
import com.example.fitlog.PrefManager;
import com.example.fitlog.data.AppExecutors;
import com.example.fitlog.data.OfflineQueue;

/** Çıkış ve hesap silme. */
public class AccountViewModel extends BaseViewModel {

    private final MutableLiveData<Event<Boolean>> deleted = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);

    public AccountViewModel(@NonNull Application app) { super(app); }

    public LiveData<Event<Boolean>> deleted() { return deleted; }
    public LiveData<Boolean> busy() { return busy; }

    /** Yerel oturumu hemen siler, sunucu oturumunu arka planda kapatır. */
    public void logout() {
        Application app = getApplication();
        String token = new PrefManager(app).getAccessToken();
        AuthManager.clearLocalSession(app);
        AuthManager.revokeInBackground(token);
        AppExecutors.io().execute(() -> OfflineQueue.get(app).clear());
    }

    public void deleteAccount() {
        busy.setValue(true);
        Application app = getApplication();
        AppExecutors.io().execute(() -> {
            try {
                repo.deleteAccount();
                OfflineQueue.get(app).clear();
                AppExecutors.main(() -> {
                    AuthManager.clearLocalSession(app);
                    busy.setValue(false);
                    deleted.setValue(new Event<>(true));
                });
            } catch (Exception e) {
                busy.postValue(false);
                postError(e);
            }
        });
    }
}
