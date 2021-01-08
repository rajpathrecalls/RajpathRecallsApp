package com.nitc.rajpathrecalls;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.os.Build;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;

public class EventList {

    private final int[] EVENT_COLOURS = new int[]{0xff553d36, 0xff684a52, 0xff857885, 0xff87a0b2, 0xff6c9a8b};
    private final LinearLayout root;
    private LinkedList<Event> events;

    static class Event {
        String title, sub_title;
        Date time;

        Event(String title, String sub_title, Long timestamp) {
            this.title = title;
            this.sub_title = sub_title;
            time = new Date(timestamp);
        }
    }

    EventList(LinearLayout layout) {
        root = layout;
    }

    void populate() {
        FirebaseDatabase.getInstance().getReference().child("Schedule").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                Date now = Calendar.getInstance().getTime();
                events = new LinkedList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Long timestamp = (Long) s.child("time").getValue();
                    String main = (String) s.child("main").getValue(), sub = (String) s.child("sub").getValue();
                    if (timestamp == null || main == null || sub == null)
                        continue;
                    Date event_time = new Date(timestamp);
                    if (sdf.format(now).equals(sdf.format(event_time)) && now.before(event_time)) {
                        events.add(new Event(main, sub, timestamp));
                    }
                }

                TransitionManager.beginDelayedTransition((ViewGroup) root.getParent().getParent());
                root.removeViewAt(0);   //progress bar

                if (events.size() > 0) {
                    root.removeViewAt(0);   //empty schedule text

                    //remove centering
                    HorizontalScrollView.LayoutParams lp = new HorizontalScrollView.LayoutParams(
                            HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.START;
                    root.setLayoutParams(lp);

                    root.setGravity(GravityCompat.START);

                    int currentColor = 0;
                    for (Event event : events) {
                        root.addView(createEventView(event, EVENT_COLOURS[currentColor++ % EVENT_COLOURS.length]));
                    }

                } else {
                    View message = root.getChildAt(0);
                    message.setVisibility(View.VISIBLE);
                    ViewGroup.LayoutParams lp = message.getLayoutParams();
                    lp.width = Resources.getSystem().getDisplayMetrics().widthPixels -
                            (int) (60 * Resources.getSystem().getDisplayMetrics().density);
                    message.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ViewGroup parent = (ViewGroup) root.getParent().getParent();
                            TransitionManager.beginDelayedTransition(parent);

                            parent.findViewById(R.id.coming_up_title).setVisibility(View.GONE);
                            ((View) v.getParent().getParent()).setVisibility(View.GONE);
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private View createEventView(Event event, int bg_color) {
        LayoutInflater inflater = LayoutInflater.from(root.getContext());

        View view_root = inflater.inflate(R.layout.schedule_event_view, root, false);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            view_root.getBackground().setColorFilter(bg_color, PorterDuff.Mode.MULTIPLY);
        else
            view_root.setBackgroundTintList(ColorStateList.valueOf(bg_color));

        ((TextView) view_root.findViewById(R.id.event_title)).setText(event.title);
        ((TextView) view_root.findViewById(R.id.event_host)).setText(event.sub_title);

        String time = new SimpleDateFormat("hh:mm aa")
                .format(event.time)
                .replace("AM", "am")
                .replace("PM", "pm");
        SpannableString time_text = new SpannableString(time);
        time_text.setSpan(new RelativeSizeSpan(0.7f), 6, 8, 0);
        ((TextView) view_root.findViewById(R.id.event_time)).setText(time_text);

        return view_root;
    }
}
