package com.hansoolabs.test.locationupdate;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hansoolabs.test.locationupdate.events.EmailEvent;
import com.hansoolabs.test.locationupdate.events.FusedIntervalEvent;
import com.hansoolabs.test.locationupdate.events.OverlayEvent;
import com.hansoolabs.test.locationupdate.events.SourceEvent;
import com.hansoolabs.test.locationupdate.events.ToggleSourceEvent;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * Created by brownsoo on 2017. 2. 17..
 */

public class OverlayView extends LinearLayout implements View.OnClickListener {

    private Map<OverlayEvent.Field, TextView> fieldTextViewMap = new HashMap<>();
    private Handler handler = new Handler();
    private Bus bus;
    private Button changeBt;
    private Button bt1000ms;
    private Button bt500ms;
    private Button bt300ms;
    private ImageButton buttonEmail;


    public OverlayView(Context context) {
        super(context);
    }

    public OverlayView(Context context, Bus bus) {
        super(context);

        this.bus = bus;
        bus.register(this);

        setOrientation(LinearLayout.VERTICAL);

        OverlayEvent.Field[] fields = OverlayEvent.Field.values();
        for (OverlayEvent.Field field : fields) {
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
            ));
            textView.setTextColor(Color.YELLOW);
            textView.setBackgroundColor(Color.argb(100, 0,0,0));
            addView(textView);
            fieldTextViewMap.put(field, textView);
        }

        LinearLayout container1 = new LinearLayout(context);
        container1.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        container1.setOrientation(HORIZONTAL);
        this.addView(container1);

        //
        bt1000ms = new Button(context);
        bt1000ms.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        bt1000ms.setText("1000ms");
        bt1000ms.setOnClickListener(this);
        container1.addView(bt1000ms);
        //
        bt500ms = new Button(context);
        bt500ms.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        bt500ms.setText("500ms");
        bt500ms.setOnClickListener(this);
        container1.addView(bt500ms);
        //
        bt300ms = new Button(context);
        bt300ms.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        bt300ms.setText("300ms");
        bt300ms.setOnClickListener(this);
        container1.addView(bt300ms);


        LinearLayout container2 = new LinearLayout(context);
        container2.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        container2.setOrientation(HORIZONTAL);
        this.addView(container2);
        //
        changeBt = new Button(context);
        changeBt.setLayoutParams(new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        ));
        changeBt.setText("Source");
        changeBt.setOnClickListener(this);
        container2.addView(changeBt);
        //
        buttonEmail = new ImageButton(context);
        buttonEmail.setImageDrawable(context.getResources().getDrawable(android.R.drawable.ic_dialog_email));
        buttonEmail.setOnClickListener(this);
        container2.addView(buttonEmail);
    }

    @Subscribe
    public void answerTestLocationEvent(final OverlayEvent event) {

        handler.post(new Runnable() {
            @Override
            public void run() {
                fieldTextViewMap.get(event.field).setText(event.field.name() + " : " + event.value);
            }
        });
    }

    @Subscribe
    public void answerSourceEvent(final SourceEvent event) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (event.source.equals(MainActivity.SRC_FUSED)) {
                    bt300ms.setVisibility(View.VISIBLE);
                    bt500ms.setVisibility(View.VISIBLE);
                    bt1000ms.setVisibility(View.VISIBLE);
                }
                else {
                    bt300ms.setVisibility(View.GONE);
                    bt500ms.setVisibility(View.GONE);
                    bt1000ms.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onClick(View view) {

        if (view.equals(bt1000ms)) {
            bus.post(new FusedIntervalEvent(1000));
        }
        else if (view.equals(bt500ms)) {
            bus.post(new FusedIntervalEvent(500));
        }
        else if (view.equals(bt300ms)) {
            bus.post(new FusedIntervalEvent(300));
        }
        else if (view.equals(buttonEmail)) {
            bus.post(new EmailEvent());
        }
        else if(view.equals(changeBt)){
            bus.post(new ToggleSourceEvent());
        }
    }
}
