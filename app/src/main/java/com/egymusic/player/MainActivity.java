package com.egymusic.player;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaPlayer;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int AUDIO_PERMISSION = 42;
    private final ArrayList<Song> songs = new ArrayList<>();
    private MediaPlayer player;
    private int current = -1;
    private LinearLayout list;
    private TextView nowPlaying, playButton;
    private int accent = Color.rgb(32, 214, 161);

    static class Song {
        long id;
        String title, artist;
        Uri uri;
        Song(long id, String title, String artist) {
            this.id = id; this.title = title; this.artist = artist;
            this.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16,17,22));
        getWindow().setNavigationBarColor(Color.rgb(16,17,22));
        buildUi();
        if (hasAudioPermission()) loadSongs();
        else requestPermissions(new String[]{audioPermission()}, AUDIO_PERMISSION);
    }

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }
    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(10));
        root.setBackgroundColor(Color.rgb(16,17,22));
        setContentView(root);

        TextView brand = new TextView(this);
        brand.setText("EGY MUSIC");
        brand.setTextColor(accent);
        brand.setTextSize(27);
        brand.setLetterSpacing(0.08f);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setTypeface(null, 1);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView subtitle = new TextView(this);
        subtitle.setText("موسيقاك.. على مزاجك");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(28)));

        TextView heading = new TextView(this);
        heading.setText("أغاني الموبايل");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        heading.setTypeface(null, 1);
        heading.setPadding(0, dp(16), 0, dp(10));
        root.addView(heading);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(14), dp(10), dp(14), dp(12));
        dock.setBackgroundColor(Color.rgb(30,31,38));
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(-1, -2);
        dockParams.topMargin = dp(10);
        root.addView(dock, dockParams);

        nowPlaying = new TextView(this);
        nowPlaying.setText("اختار أغنية للبدء");
        nowPlaying.setTextColor(Color.WHITE);
        nowPlaying.setTextSize(15);
        nowPlaying.setMaxLines(1);
        dock.addView(nowPlaying);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        dock.addView(controls);

        Button prev = controlButton("⏮");
        prev.setOnClickListener(v -> playIndex(Math.max(0, current - 1)));
        controls.addView(prev);

        Button play = controlButton("▶");
        playButton = (TextView) play;
        play.setOnClickListener(v -> togglePlayback());
        controls.addView(play);

        Button next = controlButton("⏭");
        next.setOnClickListener(v -> playIndex(Math.min(songs.size() - 1, current + 1)));
        controls.addView(next);
    }

    private Button controlButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(20);
        b.setTextColor(accent);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private void loadSongs() {
        songs.clear();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String title = cursor.getString(1);
                String artist = cursor.getString(2);
                songs.add(new Song(id, title == null ? "أغنية بدون اسم" : title, artist == null ? "فنان غير معروف" : artist));
            }
        } catch (Exception e) {
            Toast.makeText(this, "حصل خطأ أثناء قراءة الأغاني", Toast.LENGTH_LONG).show();
        }
        list.removeAllViews();
        if (songs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("مش لاقي أغاني على جهازك. أضف ملفات صوتية وحاول تاني.");
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(15);
            empty.setPadding(0, dp(20), 0, dp(20));
            list.addView(empty);
            return;
        }
        for (int i = 0; i < songs.size(); i++) {
            final int index = i;
            Song song = songs.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(8), dp(8));
            row.setBackgroundColor(Color.rgb(24,25,31));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(8);
            list.addView(row, rp);

            TextView icon = new TextView(this);
            icon.setText("♫");
            icon.setTextSize(25);
            icon.setTextColor(accent);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(48)));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(8), 0, 0, 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = new TextView(this);
            title.setText(song.title);
            title.setTextColor(Color.WHITE);
            title.setTextSize(15);
            title.setMaxLines(1);
            labels.addView(title);
            TextView artist = new TextView(this);
            artist.setText(song.artist);
            artist.setTextColor(Color.LTGRAY);
            artist.setTextSize(12);
            artist.setMaxLines(1);
            labels.addView(artist);
            row.setOnClickListener(v -> playIndex(index));
        }
    }

    private void playIndex(int index) {
        if (index < 0 || index >= songs.size()) return;
        releasePlayer();
        current = index;
        try {
            player = new MediaPlayer();
            player.setDataSource(this, songs.get(index).uri);
            player.setOnPreparedListener(mp -> {
                mp.start();
                nowPlaying.setText(songs.get(current).title + " — " + songs.get(current).artist);
                playButton.setText("⏸");
            });
            player.setOnCompletionListener(mp -> {
                if (current + 1 < songs.size()) playIndex(current + 1);
                else { playButton.setText("▶"); }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "تعذر تشغيل الملف الصوتي", Toast.LENGTH_SHORT).show();
                playButton.setText("▶");
                releasePlayer();
                return true;
            });
            player.prepareAsync();
            nowPlaying.setText("جاري التحميل: " + songs.get(index).title);
        } catch (Exception e) {
            Toast.makeText(this, "مش قادر أشغل الأغنية دي", Toast.LENGTH_SHORT).show();
            releasePlayer();
        }
    }

    private void togglePlayback() {
        if (player == null) {
            if (!songs.isEmpty()) playIndex(current >= 0 ? current : 0);
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            playButton.setText("▶");
        } else {
            player.start();
            playButton.setText("⏸");
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadSongs();
        else Toast.makeText(this, "لازم تسمح للتطبيق بقراءة ملفات الصوت", Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
