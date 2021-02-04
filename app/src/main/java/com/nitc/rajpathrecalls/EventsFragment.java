package com.nitc.rajpathrecalls;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class EventsFragment extends Fragment {

    private static class Event {
        String name, host, image, description;

        Event(DataSnapshot s) {
            this.name = s.child("name").getValue().toString();
            this.host = s.child("host").getValue().toString();
            this.image = s.child("image").getValue().toString();
            this.description = s.child("description").getValue().toString();
        }
    }

    private Event[] events;
    private ProgressBar eventsProgressBar;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.fragment_events, container, false);
        ((MainActivity) getContext()).current_fragment = this;

        eventsProgressBar = fragmentView.findViewById(R.id.events_progress);

        final GridView event_grid = fragmentView.findViewById(R.id.events_grid);
        event_grid.setOnItemClickListener(gridItemClickListener);

        FirebaseDatabase.getInstance().getReference().child("Events").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                events = new Event[(int) snapshot.getChildrenCount()];
                int i = 0;
                for (DataSnapshot s : snapshot.getChildren()) {
                    events[i++] = new Event(s);
                }

                eventsProgressBar.setVisibility(View.GONE);
                event_grid.setAdapter(gridAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        return fragmentView;
    }

    private final AdapterView.OnItemClickListener gridItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            Intent intent = new Intent(getActivity(), EventActivity.class);
            intent.putExtra("eventMain", events[position].name);
            intent.putExtra("eventSub", events[position].host);
            intent.putExtra("imageLink", events[position].image);
            intent.putExtra("eventDescription", events[position].description);

            ActivityOptionsCompat options = ActivityOptionsCompat.
                    makeSceneTransitionAnimation(getActivity(), view.findViewById(R.id.event_image), "eventImage");
            startActivity(intent, options.toBundle());
        }
    };

    private final BaseAdapter gridAdapter = new BaseAdapter() {
        LayoutInflater inflater;

        @Override
        public int getCount() {
            return events.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (inflater == null) {
                inflater = LayoutInflater.from(getContext());
            }
            if (convertView == null) {          //handle recycled views
                convertView = inflater.inflate(R.layout.event_layout, parent, false);
            }

            ((TextView) convertView.findViewById(R.id.event_title)).setText(events[position].name);
            convertView.setClipToOutline(true);
            Glide
                    .with(getContext())
                    .load(events[position].image)
                    .placeholder(new ColorDrawable(0xff404040))
                    .into((ImageView) convertView.findViewById(R.id.event_image));

            return convertView;
        }
    };


}
