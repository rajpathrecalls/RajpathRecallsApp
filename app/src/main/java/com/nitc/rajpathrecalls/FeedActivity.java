package com.nitc.rajpathrecalls;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

public class FeedActivity extends AppCompatActivity {

    private String[] images;
    private ProgressBar[] progressBars;
    private ImageView image;
    private ObjectAnimator animator;
    private int currentPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        images = (String[]) getIntent().getSerializableExtra("pages");

        if (images == null) {
            onBackPressed();
            return;
        }

        LinearLayout progressRoot = findViewById(R.id.progress_layout);
        progressBars = new ProgressBar[images.length];
        for (int i = 0; i < progressBars.length; ++i) {
            progressBars[i] = (ProgressBar) getLayoutInflater().inflate(R.layout.feed_progress_layout, progressRoot, false);
            progressRoot.addView(progressBars[i]);
        }

        image = findViewById(R.id.feed_image);
        goToPage(0);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        final int width = displayMetrics.widthPixels;

        //noinspection AndroidLintClickableViewAccessibility
        image.setOnTouchListener(new View.OnTouchListener() {
            long buttonHoldTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (animator != null)
                        animator.pause();
                    buttonHoldTime = System.currentTimeMillis();

                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (animator != null)
                        animator.resume();
                    long timeElapsed = System.currentTimeMillis() - buttonHoldTime;
                    if (timeElapsed < 500) {
                        int position = (int) event.getRawX();
                        if (position > width / 2)
                            goToPage(currentPage + 1);
                        else
                            goToPage(currentPage - 1);
                    }
                }
                return true;
            }
        });
    }

    void goToPage(int page) {
        if (page >= 0 && page < images.length) {
            currentPage = page;
            setImage(images[page]);
        }
        if (page >= images.length)
            onBackPressed();
    }

    void setImage(String imageLink) {
        Glide
                .with(this)
                .load(imageLink)
                .listener(glideListener)
                .into(image);
    }

    private final RequestListener<Drawable> glideListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {

            if (animator != null)
                animator.cancel();
            animator = ObjectAnimator.ofInt(progressBars[currentPage], "progress", 0, 100);
            animator.setDuration(5000);
            animator.setInterpolator(new LinearInterpolator());
            animator.addListener(new AnimatorListenerAdapter() {

                boolean cancelled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    super.onAnimationCancel(animation);
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    animator = null;
                    if (!cancelled)
                        goToPage(currentPage + 1);
                }
            });
            animator.start();
            for (int i = 0; i < currentPage; ++i)
                progressBars[i].setProgress(100);
            for (int i = currentPage + 1; i < progressBars.length; ++i)
                progressBars[i].setProgress(0);

            return false;
        }
    };
}