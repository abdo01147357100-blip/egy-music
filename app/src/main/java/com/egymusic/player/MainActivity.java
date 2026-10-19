package com.egymusic.player;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int AUDIO_PERMISSION = 42;
    private final ArrayList<Song> songs = new ArrayList<>();
    private final Set<Long> favorites = new HashSet<>();
    private int current = -1;
    private LinearLayout list;
    private TextView nowPlaying, playButton, allTab, favoritesTab;
    private EditText search;
    private boolean showFavorites = false;
    private int accent = Color.rgb(32, 214, 161);
    private SharedPreferences prefs;

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
        prefs = getSharedPreferences("egy_music_library", MODE_PRIVATE);
        for (String value : prefs.getStringSet("favorites", new HashSet<>())) {
            try { favorites.add(Long.parseLong(value)); } catch (NumberFormatException ignored) {}
        }
        getWindow().setStatusBarColor(Color.rgb(16,17,22));
        getWindow().setNavigationBarColor(Color.rgb(16,17,22));
        buildUi();
        if (hasAudioPermission()) loadSongs();
        else requestPermissions(new String[]{audioPermission()}, AUDIO_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
        }
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
        root.setPadding(dp(18), dp(18), dp(18), dp(10));
        root.setBackgroundColor(Color.rgb(16,17,22));
        setContentView(root);

        TextView brand = new TextView(this);
        brand.setText("EGY MUSIC  ♫");
        brand.setTextColor(accent);
        brand.setTextSize(27);
        brand.setLetterSpacing(0.06f);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setTypeface(null, 1);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView subtitle = new TextView(this);
        subtitle.setText("موسيقاك.. على مزاجك");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(26)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("ابحث عن أغنية أو فنان...");
        search.setHintTextColor(Color.GRAY);
        search.setTextColor(Color.WHITE);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackgroundColor(Color.rgb(31,32,39));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.topMargin = dp(12);
        root.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderSongs(); }
            @Override public void afterTextChanged(Editable e) {}
        });

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0, dp(8), 0, dp(6));
        root.addView(tabs);
        allTab = tabButton("كل الأغاني");
        favoritesTab = tabButton("♥ المفضلة");
        tabs.addView(allTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(favoritesTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        allTab.setOnClickListener(v -> { showFavorites = false; updateTabs(); renderSongs(); });
        favoritesTab.setOnClickListener(v -> { showFavorites = true; updateTabs(); renderSongs(); });
        updateTabs();

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(14), dp(10), dp(14), dp(8));
        dock.setBackgroundColor(Color.rgb(30,31,38));
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(-1, -2);
        dockParams.topMargin = dp(8);
        root.addView(dock, dockParams);

        nowPlaying = new TextView(this);
        nowPlaying.setText("اختار أغنية للبدء");
        nowPlaying.setTextColor(Color.WHITE);
        nowPlaying.setTextSize(14);
        nowPlaying.setMaxLines(1);
        dock.addView(nowPlaying);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        dock.addView(controls);
        Button prev = controlButton("⏮");
        prev.setOnClickListener(v -> playIndex(Math.max(0, current - 1)));
        controls.addView(prev);
        Button play = controlButton("▶");
        playButton = play;
        play.setOnClickListener(v -> togglePlayback());
        controls.addView(play);
        Button next = controlButton("⏭");
        next.setOnClickListener(v -> playIndex(Math.min(songs.size() - 1, current + 1)));
        controls.addView(next);
        Button shuffle = controlButton("🔀");
        shuffle.setTextSize(16);
        shuffle.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_SHUFFLE);
            startServiceCompat(intent);
            Toast.makeText(this, "تم تبديل وضع التشغيل العشوائي", Toast.LENGTH_SHORT).show();
        });
        controls.addView(shuffle);
        Button repeat = controlButton("🔁");
        repeat.setTextSize(16);
        repeat.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_REPEAT);
            startServiceCompat(intent);
            Toast.makeText(this, "تم تبديل وضع التكرار", Toast.LENGTH_SHORT).show();
        });
        controls.addView(repeat);
    }

    private TextView tabButton(String label) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(null, 1);
        return b;
    }

    private void updateTabs() {
        if (allTab == null || favoritesTab == null) return;
        allTab.setTextColor(showFavorites ? Color.LTGRAY : accent);
        favoritesTab.setTextColor(showFavorites ? accent : Color.LTGRAY);
        allTab.setBackgroundColor(showFavorites ? Color.rgb(24,25,31) : Color.rgb(35,48,46));
        favoritesTab.setBackgroundColor(showFavorites ? Color.rgb(35,48,46) : Color.rgb(24,25,31));
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
        renderSongs();
    }

    private void renderSongs() {
        if (list == null) return;
        list.removeAllViews();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        int count = 0;
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            if (showFavorites && !favorites.contains(song.id)) continue;
            if (!query.isEmpty() && !song.title.toLowerCase(java.util.Locale.ROOT).contains(query)
                    && !song.artist.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            count++;
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(7), dp(4), dp(7));
            row.setBackgroundColor(current == i ? Color.rgb(35,48,46) : Color.rgb(24,25,31));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(7);
            list.addView(row, rp);

            TextView icon = new TextView(this);
            icon.setText("♫");
            icon.setTextSize(24);
            icon.setTextColor(accent);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(48)));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(6), 0, dp(4), 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = new TextView(this);
            title.setText(song.title);
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setMaxLines(1);
            labels.addView(title);
            TextView artist = new TextView(this);
            artist.setText(song.artist);
            artist.setTextColor(Color.LTGRAY);
            artist.setTextSize(12);
            artist.setMaxLines(1);
            labels.addView(artist);
            row.setOnClickListener(v -> playIndex(index));

            TextView favorite = new TextView(this);
            favorite.setText(favorites.contains(song.id) ? "♥" : "♡");
            favorite.setTextSize(24);
            favorite.setTextColor(favorites.contains(song.id) ? accent : Color.GRAY);
            favorite.setGravity(Gravity.CENTER);
            row.addView(favorite, new LinearLayout.LayoutParams(dp(42), dp(48)));
            favorite.setOnClickListener(v -> {
                if (favorites.contains(song.id)) favorites.remove(song.id);
                else favorites.add(song.id);
                saveFavorites();
                renderSongs();
            });
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(showFavorites ? "لسه مفيش أغاني في المفضلة." : "مش لاقي أغاني مطابقة للبحث.");
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(15);
            empty.setPadding(dp(4), dp(22), dp(4), dp(22));
            list.addView(empty);
        }
    }

    private void saveFavorites() {
        Set<String> values = new HashSet<>();
        for (Long id : favorites) values.add(String.valueOf(id));
        prefs.edit().putStringSet("favorites", values).apply();
    }

    private void startServiceCompat(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && (PlaybackService.ACTION_PLAY.equals(intent.getAction())
                    || PlaybackService.ACTION_TOGGLE.equals(intent.getAction()))) startForegroundService(intent);
            else startService(intent);
        } catch (Exception e) {
            Toast.makeText(this, "تعذر تنفيذ الأمر", Toast.LENGTH_SHORT).show();
        }
    }

    private void playIndex(int index) {
        if (index < 0 || index >= songs.size()) return;
        current = index;
        Song selected = songs.get(index);
        nowPlaying.setText(selected.title + " — " + selected.artist);
        playButton.setText("⏸");
        String[] uris = new String[songs.size()];
        String[] titles = new String[songs.size()];
        String[] artists = new String[songs.size()];
        for (int i = 0; i < songs.size(); i++) {
            uris[i] = songs.get(i).uri.toString();
            titles[i] = songs.get(i).title;
            artists[i] = songs.get(i).artist;
        }
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY);
        intent.putExtra(PlaybackService.EXTRA_INDEX, index);
        intent.putExtra(PlaybackService.EXTRA_URIS, uris);
        intent.putExtra(PlaybackService.EXTRA_TITLES, titles);
        intent.putExtra(PlaybackService.EXTRA_ARTISTS, artists);
        startServiceCompat(intent);
        renderSongs();
    }

    private void togglePlayback() {
        if (current < 0) {
            if (!songs.isEmpty()) playIndex(0);
            else Toast.makeText(this, "اختار أغنية الأول", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_TOGGLE);
        startServiceCompat(intent);
        if (playButton.getText().toString().equals("⏸")) playButton.setText("▶");
        else playButton.setText("⏸");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadSongs();
        else if (requestCode == AUDIO_PERMISSION) Toast.makeText(this, "لازم تسمح للتطبيق بقراءة ملفات الصوت", Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
