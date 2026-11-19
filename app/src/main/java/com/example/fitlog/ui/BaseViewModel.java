package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.data.AppExecutors;
import com.example.fitlog.data.FitLogRepository;
import com.example.fitlog.data.ServiceLocator;

/** Ortak ViewModel: arka planda iş çalıştırma ve tek seferlik mesajlar. */
public abstract class BaseViewModel extends AndroidViewModel {

    /** Arka planda çalışan, sonuç üreten iş. */
    public interface Task<T> { T run() throws Exception; }

    protected final FitLogRepository repo;
    private final MutableLiveData<Event<Throwable>> errors = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> messages = new MutableLiveData<>();

    protected BaseViewModel(@NonNull Application app) {
        super(app);
        repo = ServiceLocator.repository(app);
    }

    public LiveData<Event<Throwable>> errors() { return errors; }
    public LiveData<Event<Integer>> messages() { return messages; }

    protected void postMessage(int stringRes) { messages.postValue(new Event<>(stringRes)); }
    protected void postError(Throwable t) { errors.postValue(new Event<>(t)); }

    /** İşi arka planda çalıştırır ve durumu {@code target}'a yazar. */
    protected <T> void load(MutableLiveData<Resource<T>> target, Task<T> task) {
        Resource<T> prev = target.getValue();
        T previous = prev != null ? prev.data : null;
        target.setValue(Resource.loading(previous));
        AppExecutors.io().execute(() -> {
            try {
                target.postValue(Resource.success(task.run()));
            } catch (Exception e) {
                target.postValue(Resource.error(e, previous));
            }
        });
    }

    /** Hatası kullanıcıya gösterilmeyen arka plan işi (bildirim, token yükleme vb.). */
    protected <T> void runSilently(Task<T> task, java.util.function.Consumer<T> onSuccess) {
        AppExecutors.io().execute(() -> {
            try {
                T result = task.run();
                if (onSuccess != null) AppExecutors.main(() -> onSuccess.accept(result));
            } catch (Exception e) {
                android.util.Log.w(getClass().getSimpleName(), "Arka plan işi başarısız", e);
            }
        });
    }

    /** Sonucu yalnızca geri çağrıya veren arka plan işi; hata errors()'a gider. */
    protected <T> void run(Task<T> task, java.util.function.Consumer<T> onSuccess) {
        AppExecutors.io().execute(() -> {
            try {
                T result = task.run();
                if (onSuccess != null) AppExecutors.main(() -> onSuccess.accept(result));
            } catch (Exception e) {
                postError(e);
            }
        });
    }
}
