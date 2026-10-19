package com.egymusic.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int AUDIO_PERMISSION = 42;
    private final ArrayList<Song> songs = new ArrayList<>();
    private final Set<Long> favorites = new HashSet<>();
    private final ArrayList<String> playlists = new ArrayList<>();
    private final HashMap<String, Set<Long>> playlistSongs = new HashMap<>();
    private String activePlaylist = null;
    private int current = -1;
    private LinearLayout list;
    private TextView nowPlaying, playButton, allTab, albumsTab, artistsTab, favoritesTab, playlistsTab, libraryCount;
    private ImageView miniArtwork;
    private EditText search;
    private boolean showFavorites = false;
    private int libraryMode = 0;
    private int accent = Color.rgb(255, 91, 116);
    private int panel = Color.rgb(28, 27, 36);
    private SharedPreferences prefs;

    static class Song {
        long id;
        String title, artist, album;
        Uri uri, artworkUri;
        Song(long id, String title, String artist, String album, long albumId) {
            this.id = id; this.title = title; this.artist = artist; this.album = album;
            this.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            this.artworkUri = albumId > 0 ? Uri.parse("content://media/external/audio/albumart/" + albumId) : null;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("egy_music_library", MODE_PRIVATE);
        for (String value : prefs.getStringSet("favorites", new HashSet<>())) {
            try { favorites.add(Long.parseLong(value)); } catch (NumberFormatException ignored) {}
        }
        for (String name : prefs.getStringSet("playlist_names", new HashSet<>())) {
            playlists.add(name);
            Set<Long> ids = new HashSet<>();
            for (String id : prefs.getStringSet("playlist_songs_" + name, new HashSet<>())) {
                try { ids.add(Long.parseLong(id)); } catch (NumberFormatException ignored) {}
            }
            playlistSongs.put(name, ids);
        }
        getWindow().setStatusBarColor(Color.rgb(16,17,22));
        getWindow().setNavigationBarColor(Color.rgb(16,17,22));
        IntentFilter progressFilter = new IntentFilter(PlaybackService.ACTION_PROGRESS_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(progressReceiver, progressFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(progressReceiver, progressFilter);
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
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        root.setBackgroundColor(Color.rgb(13, 13, 19));
        setContentView(root);

        TextView brand = new TextView(this);
        brand.setText("EGY MUSIC  ♫");
        brand.setTextColor(accent);
        brand.setTextSize(27);
        brand.setLetterSpacing(0.06f);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setTypeface(null, 1);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView subtitle = new TextView(this);
        subtitle.setText("موسيقاك.. على مزاجك");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(14), dp(14), dp(14));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(111, 63, 156), Color.rgb(195, 59, 104), Color.rgb(244, 104, 91)});
        heroBg.setCornerRadius(dp(22));
        hero.setBackground(heroBg);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(112));
        heroParams.topMargin = dp(10);
        root.addView(hero, heroParams);
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        hero.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView heroEyebrow = new TextView(this);
        heroEyebrow.setText("صوتك، مزاجك، عالمك");
        heroEyebrow.setTextColor(Color.rgb(255, 225, 232));
        heroEyebrow.setTextSize(12);
        heroText.addView(heroEyebrow);
        TextView heroTitle = new TextView(this);
        heroTitle.setText("خلّي الموسيقى تعيش معاك");
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTextSize(18);
        heroTitle.setTypeface(null, 1);
        LinearLayout.LayoutParams heroTitleParams = new LinearLayout.LayoutParams(-1, -2);
        heroTitleParams.topMargin = dp(5);
        heroText.addView(heroTitle, heroTitleParams);
        libraryCount = new TextView(this);
        libraryCount.setText("مكتبتك الموسيقية في مكان واحد");
        libraryCount.setTextColor(Color.rgb(255, 232, 236));
        libraryCount.setTextSize(11);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.topMargin = dp(4);
        heroText.addView(libraryCount, countParams);
        TextView heroPlay = new TextView(this);
        heroPlay.setText("▶");
        heroPlay.setTextSize(24);
        heroPlay.setTextColor(Color.WHITE);
        heroPlay.setGravity(Gravity.CENTER);
        GradientDrawable playBg = new GradientDrawable();
        playBg.setColor(Color.argb(55, 255, 255, 255));
        playBg.setCornerRadius(dp(28));
        heroPlay.setBackground(playBg);
        hero.addView(heroPlay, new LinearLayout.LayoutParams(dp(52), dp(52)));
        heroPlay.setOnClickListener(v -> { if (!songs.isEmpty()) playIndex(0); else Toast.makeText(this, "ضيف أغاني على الموبايل الأول", Toast.LENGTH_SHORT).show(); });

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("ابحث عن أغنية أو فنان...");
        search.setHintTextColor(Color.GRAY);
        search.setTextColor(Color.WHITE);
        search.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(panel);
        searchBg.setCornerRadius(dp(16));
        search.setBackground(searchBg);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.topMargin = dp(14);
        root.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderSongs(); }
            @Override public void afterTextChanged(Editable e) {}
        });

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0, dp(12), 0, dp(8));
        root.addView(tabs);
        allTab = tabButton("♫ الأغاني");
        albumsTab = tabButton("▦ الألبومات");
        artistsTab = tabButton("♬ الفنانون");
        favoritesTab = tabButton("♥ المفضلة");
        playlistsTab = tabButton("☷ القوائم");
        tabs.addView(allTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(albumsTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(artistsTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(favoritesTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(playlistsTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        allTab.setOnClickListener(v -> { libraryMode = 0; showFavorites = false; updateTabs(); renderSongs(); });
        albumsTab.setOnClickListener(v -> { libraryMode = 1; showFavorites = false; updateTabs(); renderSongs(); });
        artistsTab.setOnClickListener(v -> { libraryMode = 2; showFavorites = false; updateTabs(); renderSongs(); });
        favoritesTab.setOnClickListener(v -> { libraryMode = 3; showFavorites = true; activePlaylist = null; updateTabs(); renderSongs(); });
        playlistsTab.setOnClickListener(v -> { libraryMode = 4; showFavorites = false; activePlaylist = null; updateTabs(); renderSongs(); });
        updateTabs();

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(12), dp(10), dp(12), dp(6));
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(Color.rgb(29, 27, 37));
        dockBg.setCornerRadius(dp(20));
        dock.setBackground(dockBg);
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(-1, -2);
        dockParams.topMargin = dp(8);
        root.addView(dock, dockParams);

        LinearLayout miniRow = new LinearLayout(this);
        miniRow.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(miniRow, new LinearLayout.LayoutParams(-1, dp(52)));
        miniArtwork = new ImageView(this);
        miniArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtwork.setImageResource(android.R.drawable.ic_media_play);
        miniArtwork.setColorFilter(accent);
        miniArtwork.setBackground(makePill(panel));
        miniRow.addView(miniArtwork, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout miniLabels = new LinearLayout(this);
        miniLabels.setOrientation(LinearLayout.VERTICAL);
        miniLabels.setPadding(dp(10), 0, dp(8), 0);
        miniRow.addView(miniLabels, new LinearLayout.LayoutParams(0, -2, 1));
        nowPlaying = new TextView(this);
        nowPlaying.setText("اختار أغنية للبدء");
        nowPlaying.setTextColor(Color.WHITE);
        nowPlaying.setTextSize(13);
        nowPlaying.setMaxLines(1);
        nowPlaying.setTypeface(null, 1);
        miniLabels.addView(nowPlaying);
        TextView miniSubtitle = new TextView(this);
        miniSubtitle.setText("EGY MUSIC • مشغّل الموسيقى");
        miniSubtitle.setTextColor(Color.LTGRAY);
        miniSubtitle.setTextSize(10);
        miniLabels.addView(miniSubtitle);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        dock.addView(controls);
        Button prev = controlButton("⏮");
        prev.setOnClickListener(v -> startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_PREVIOUS)));
        controls.addView(prev);
        Button play = controlButton("▶");
        playButton = play;
        play.setOnClickListener(v -> togglePlayback());
        controls.addView(play);
        Button next = controlButton("⏭");
        next.setOnClickListener(v -> startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_NEXT)));
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
        Button timer = controlButton("⏱");
        timer.setTextSize(16);
        timer.setOnClickListener(v -> showSleepTimer());
        controls.addView(timer);
        Button equalizer = controlButton("🎚");
        equalizer.setTextSize(16);
        equalizer.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_EQUALIZER);
            startServiceCompat(intent);
        });
        controls.addView(equalizer);
    }

    private TextView tabButton(String label) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(null, 1);
        return b;
    }

    private void updateTabs() {
        if (allTab == null || favoritesTab == null) return;
        TextView[] tabs = {allTab, albumsTab, artistsTab, favoritesTab, playlistsTab};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == libraryMode || (i == 4 && libraryMode == 5);
            tabs[i].setTextColor(selected ? accent : Color.LTGRAY);
            tabs[i].setBackground(makePill(selected ? Color.rgb(67, 34, 52) : panel));
            tabs[i].setTextSize(11);
        }
    }

    private GradientDrawable makePill(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(15));
        return drawable;
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
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String title = cursor.getString(1);
                String artist = cursor.getString(2);
                String album = cursor.getString(3);
                long albumId = cursor.getLong(4);
                songs.add(new Song(id, title == null ? "أغنية بدون اسم" : title, artist == null ? "فنان غير معروف" : artist, album == null ? "ألبوم غير معروف" : album, albumId));
            }
        } catch (Exception e) {
            Toast.makeText(this, "حصل خطأ أثناء قراءة الأغاني", Toast.LENGTH_LONG).show();
        }
        if (libraryCount != null) libraryCount.setText(songs.size() + " أغنية في مكتبتك • جاهز تسمع؟");
        renderSongs();
    }

    private void renderSongs() {
        if (list == null) return;
        list.removeAllViews();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (libraryMode == 1 || libraryMode == 2) { renderCollections(libraryMode == 1, query); return; }
        if (libraryMode == 4) { renderPlaylists(query); return; }
        if (libraryMode == 5 && activePlaylist != null) {
            TextView back = tabButton("‹ رجوع إلى القوائم — " + activePlaylist);
            back.setTextColor(accent); back.setBackground(makePill(panel));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(40)); bp.bottomMargin = dp(8); list.addView(back, bp);
            back.setOnClickListener(v -> { libraryMode = 4; activePlaylist = null; updateTabs(); renderSongs(); });
        }
        int count = 0;
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            if (libraryMode == 3 && !favorites.contains(song.id)) continue;
            if (libraryMode == 5 && (activePlaylist == null || !playlistSongs.containsKey(activePlaylist) || !playlistSongs.get(activePlaylist).contains(song.id))) continue;
            if (!query.isEmpty() && !song.title.toLowerCase(java.util.Locale.ROOT).contains(query)
                    && !song.artist.toLowerCase(java.util.Locale.ROOT).contains(query)
                    && !song.album.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            count++;
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(9), dp(8), dp(6), dp(8));
            row.setBackground(makePill(current == i ? Color.rgb(62, 34, 51) : Color.rgb(25, 24, 32)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(7);
            list.addView(row, rp);

            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            icon.setBackgroundColor(Color.rgb(35,48,46));
            if (song.artworkUri != null) {
                try { icon.setImageURI(song.artworkUri); } catch (Exception ignored) {}
            }
            if (icon.getDrawable() == null) {
                icon.setImageResource(android.R.drawable.ic_media_play);
                icon.setPadding(dp(8), dp(8), dp(8), dp(8));
                icon.setColorFilter(accent);
            }
            icon.setClipToOutline(true);
            row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

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
            row.setOnLongClickListener(v -> { showAddToPlaylist(song); return true; });
            favorite.setOnClickListener(v -> {
                if (favorites.contains(song.id)) favorites.remove(song.id);
                else favorites.add(song.id);
                saveFavorites();
                renderSongs();
            });
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(libraryMode == 3 ? "لسه مفيش أغاني في المفضلة." : "مش لاقي أغاني مطابقة للبحث.");
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(15);
            empty.setPadding(dp(4), dp(22), dp(4), dp(22));
            list.addView(empty);
        }
    }

    private void renderCollections(boolean byAlbum, String query) {
        java.util.LinkedHashMap<String, ArrayList<Song>> groups = new java.util.LinkedHashMap<>();
        for (Song song : songs) {
            String name = byAlbum ? song.album : song.artist;
            if (!query.isEmpty() && !name.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            if (!groups.containsKey(name)) groups.put(name, new ArrayList<>());
            groups.get(name).add(song);
        }
        for (java.util.Map.Entry<String, ArrayList<Song>> entry : groups.entrySet()) {
            String name = entry.getKey();
            ArrayList<Song> groupSongs = entry.getValue();
            Song representative = groupSongs.get(0);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(9), dp(8), dp(9));
            row.setBackground(makePill(Color.rgb(25, 24, 32)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(8);
            list.addView(row, params);
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (representative.artworkUri != null) {
                try { cover.setImageURI(representative.artworkUri); } catch (Exception ignored) {}
            }
            if (cover.getDrawable() == null) {
                cover.setImageResource(byAlbum ? android.R.drawable.ic_menu_gallery : android.R.drawable.ic_menu_myplaces);
                cover.setColorFilter(accent);
            }
            row.addView(cover, new LinearLayout.LayoutParams(dp(58), dp(58)));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12), 0, dp(6), 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = new TextView(this);
            title.setText(name);
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setTypeface(null, 1);
            title.setMaxLines(1);
            labels.addView(title);
            TextView count = new TextView(this);
            count.setText(groupSongs.size() + (byAlbum ? " أغنية في الألبوم" : " أغنية للفنان"));
            count.setTextColor(Color.LTGRAY);
            count.setTextSize(12);
            labels.addView(count);
            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(accent);
            arrow.setTextSize(26);
            row.addView(arrow);
            row.setOnClickListener(v -> {
                libraryMode = 0;
                showFavorites = false;
                updateTabs();
                search.setText(name);
            });
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle(name)
                        .setMessage("تشغيل " + groupSongs.size() + " أغنية؟")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("تشغيل", (dialog, which) -> playIndex(songs.indexOf(representative))).show();
                return true;
            });
        }
        if (groups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(byAlbum ? "الألبومات هتظهر لما يكون عندك ملفات موسيقى." : "الفنانون هيتعرضوا هنا بعد قراءة الأغاني.");
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(14);
            empty.setPadding(dp(6), dp(22), dp(6), dp(22));
            list.addView(empty);
        }
    }

    private void renderPlaylists(String query) {
        Button create = controlButton("＋ إنشاء قائمة تشغيل");
        create.setTextSize(14); create.setBackground(makePill(Color.rgb(67, 34, 52)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(46)); cp.bottomMargin = dp(10); list.addView(create, cp);
        create.setOnClickListener(v -> promptCreatePlaylist(null));
        for (String name : playlists) {
            if (!query.isEmpty() && !name.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(12), dp(12)); row.setBackground(makePill(Color.rgb(25, 24, 32)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.bottomMargin = dp(8); list.addView(row, rp);
            TextView icon = new TextView(this); icon.setText("♫"); icon.setTextColor(accent); icon.setTextSize(28);
            row.addView(icon, new LinearLayout.LayoutParams(dp(42), -2));
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = new TextView(this); title.setText(name); title.setTextColor(Color.WHITE); title.setTextSize(15); title.setTypeface(null, 1); labels.addView(title);
            TextView count = new TextView(this); count.setText((playlistSongs.containsKey(name) ? playlistSongs.get(name).size() : 0) + " أغنية"); count.setTextColor(Color.LTGRAY); count.setTextSize(12); labels.addView(count);
            TextView more = new TextView(this); more.setText("⋮"); more.setTextSize(25); more.setTextColor(Color.LTGRAY); row.addView(more);
            row.setOnClickListener(v -> { activePlaylist = name; libraryMode = 5; updateTabs(); renderSongs(); });
            more.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[]{"فتح القائمة", "إعادة تسمية", "حذف القائمة"}, (dialog, which) -> {
                    if (which == 0) { activePlaylist = name; libraryMode = 5; updateTabs(); renderSongs(); }
                    else if (which == 1) promptRenamePlaylist(name);
                    else new AlertDialog.Builder(this).setTitle("حذف القائمة؟").setMessage("لن تُحذف الأغاني من الهاتف.")
                        .setNegativeButton("إلغاء", null).setPositiveButton("حذف", (d, w) -> {
                            playlists.remove(name); playlistSongs.remove(name); prefs.edit().remove("playlist_songs_" + name).apply(); savePlaylists(); renderSongs();
                        }).show();
                }).show());
        }
        if (playlists.isEmpty()) { TextView empty = new TextView(this); empty.setText("أنشئ قوائمك ورتّب أغانيك حسب مزاجك."); empty.setTextColor(Color.LTGRAY); empty.setTextSize(14); empty.setPadding(dp(6), dp(12), dp(6), dp(12)); list.addView(empty); }
    }

    private void showAddToPlaylist(Song song) {
        ArrayList<String> choices = new ArrayList<>(playlists); choices.add("＋ إنشاء قائمة جديدة");
        new AlertDialog.Builder(this).setTitle("إضافة «" + song.title + "» إلى قائمة")
            .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                if (which == choices.size() - 1) promptCreatePlaylist(song);
                else { String name = choices.get(which); if (!playlistSongs.containsKey(name)) playlistSongs.put(name, new HashSet<>()); playlistSongs.get(name).add(song.id); savePlaylists(); Toast.makeText(this, "اتضافت الأغنية إلى " + name, Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton("إلغاء", null).show();
    }

    private void promptCreatePlaylist(Song addSong) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("اسم قائمة التشغيل");
        new AlertDialog.Builder(this).setTitle("قائمة تشغيل جديدة").setView(input).setNegativeButton("إلغاء", null)
            .setPositiveButton("إنشاء", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(this, "اكتب اسم القائمة", Toast.LENGTH_SHORT).show(); return; }
                if (!playlists.contains(name)) playlists.add(name);
                if (!playlistSongs.containsKey(name)) playlistSongs.put(name, new HashSet<>());
                if (addSong != null) playlistSongs.get(name).add(addSong.id);
                savePlaylists(); libraryMode = 4; activePlaylist = null; updateTabs(); renderSongs();
            }).show();
    }

    private void promptRenamePlaylist(String oldName) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(oldName);
        new AlertDialog.Builder(this).setTitle("إعادة تسمية القائمة").setView(input).setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty() || name.equals(oldName) || playlists.contains(name)) return;
                int index = playlists.indexOf(oldName); if (index >= 0) playlists.set(index, name);
                playlistSongs.put(name, playlistSongs.remove(oldName)); prefs.edit().remove("playlist_songs_" + oldName).apply();
                if (oldName.equals(activePlaylist)) activePlaylist = name;
                savePlaylists(); renderSongs();
            }).show();
    }

    private void savePlaylists() {
        SharedPreferences.Editor editor = prefs.edit().putStringSet("playlist_names", new HashSet<>(playlists));
        for (Map.Entry<String, Set<Long>> entry : playlistSongs.entrySet()) {
            Set<String> ids = new HashSet<>(); for (Long id : entry.getValue()) ids.add(String.valueOf(id));
            editor.putStringSet("playlist_songs_" + entry.getKey(), ids);
        }
        editor.apply();
    }

    private void showSleepTimer() {
        String[] choices = {"بعد 15 دقيقة", "بعد 30 دقيقة", "بعد 45 دقيقة", "بعد ساعة", "بعد ساعة ونصف", "إلغاء مؤقت النوم"};
        int[] minutes = {15, 30, 45, 60, 90, 0};
        new AlertDialog.Builder(this).setTitle("مؤقت النوم").setItems(choices, (dialog, which) -> {
            Intent intent = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_SLEEP_TIMER);
            intent.putExtra(PlaybackService.EXTRA_TIMER_MINUTES, minutes[which]);
            startServiceCompat(intent);
            Toast.makeText(this, minutes[which] == 0 ? "تم إلغاء مؤقت النوم" : "الموسيقى هتقف بعد " + minutes[which] + " دقيقة", Toast.LENGTH_SHORT).show();
        }).show();
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
        nowPlaying.setText(selected.title);
        if (miniArtwork != null) {
            miniArtwork.clearColorFilter();
            if (selected.artworkUri != null) {
                try { miniArtwork.setImageURI(selected.artworkUri); } catch (Exception ignored) { miniArtwork.setImageResource(android.R.drawable.ic_media_play); }
            } else { miniArtwork.setImageResource(android.R.drawable.ic_media_play); miniArtwork.setColorFilter(accent); }
        }
        playButton.setText("⏸");
        ArrayList<Song> queueSongs = new ArrayList<>();
        if (libraryMode == 5 && activePlaylist != null && playlistSongs.containsKey(activePlaylist)) {
            for (Song item : songs) if (playlistSongs.get(activePlaylist).contains(item.id)) queueSongs.add(item);
        } else queueSongs.addAll(songs);
        int queueIndex = queueSongs.indexOf(selected);
        String[] uris = new String[queueSongs.size()];
        String[] titles = new String[queueSongs.size()];
        String[] artists = new String[queueSongs.size()];
        for (int i = 0; i < queueSongs.size(); i++) {
            uris[i] = queueSongs.get(i).uri.toString(); titles[i] = queueSongs.get(i).title; artists[i] = queueSongs.get(i).artist;
        }
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY);
        intent.putExtra(PlaybackService.EXTRA_INDEX, Math.max(0, queueIndex));
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
        playerUiHandler.removeCallbacks(playerUiUpdater);
        try { unregisterReceiver(progressReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
