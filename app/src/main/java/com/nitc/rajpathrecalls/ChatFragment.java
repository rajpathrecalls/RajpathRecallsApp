package com.nitc.rajpathrecalls;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFragment extends Fragment {

    private EditText message_box;
    private ImageView fn_button;
    private String username;
    private FirebaseRecyclerAdapter<Message, ChatViewHolder> adapter;

    static class Message {
        private String sender, message, time;
        private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

        public Message(String sender, String message, long time) {
            this.sender = sender;
            this.message = message;
            this.time = sdf.format(time);
        }

        public String getSender() {
            return sender;
        }

        public String getMessage() {
            return message;
        }

        public String getTime() {
            return time;
        }
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView sender_view, message_view, time_view;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            sender_view = itemView.findViewById(R.id.sender);
            message_view = itemView.findViewById(R.id.message);
            time_view = itemView.findViewById(R.id.time);
        }

        void hideSender() {
            sender_view.setVisibility(View.GONE);
            time_view.setVisibility(View.GONE);
        }

        void showSender() {
            sender_view.setVisibility(View.VISIBLE);
            time_view.setVisibility(View.VISIBLE);
        }

        void setData(Message m) {
            sender_view.setText(m.getSender());
            message_view.setText(m.getMessage());
            time_view.setText(m.getTime());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ((MainActivity) getContext()).current_fragment = this;

        View root = inflater.inflate(R.layout.fragment_chat, container, false);

        message_box = root.findViewById(R.id.message_view);
        fn_button = root.findViewById(R.id.message_fn_button);
        username = getActivity().getSharedPreferences("preferences", Context.MODE_PRIVATE).
                getString("chat_username", null);
        if (username == null) {
            username = "Anon_" + (new Random().nextInt(9000) + 1000);
        }

        message_box.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                if (s.toString().trim().length() > 0)
                    fn_button.setImageResource(R.drawable.ic_send);
                else
                    fn_button.setImageResource(R.drawable.ic_name_edit);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        handleRecyclerView((RecyclerView) root.findViewById(R.id.chat_recycler));

        fn_button.setOnClickListener(fn_listener);

        return root;
    }

    private void handleRecyclerView(final RecyclerView chat_list) {
        final LinearLayoutManager manager = new LinearLayoutManager(getContext()) {
            @Override
            public void onItemsAdded(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
                super.onItemsAdded(recyclerView, positionStart, itemCount);
                recyclerView.smoothScrollToPosition(getItemCount() - 1);
            }
        };
        manager.setStackFromEnd(true);
        chat_list.setLayoutManager(manager);
        chat_list.setHasFixedSize(true);

        Query query = FirebaseDatabase.getInstance().getReference().child("Chat");

        FirebaseRecyclerOptions<Message> options =
                new FirebaseRecyclerOptions.Builder<Message>()
                        .setQuery(query, new SnapshotParser<Message>() {
                            @NonNull
                            @Override
                            public Message parseSnapshot(@NonNull DataSnapshot snapshot) {
                                return new Message(snapshot.child("sender").getValue().toString(),
                                        snapshot.child("message").getValue().toString(),
                                        (long) snapshot.child("time").getValue());
                            }
                        })
                        .build();

        adapter = new FirebaseRecyclerAdapter<Message, ChatViewHolder>(options) {

            @NonNull
            @Override
            public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View chat_view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_user_layout, parent, false);
                return new ChatViewHolder(chat_view);
            }

            @Override
            protected void onBindViewHolder(@NonNull ChatViewHolder chatViewHolder, int i, @NonNull Message message) {
                chatViewHolder.setData(message);
            }
        };

        chat_list.setAdapter(adapter);
    }


    private View.OnClickListener fn_listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String message = message_box.getText().toString().trim();

            if (message.length() > 0) {
                DatabaseReference data = FirebaseDatabase.getInstance().getReference().child("Chat").push();
                Map<String, Object> map = new HashMap<>();
                map.put("sender", username);
                map.put("message", message);
                map.put("time", Calendar.getInstance().getTimeInMillis());
                data.setValue(map);

                message_box.setText("");

            } else {
                showNameDialog();
            }
        }
    };

    private void showNameDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme);
        dialog.setTitle("Choose your username");

        final EditText e = new EditText(getContext());
        e.setTextColor(getResources().getColor(R.color.subTextColor));
        e.setHint(username);
        e.setHintTextColor(0xff808080);
        e.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimaryHalf)));
        e.setHighlightColor(getResources().getColor(R.color.colorPrimaryHalf));
        float density = getResources().getDisplayMetrics().density;
        e.setPadding((int) (10 * density), e.getPaddingTop(), (int) (10 * density), e.getPaddingBottom());
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});

        dialog.setView(e);
        dialog.setPositiveButton(R.string.okay_text, null);
        dialog.setNegativeButton(R.string.cancel_text, null);

        final AlertDialog shown = dialog.show();
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = e.getText().toString();
                if ("".equals(name))
                    name = username;

                if (name.length() < 5) {
                    e.setError("Too short!");
                    return;
                }

                Matcher m = Pattern.compile("^[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*$").matcher(name);
                if (m.matches()) {
                    username = name;
                    shown.dismiss();
                } else {
                    e.setError("Invalid Username");
                }
            }
        });

    }


    @Override
    public void onPause() {
        super.onPause();
        getActivity().getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().putString("chat_username", username).apply();
        adapter.stopListening();
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.startListening();
    }
}
