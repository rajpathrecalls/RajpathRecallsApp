package com.nitc.rajpathrecalls;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class EventActivity extends AppCompatActivity {

    boolean imageLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event);

        findViewById(R.id.event_fg).setClipToOutline(true);
        findViewById(R.id.event_image).setClipToOutline(true);

        Intent intent = getIntent();

        ((TextView) findViewById(R.id.event_title)).setText(intent.getStringExtra("eventMain"));
        ((TextView) findViewById(R.id.event_host)).setText(intent.getStringExtra("eventSub"));
        ((TextView) findViewById(R.id.event_description)).setText(intent.getStringExtra("eventDescription"));

        View shareButton = findViewById(R.id.share_button);
        if (isInstagramInstalled()) {
            shareButton.setOnClickListener(shareListener);
        } else {
            shareButton.setVisibility(View.GONE);
        }

        findViewById(R.id.event_bg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                supportFinishAfterTransition();
            }
        });

        Glide
                .with(this)
                .load(intent.getStringExtra("imageLink"))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        imageLoaded = true;
                        return false;
                    }
                })
                .into((ImageView) findViewById(R.id.event_image));

    }

    View.OnClickListener shareListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!imageLoaded) {
                Toast.makeText(EventActivity.this, "Image Loading", Toast.LENGTH_SHORT).show();
                return;
            }

            Drawable imageDrawable = ((ImageView) findViewById(R.id.event_image)).getDrawable();
            Bitmap bitmap = (imageDrawable instanceof BitmapDrawable) ? ((BitmapDrawable) imageDrawable).getBitmap() : null;

            if (bitmap == null) {
                Toast.makeText(EventActivity.this, "Failed to share", Toast.LENGTH_SHORT).show();
                return;
            }

            bitmap = Bitmap.createScaledBitmap(bitmap, 600, 600, true);

            //need to write to external storage
            final File downloaded = new File(getExternalFilesDir(null), "stickerimage.png");

            if (downloaded.exists() && !downloaded.delete())
                return;

            FileOutputStream fos;
            try {
                fos = new FileOutputStream(downloaded);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();
            } catch (IOException e) {
                Toast.makeText(EventActivity.this, "Failed to share", Toast.LENGTH_SHORT).show();
                return;
            }

            bitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
            int color = bitmap.getPixel(0, 0);
            final String top_color = "#" + Integer.toHexString(color).substring(2);
            goToInstagram(downloaded, top_color);
        }
    };


    void goToInstagram(File f, String color) {
        Uri stickerAssetUri = Uri.fromFile(f);

        Intent intent = new Intent("com.instagram.share.ADD_TO_STORY");
        intent.putExtra("source_application", BuildConfig.APPLICATION_ID);

        intent.setType("image/png");
        intent.putExtra("interactive_asset_uri", stickerAssetUri);
        intent.putExtra("top_background_color", color);
        intent.putExtra("bottom_background_color", "#000000");

        grantUriPermission("com.instagram.android", stickerAssetUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (getPackageManager().resolveActivity(intent, 0) != null) {
            startActivityForResult(intent, 69);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 69) {
            File downloaded = new File(getExternalFilesDir(null), "stickerimage.jpg");
            if (downloaded.exists())
                downloaded.delete();
        }
    }

    private boolean isInstagramInstalled() {
        try {
            return getPackageManager().getApplicationInfo("com.instagram.android", 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

    }

}