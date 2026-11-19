package com.example.fitlog.ui;

import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

/** Ortak fragment yardımcıları. */
public abstract class BaseFragment extends Fragment {

    /** ViewModel'in hata ve mesaj olaylarını toast olarak göster. */
    protected void bindCommon(BaseViewModel vm) {
        vm.errors().observe(getViewLifecycleOwner(), e -> {
            Throwable t = e.consume();
            if (t != null && isAdded()) ErrorMessages.toast(requireContext(), t);
        });
        vm.messages().observe(getViewLifecycleOwner(), e -> {
            Integer res = e.consume();
            if (res != null && isAdded()) toast(res);
        });
    }

    protected void toast(@StringRes int res) {
        if (getContext() != null) Toast.makeText(getContext(), res, Toast.LENGTH_SHORT).show();
    }

    protected void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    protected void navigateUp() {
        NavHostFragment.findNavController(this).navigateUp();
    }

    protected void navigate(int destination) {
        NavHostFragment.findNavController(this).navigate(destination);
    }
}
