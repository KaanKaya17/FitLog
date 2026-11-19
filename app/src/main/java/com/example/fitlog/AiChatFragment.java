package com.example.fitlog;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitlog.ui.AiChatViewModel;
import com.example.fitlog.ui.BaseFragment;

import java.util.ArrayList;
import java.util.List;

/** AI koçla sohbet ekranı. */
public class AiChatFragment extends BaseFragment {

    private AiChatViewModel vm;
    private EditText etMessage;
    private View btnSend, layoutEmpty, tvTyping;
    private RecyclerView recycler;
    private final ChatAdapter adapter = new ChatAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(AiChatViewModel.class);
        bindCommon(vm);

        etMessage = view.findViewById(R.id.etChatMessage);
        btnSend = view.findViewById(R.id.btnSendChat);
        layoutEmpty = view.findViewById(R.id.layoutChatEmpty);
        tvTyping = view.findViewById(R.id.tvTyping);
        recycler = view.findViewById(R.id.recyclerChat);

        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        recycler.setLayoutManager(lm);
        recycler.setAdapter(adapter);

        view.findViewById(R.id.btnBack).setOnClickListener(v -> navigateUp());
        btnSend.setOnClickListener(v -> send(etMessage.getText().toString()));
        view.findViewById(R.id.chipSuggestion1).setOnClickListener(v -> send(getString(R.string.ai_chat_suggestion_1)));
        view.findViewById(R.id.chipSuggestion2).setOnClickListener(v -> send(getString(R.string.ai_chat_suggestion_2)));

        vm.chat().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            layoutEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            if (!list.isEmpty()) recycler.scrollToPosition(list.size() - 1);
        });
        vm.typing().observe(getViewLifecycleOwner(), t -> {
            tvTyping.setVisibility(Boolean.TRUE.equals(t) ? View.VISIBLE : View.GONE);
            btnSend.setEnabled(!Boolean.TRUE.equals(t));
        });
    }

    private void send(String text) {
        if (text.trim().isEmpty()) return;
        if (!NetworkUtils.isNetworkAvailable(requireContext())) { toast(R.string.error_network); return; }
        PrefManager p = new PrefManager(requireContext());
        if (p.getId() == -1) { toast(R.string.error_not_logged_in); return; }

        AiChatViewModel.Setup setup = new AiChatViewModel.Setup(
                getString(R.string.ai_chat_system, p.getFirstName(), orDash(p.getGender()),
                        p.getHeight() > 0 ? String.valueOf(p.getHeight()) : "-",
                        p.getWeight() > 0 ? String.valueOf(p.getWeight()) : "-", orDash(p.getGoal())),
                getString(R.string.ai_coach_weekly_context),
                p.getCalorieGoal(), p.getWaterGoal());
        vm.send(text, setup);
        etMessage.setText("");
    }

    private static String orDash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    private static final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        private final List<AiChatViewModel.Message> items = new ArrayList<>();

        void submit(List<AiChatViewModel.Message> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AiChatViewModel.Message m = items.get(position);
            h.bubble.setText(m.fromUser ? m.text : m.text.replaceAll("\\*\\*(.*?)\\*\\*", "$1").replaceAll("(?m)^\\s*[*-]\\s+", "• "));
            h.bubble.setBackgroundResource(m.fromUser ? R.drawable.chat_bubble_user : R.drawable.chat_bubble_model);
            h.bubble.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                    m.fromUser ? R.color.white : R.color.text_primary));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.bubble.getLayoutParams();
            lp.gravity = m.fromUser ? Gravity.END : Gravity.START;
            h.bubble.setLayoutParams(lp);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView bubble;
            VH(View v) { super(v); bubble = v.findViewById(R.id.tvBubble); }
        }
    }
}
