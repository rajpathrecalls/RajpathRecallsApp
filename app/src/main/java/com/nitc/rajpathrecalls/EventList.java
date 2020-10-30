package com.nitc.rajpathrecalls;

import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;

public class EventList {

    @Keep       //dont change name while minifying
    static class Event {
        private String title, sub_title;
        private Date when;

        //set to ist timezone in event list constructor (since event times are in ist)
        private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        //For Firebase
        public Event() {

        }

        //DO NOT CHANGE FOLLOWING METHOD NAMES [firebase names]
        public void setWhen(String when_string) {
            try {
                when = sdf.parse(when_string);
            } catch (ParseException e) {
                when = null;
            }
        }

        public Date getWhen() {
            return when;
        }

        public void setMain(String main) {
            title = main;
        }

        public String getMain() {
            return title;
        }

        public void setSub(String sub) {
            sub_title = sub;
        }

        public String getSub() {
            return sub_title;
        }
    }

    private DatabaseReference mdata;

    private LinearLayout root;
    private LinkedList<Event> events;

    EventList(LinearLayout layout) {

        //event times are in ist
        Event.sdf.setTimeZone(TimeZone.getTimeZone("GMT+5:30"));

        root = layout;
        mdata = FirebaseDatabase.getInstance().getReference();
    }


    void populate() {
        mdata.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                Date now = Calendar.getInstance().getTime();
                events = new LinkedList<>();
                for (DataSnapshot s : snapshot.child("Schedule").getChildren()) {
                    Event e = s.getValue(Event.class);
                    if (e!= null && now.before(e.when))
                        events.add(e);
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
                    for (Event event : events) {
                        if (event.getWhen() != null)
                            root.addView(createEventView(event));
                    }

                } else {
                    View message = root.getChildAt(0);
                    message.setVisibility(View.VISIBLE);
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

    private View createEventView(Event event) {
        LayoutInflater inflater = LayoutInflater.from(root.getContext());

        View view_root = inflater.inflate(R.layout.schedule_event_view, root, false);

        ((TextView) view_root.findViewById(R.id.event_title)).setText(event.getMain());
        ((TextView) view_root.findViewById(R.id.event_host)).setText(event.getSub());
        ((TextView) view_root.findViewById(R.id.event_time)).setText(
                new SimpleDateFormat("HH:mm").format(event.getWhen()));

        return view_root;
    }
}
