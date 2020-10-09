package com.nitc.rajpathrecalls;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EventList {

    private class Event{
        String title, sub_title, time;

        Event(String title, String sub_title, String time){
            this.title = title;
            this.sub_title = sub_title;
            this.time = time;
        }
    }

    private LinearLayout root;
    private Event[] events;

    EventList(LinearLayout layout, String day_of_week){
        root = layout;
        //todo load from day_of_week

        events = new Event[5];
        events[0] = new Event("The Not So Funny Show", "with Default Name", "16:00");
        events[1] = new Event("Chai Uncut", "with Default Name2", "17:00");
        events[2] = new Event("Unfiltered", "with Default Name3", "18:00");
        events[3] = new Event("Center Circle Scoop", "with Default Name4", "19:00");
        events[4] = new Event("Recalling", "with Default Name5", "20:00");
    }


    void populate(){
        for (Event event : events) {
            root.addView(createEventView(event));
        }
    }

    private View createEventView(Event event){
        LayoutInflater inflater = LayoutInflater.from(root.getContext());

        View view_root = inflater.inflate(R.layout.schedule_event_view, root, false);

        ((TextView)view_root.findViewById(R.id.event_title)).setText(event.title);
        ((TextView)view_root.findViewById(R.id.event_host)).setText(event.sub_title);
        ((TextView)view_root.findViewById(R.id.event_time)).setText(event.time);

        return view_root;
    }
}
