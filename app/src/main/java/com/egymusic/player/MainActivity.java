package com.egymusic.player;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
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
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private int accent = Color.rgb(167, 139, 250);
    private int panel = Color.rgb(24, 24, 43);
    private SharedPreferences prefs;
    private AlertDialog fullPlayerDialog;
    private ValueAnimator playerBackgroundAnimator;
    private ValueAnimator coverPulseAnimator;
    private SeekBar progressBar;
    private TextView fullTitle, fullArtist, elapsedText, durationText;
    private int durationMillis = 0;
    private boolean userSeeking = false;
    private final Handler playerUiHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            durationMillis = intent.getIntExtra(PlaybackService.EXTRA_DURATION, 0);
            int position = intent.getIntExtra(PlaybackService.EXTRA_POSITION, 0);
            boolean playing = intent.getBooleanExtra(PlaybackService.EXTRA_IS_PLAYING, false);
            String title = intent.getStringExtra(PlaybackService.EXTRA_CURRENT_TITLE);
            String artist = intent.getStringExtra(PlaybackService.EXTRA_CURRENT_ARTIST);
            if (fullTitle != null && title != null) fullTitle.setText(title);
            if (fullArtist != null && artist != null) fullArtist.setText(artist);
            if (progressBar != null && !userSeeking) progressBar.setProgress(durationMillis > 0 ? (int)(position * 1000L / durationMillis) : 0);
            if (elapsedText != null) elapsedText.setText(formatTime(position));
            if (durationText != null) durationText.setText(formatTime(durationMillis));
            if (playButton != null) playButton.setText(playing ? "⏸" : "▶");
        }
    };

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
        if ("com.egymusic.player.OPEN_EQUALIZER".equals(getIntent().getAction())) {
            showBuiltInEqualizer(getIntent().getIntExtra("audio_session_id", 0));
        }
    }

    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if ("com.egymusic.player.OPEN_EQUALIZER".equals(intent.getAction())) {
            showBuiltInEqualizer(intent.getIntExtra("audio_session_id", 0));
        }
    }

    private void showBuiltInEqualizer(int sessionId) {
        LinearLayout panelView = new LinearLayout(this);
        panelView.setOrientation(LinearLayout.VERTICAL);
        panelView.setPadding(dp(20), dp(16), dp(20), dp(8));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(24, 45, 31), Color.rgb(10, 12, 12)});
        bg.setCornerRadius(dp(24));
        panelView.setBackground(bg);
        TextView caption = new TextView(this);
        caption.setText("اضبط صوتك بالطريقة اللي تحبها");
        caption.setTextColor(Color.rgb(239, 151, 145));
        caption.setTextSize(13);
        caption.setGravity(Gravity.CENTER);
        panelView.addView(caption, new LinearLayout.LayoutParams(-1, dp(34)));
        final Equalizer eq;
        try {
            if (sessionId <= 0) throw new IllegalStateException("No active audio session");
            eq = new Equalizer(0, sessionId);
            eq.setEnabled(true);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("معادل الصوت")
                .setMessage("ابدأ تشغيل أغنية أولًا، وبعدها افتح معادل الصوت مرة تانية. لو المشكلة مستمرة، ممكن يكون مؤثر الصوت مقفول من النظام.")
                .setPositiveButton("حسنًا", null).show();
            return;
        }
        short bands = eq.getNumberOfBands();
        short[] range = eq.getBandLevelRange();
        TextView presetLabel = new TextView(this);
        presetLabel.setText("الإعدادات الجاهزة");
        presetLabel.setTextColor(Color.WHITE);
        presetLabel.setTextSize(14);
        presetLabel.setPadding(0, dp(8), 0, dp(4));
        panelView.addView(presetLabel);
        android.widget.Spinner presets = new android.widget.Spinner(this);
        java.util.ArrayList<String> presetNames = new java.util.ArrayList<>();
        presetNames.add("مخصص");
        short presetCount = eq.getNumberOfPresets();
        for (short p = 0; p < presetCount; p++) presetNames.add(eq.getPresetName(p));
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presetNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presets.setAdapter(adapter);
        panelView.addView(presets, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout sliders = new LinearLayout(this);
        sliders.setOrientation(LinearLayout.VERTICAL);
        panelView.addView(sliders, new LinearLayout.LayoutParams(-1, -2));
        for (short band = 0; band < bands; band++) {
            final short bandIndex = band;
            LinearLayout bandRow = new LinearLayout(this);
            bandRow.setGravity(Gravity.CENTER_VERTICAL);
            sliders.addView(bandRow, new LinearLayout.LayoutParams(-1, dp(42)));
            TextView name = new TextView(this);
            name.setText(String.format(java.util.Locale.US, "%.1f kHz", eq.getCenterFreq(band) / 1000000f));
            name.setTextColor(Color.WHITE);
            name.setTextSize(11);
            bandRow.addView(name, new LinearLayout.LayoutParams(dp(60), -2));
            SeekBar slider = new SeekBar(this);
            slider.setMax(range[1] - range[0]);
            slider.setProgress(eq.getBandLevel(band) - range[0]);
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(239, 151, 145)));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(239, 151, 145)));
            bandRow.addView(slider, new LinearLayout.LayoutParams(0, dp(36), 1));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                    if (fromUser) {
                        try { eq.setBandLevel(bandIndex, (short)(value + range[0])); presets.setSelection(0); }
                        catch (Exception ignored) {}
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
        }
        presets.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    try {
                        eq.usePreset((short)(position - 1));
                        for (int b = 0; b < sliders.getChildCount(); b++) {
                            LinearLayout row = (LinearLayout)sliders.getChildAt(b);
                            SeekBar slider = (SeekBar)row.getChildAt(1);
                            slider.setProgress(eq.getBandLevel((short)b) - range[0]);
                        }
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("معادل الصوت • EGY MUSIC")
            .setView(panelView).setPositiveButton("تم", (d, w) -> {}).setNegativeButton("إيقاف التأثير", (d, w) -> {
                try { eq.setEnabled(false); eq.release(); } catch (Exception ignored) {}
            }).create();
        dialog.setOnDismissListener(d -> { try { eq.release(); } catch (Exception ignored) {} });
        dialog.show();
    }

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }
    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private void buildUi() {
        // New EGY MUSIC Studio layout: editorial header, featured listening card,
        // dedicated browse filters, library feed and a persistent player dock.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(7));
        root.setBackgroundColor(Color.rgb(8, 9, 18));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(5), dp(2), dp(10));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView eyebrow = new TextView(this);
        eyebrow.setText("YOUR SOUND. YOUR SPACE.");
        eyebrow.setTextColor(Color.rgb(167, 139, 250));
        eyebrow.setTextSize(9);
        eyebrow.setLetterSpacing(0.18f);
        heading.addView(eyebrow);
        TextView brand = new TextView(this);
        brand.setText("اكتشف موسيقاك");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(25);
        brand.setTypeface(null, 1);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-1, -2);
        brandParams.topMargin = dp(2);
        heading.addView(brand, brandParams);

        TextView settings = new TextView(this);
        settings.setText("⚙");
        settings.setTextColor(Color.WHITE);
        settings.setTextSize(22);
        settings.setGravity(Gravity.CENTER);
        GradientDrawable settingBg = new GradientDrawable();
        settingBg.setColor(Color.rgb(25, 24, 44));
        settingBg.setCornerRadius(dp(17));
        settingBg.setStroke(dp(1), Color.rgb(52, 48, 81));
        settings.setBackground(settingBg);
        header.addView(settings, new LinearLayout.LayoutParams(dp(46), dp(46)));
        settings.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("إعدادات EGY MUSIC")
            .setItems(new String[]{"معادل الصوت", "مؤقت النوم", "تحديث مكتبة الأغاني", "ترتيب حسب الاسم", "ترتيب حسب الفنان"}, (d, which) -> {
                if (which == 0) startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_EQUALIZER));
                else if (which == 1) showSleepTimer();
                else if (which == 2) loadSongs();
                else { java.util.Collections.sort(songs, which == 4 ? (a, b) -> a.artist.compareToIgnoreCase(b.artist) : (a, b) -> a.title.compareToIgnoreCase(b.title)); renderSongs(); }
            }).show());

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setHint("⌕   ابحث عن أغنية، ألبوم أو فنان");
        search.setHintTextColor(Color.rgb(143, 143, 167));
        search.setTextColor(Color.WHITE);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setCompoundDrawablePadding(dp(8));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.rgb(20, 21, 37));
        searchBg.setCornerRadius(dp(16));
        searchBg.setStroke(dp(1), Color.rgb(42, 40, 66));
        search.setBackground(searchBg);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.bottomMargin = dp(13);
        root.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderSongs(); }
            @Override public void afterTextChanged(Editable e) {}
        });

        FrameLayout feature = new FrameLayout(this);
        GradientDrawable featureBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(92, 60, 177), Color.rgb(48, 39, 107), Color.rgb(19, 23, 45)});
        featureBg.setCornerRadius(dp(26));
        featureBg.setStroke(dp(1), Color.rgb(99, 78, 171));
        feature.setBackground(featureBg);
        feature.setClipToOutline(true);
        LinearLayout.LayoutParams featureParams = new LinearLayout.LayoutParams(-1, dp(164));
        featureParams.bottomMargin = dp(13);
        root.addView(feature, featureParams);

        LinearLayout featureText = new LinearLayout(this);
        featureText.setOrientation(LinearLayout.VERTICAL);
        featureText.setPadding(dp(19), dp(17), 0, dp(15));
        FrameLayout.LayoutParams featureTextParams = new FrameLayout.LayoutParams(-1, -1, Gravity.LEFT);
        feature.addView(featureText, featureTextParams);
        TextView featureTag = new TextView(this);
        featureTag.setText("EGY MUSIC ORIGINAL");
        featureTag.setTextColor(Color.rgb(221, 207, 255));
        featureTag.setTextSize(9);
        featureTag.setLetterSpacing(0.16f);
        featureText.addView(featureTag);
        TextView featureTitle = new TextView(this);
        featureTitle.setText("عيش اللحظة\nعلى إيقاعك");
        featureTitle.setTextColor(Color.WHITE);
        featureTitle.setTextSize(24);
        featureTitle.setTypeface(null, 1);
        featureTitle.setLineSpacing(0, 0.92f);
        LinearLayout.LayoutParams featureTitleParams = new LinearLayout.LayoutParams(dp(205), 0, 1);
        featureTitleParams.topMargin = dp(7);
        featureText.addView(featureTitle, featureTitleParams);
        TextView featureButton = new TextView(this);
        featureButton.setText("▶   شغّل موسيقاك");
        featureButton.setTextColor(Color.WHITE);
        featureButton.setTextSize(11);
        featureButton.setTypeface(null, 1);
        featureButton.setGravity(Gravity.CENTER);
        featureButton.setBackground(makePill(Color.rgb(126, 92, 246)));
        featureText.addView(featureButton, new LinearLayout.LayoutParams(dp(132), dp(32)));
        TextView record = new TextView(this);
        record.setText("♫");
        record.setTextSize(92);
        record.setGravity(Gravity.CENTER);
        record.setTextColor(Color.rgb(224, 211, 255));
        GradientDrawable recordBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(153, 123, 255), Color.rgb(72, 48, 160)});
        recordBg.setShape(GradientDrawable.OVAL);
        record.setBackground(recordBg);
        FrameLayout.LayoutParams recordParams = new FrameLayout.LayoutParams(dp(118), dp(118), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        recordParams.rightMargin = dp(16);
        feature.addView(record, recordParams);
        View.OnClickListener playFeatured = v -> {
            if (songs.isEmpty()) Toast.makeText(this, "مفيش أغاني للتشغيل", Toast.LENGTH_SHORT).show();
            else playIndex(current >= 0 ? current : 0);
        };
        feature.setOnClickListener(playFeatured);
        featureButton.setOnClickListener(playFeatured);

        TextView browseTitle = new TextView(this);
        browseTitle.setText("تصفّح مكتبتك");
        browseTitle.setTextColor(Color.WHITE);
        browseTitle.setTextSize(16);
        browseTitle.setTypeface(null, 1);
        LinearLayout.LayoutParams browseTitleParams = new LinearLayout.LayoutParams(-1, -2);
        browseTitleParams.bottomMargin = dp(8);
        root.addView(browseTitle, browseTitleParams);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filterScroll.addView(filters);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, dp(42));
        filterParams.bottomMargin = dp(10);
        root.addView(filterScroll, filterParams);
        allTab = tabButton("كل الأغاني");
        albumsTab = tabButton("الألبومات");
        artistsTab = tabButton("الفنانون");
        favoritesTab = tabButton("♥ المفضلة");
        playlistsTab = tabButton("قوائم التشغيل");
        filters.addView(allTab, tabLayoutParams(104));
        filters.addView(albumsTab, tabLayoutParams(100));
        filters.addView(artistsTab, tabLayoutParams(92));
        filters.addView(favoritesTab, tabLayoutParams(106));
        filters.addView(playlistsTab, tabLayoutParams(126));
        allTab.setOnClickListener(v -> { libraryMode = 0; showFavorites = false; activePlaylist = null; updateTabs(); renderSongs(); });
        albumsTab.setOnClickListener(v -> { libraryMode = 1; showFavorites = false; updateTabs(); renderSongs(); });
        artistsTab.setOnClickListener(v -> { libraryMode = 2; showFavorites = false; updateTabs(); renderSongs(); });
        favoritesTab.setOnClickListener(v -> { libraryMode = 3; showFavorites = true; activePlaylist = null; updateTabs(); renderSongs(); });
        playlistsTab.setOnClickListener(v -> { libraryMode = 4; showFavorites = false; activePlaylist = null; updateTabs(); renderSongs(); });
        updateTabs();

        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        libraryHeader.setPadding(dp(2), dp(2), dp(2), dp(8));
        root.addView(libraryHeader, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout libraryHeading = new LinearLayout(this);
        libraryHeading.setOrientation(LinearLayout.VERTICAL);
        libraryHeader.addView(libraryHeading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView libraryTitle = new TextView(this);
        libraryTitle.setText("مكتبتك الموسيقية");
        libraryTitle.setTextColor(Color.WHITE);
        libraryTitle.setTextSize(17);
        libraryTitle.setTypeface(null, 1);
        libraryHeading.addView(libraryTitle);
        libraryCount = new TextView(this);
        libraryCount.setText("جاري تحميل الأغاني...");
        libraryCount.setTextColor(Color.rgb(150, 150, 177));
        libraryCount.setTextSize(10);
        libraryHeading.addView(libraryCount);
        TextView playAll = new TextView(this);
        playAll.setText("تشغيل الكل  ▶");
        playAll.setTextColor(Color.WHITE);
        playAll.setTextSize(11);
        playAll.setGravity(Gravity.CENTER);
        playAll.setPadding(dp(13), 0, dp(13), 0);
        playAll.setBackground(makePill(Color.rgb(126, 92, 246)));
        libraryHeader.addView(playAll, new LinearLayout.LayoutParams(dp(116), dp(36)));
        playAll.setOnClickListener(v -> {
            if (songs.isEmpty()) Toast.makeText(this, "مفيش أغاني للتشغيل", Toast.LENGTH_SHORT).show();
            else playIndex(0);
        });

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView songScroll = new ScrollView(this);
        songScroll.setFillViewport(false);
        songScroll.setClipToPadding(false);
        songScroll.setVerticalScrollBarEnabled(false);
        songScroll.addView(list);
        root.addView(songScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(12), dp(7), dp(12), dp(4));
        GradientDrawable dockBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(29, 26, 55), Color.rgb(18, 20, 36)});
        dockBg.setCornerRadius(dp(23));
        dockBg.setStroke(dp(1), Color.rgb(56, 47, 91));
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(-1, -2);
        dockParams.topMargin = dp(8);
        root.addView(dock, dockParams);
        LinearLayout miniRow = new LinearLayout(this);
        miniRow.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(miniRow, new LinearLayout.LayoutParams(-1, dp(58)));
        miniArtwork = new ImageView(this);
        miniArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtwork.setImageResource(android.R.drawable.ic_media_play);
        miniArtwork.setBackground(makePill(Color.rgb(55, 42, 93)));
        miniArtwork.setClipToOutline(true);
        miniRow.addView(miniArtwork, new LinearLayout.LayoutParams(dp(52), dp(52)));
        miniArtwork.setOnClickListener(v -> showFullPlayer());
        LinearLayout miniLabels = new LinearLayout(this);
        miniLabels.setOrientation(LinearLayout.VERTICAL);
        miniLabels.setPadding(dp(10), 0, dp(7), 0);
        miniRow.addView(miniLabels, new LinearLayout.LayoutParams(0, -2, 1));
        nowPlaying = new TextView(this);
        nowPlaying.setText("اختار أغنية للبدء");
        nowPlaying.setTextColor(Color.WHITE);
        nowPlaying.setTextSize(13);
        nowPlaying.setMaxLines(1);
        nowPlaying.setTypeface(null, 1);
        miniLabels.addView(nowPlaying);
        TextView miniSubtitle = new TextView(this);
        miniSubtitle.setText("EGY MUSIC  •  NOW PLAYING");
        miniSubtitle.setTextColor(Color.rgb(164, 157, 195));
        miniSubtitle.setTextSize(9);
        miniLabels.addView(miniSubtitle);
        Button play = controlButton("▶");
        play.setTextSize(22);
        playButton = play;
        play.setOnClickListener(v -> togglePlayback());
        miniRow.addView(play, new LinearLayout.LayoutParams(dp(46), dp(48)));
        Button repeat = controlButton("⟳");
        repeat.setTextSize(21);
        repeat.setOnClickListener(v -> startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_REPEAT)));
        miniRow.addView(repeat, new LinearLayout.LayoutParams(dp(42), dp(48)));
        miniRow.setOnClickListener(v -> showFullPlayer());

        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(3), dp(3), dp(3), 0);
        root.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(45)));
        addBottomNavItem(bottomNav, "⌂", "الرئيسية", true, () -> {
            libraryMode = 0; activePlaylist = null; showFavorites = false; updateTabs(); renderSongs();
        });
        addBottomNavItem(bottomNav, "⌕", "بحث", false, () -> {
            search.requestFocus();
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        addBottomNavItem(bottomNav, "♫", "مكتبتي", false, () -> {
            libraryMode = 0; activePlaylist = null; showFavorites = false; updateTabs(); renderSongs();
            songScroll.smoothScrollTo(0, 0);
        });
        addBottomNavItem(bottomNav, "⚙", "الإعدادات", false, () ->
            new AlertDialog.Builder(this).setTitle("إعدادات EGY MUSIC")
                .setItems(new String[]{"معادل الصوت", "مؤقت النوم", "تحديث مكتبة الأغاني"}, (d, which) -> {
                    if (which == 0) startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_EQUALIZER));
                    else if (which == 1) showSleepTimer();
                    else loadSongs();
                }).show());
    }

    private void addQuickAction(LinearLayout parent, String glyphText, String titleText, String subText, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(20, 23, 41));
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), Color.rgb(43, 43, 72));
        card.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(142), dp(68));
        params.rightMargin = dp(8);
        parent.addView(card, params);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(line);
        TextView glyph = new TextView(this);
        glyph.setText(glyphText);
        glyph.setTextColor(accent);
        glyph.setTextSize(21);
        glyph.setGravity(Gravity.CENTER);
        line.addView(glyph, new LinearLayout.LayoutParams(dp(28), dp(30)));
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(12);
        title.setTypeface(null, 1);
        title.setPadding(dp(5), 0, 0, 0);
        line.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(subText);
        subtitle.setTextColor(Color.rgb(157, 160, 188));
        subtitle.setTextSize(9);
        subtitle.setPadding(dp(33), 0, 0, 0);
        card.addView(subtitle);
        card.setOnClickListener(v -> action.run());
    }

    private void addBottomNavItem(LinearLayout nav, String symbol, String label, boolean active, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        TextView glyph = new TextView(this);
        glyph.setText(symbol);
        glyph.setTextSize(21);
        glyph.setGravity(Gravity.CENTER);
        glyph.setTextColor(active ? accent : Color.rgb(145, 145, 150));
        item.addView(glyph);
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(9);
        caption.setGravity(Gravity.CENTER);
        caption.setTextColor(active ? Color.WHITE : Color.rgb(145, 145, 150));
        item.addView(caption);
        item.setOnClickListener(v -> action.run());
    }

    private LinearLayout.LayoutParams tabLayoutParams(int widthDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(widthDp), dp(40));
        p.rightMargin = dp(7);
        return p;
    }

    private TextView tabButton(String label) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(null, 1);
        return b;
    }

    private void updateTabs() {
        if (allTab == null || favoritesTab == null) return;
        TextView[] tabs = {allTab, albumsTab, artistsTab, favoritesTab, playlistsTab};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == libraryMode || (i == 4 && libraryMode == 5);
            tabs[i].setTextColor(selected ? Color.WHITE : Color.rgb(168, 168, 172));
            tabs[i].setBackground(makePill(selected ? Color.rgb(31, 117, 75) : Color.rgb(25, 27, 27)));
            tabs[i].setTextSize(11);
        }
    }

    private GradientDrawable makePill(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(18));
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
            row.setPadding(dp(8), dp(8), dp(7), dp(8));
            GradientDrawable songCard = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    index == current
                        ? new int[]{Color.rgb(47, 68, 49), Color.rgb(30, 39, 31)}
                        : new int[]{Color.rgb(27, 30, 29), Color.rgb(17, 19, 19)});
            songCard.setCornerRadius(dp(18));
            if (index == current) songCard.setStroke(dp(2), Color.rgb(100, 213, 143));
            row.setBackground(songCard);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(6);
            list.addView(row, rp);

            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable artworkBg = new GradientDrawable();
            artworkBg.setColor(Color.rgb(35,48,46));
            artworkBg.setCornerRadius(dp(13));
            icon.setBackground(artworkBg);
            if (song.artworkUri != null) {
                try { icon.setImageURI(song.artworkUri); } catch (Exception ignored) {}
            }
            if (icon.getDrawable() == null) {
                icon.setImageResource(android.R.drawable.ic_media_play);
                icon.setPadding(dp(8), dp(8), dp(8), dp(8));
                icon.setColorFilter(accent);
            }
            icon.setClipToOutline(true);
            row.addView(icon, new LinearLayout.LayoutParams(dp(66), dp(66)));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(6), 0, dp(10), 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = new TextView(this);
            title.setText(song.title);
            title.setTextColor(index == current ? Color.rgb(147, 232, 175) : Color.WHITE);
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

    private String formatTime(int millis) {
        int total = Math.max(0, millis) / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private void showFullPlayer() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(12));
        GradientDrawable playerBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(18, 13, 39), Color.rgb(41, 30, 88), Color.rgb(12, 16, 32)});
        playerBg.setCornerRadius(dp(26));
        content.setBackground(playerBg);
        // Subtle animated emerald/coral glow gives the player a living, dynamic feel.
        playerBackgroundAnimator = ValueAnimator.ofFloat(0f, 1f);
        playerBackgroundAnimator.setDuration(5200);
        playerBackgroundAnimator.setRepeatMode(ValueAnimator.REVERSE);
        playerBackgroundAnimator.setRepeatCount(ValueAnimator.INFINITE);
        playerBackgroundAnimator.addUpdateListener(a -> {
            float t = (float)a.getAnimatedValue();
            int c1 = (Integer)new ArgbEvaluator().evaluate(t, Color.rgb(18, 13, 39), Color.rgb(38, 20, 67));
            int c2 = (Integer)new ArgbEvaluator().evaluate(t, Color.rgb(41, 30, 88), Color.rgb(48, 35, 104));
            int c3 = (Integer)new ArgbEvaluator().evaluate(t, Color.rgb(12, 16, 32), Color.rgb(22, 29, 58));
            playerBg.setColors(new int[]{c1, c2, c3});
            content.invalidate();
        });
        playerBackgroundAnimator.start();
        TextView heading = new TextView(this);
        heading.setText("EGY MUSIC  •  NOW PLAYING"); heading.setTextColor(Color.rgb(205, 187, 255)); heading.setTextSize(15); heading.setLetterSpacing(0.04f); heading.setGravity(Gravity.CENTER);
        content.addView(heading, new LinearLayout.LayoutParams(-1, dp(32)));
        ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setBackground(makePill(panel));
        if (current >= 0 && current < songs.size() && songs.get(current).artworkUri != null) {
            try { cover.setImageURI(songs.get(current).artworkUri); } catch (Exception ignored) {}
        }
        if (cover.getDrawable() == null) { cover.setImageResource(android.R.drawable.ic_media_play); cover.setColorFilter(accent); }
        GradientDrawable coverFrame = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(167, 139, 250), Color.rgb(101, 74, 203), Color.rgb(167, 139, 250)});
        coverFrame.setCornerRadius(dp(22));
        cover.setBackground(coverFrame);
        cover.setClipToOutline(true);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(250), dp(250));
        coverParams.topMargin = dp(12); coverParams.bottomMargin = dp(18); content.addView(cover, coverParams);
        coverPulseAnimator = ValueAnimator.ofFloat(0.985f, 1.015f);
        coverPulseAnimator.setDuration(1800);
        coverPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        coverPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        coverPulseAnimator.addUpdateListener(a -> {
            float scale = (float)a.getAnimatedValue();
            cover.setScaleX(scale);
            cover.setScaleY(scale);
        });
        coverPulseAnimator.start();
        fullTitle = new TextView(this); fullTitle.setTextColor(Color.WHITE); fullTitle.setTextSize(23); fullTitle.setTypeface(null, 1); fullTitle.setGravity(Gravity.CENTER); fullTitle.setMaxLines(2);
        content.addView(fullTitle, new LinearLayout.LayoutParams(-1, -2));
        fullArtist = new TextView(this); fullArtist.setTextColor(Color.rgb(195, 174, 255)); fullArtist.setTextSize(15); fullArtist.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(-1, -2); artistParams.topMargin = dp(5); content.addView(fullArtist, artistParams);
        progressBar = new SeekBar(this); progressBar.setMax(1000); progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent)); progressBar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(-1, -2); seekParams.topMargin = dp(18); content.addView(progressBar, seekParams);
        LinearLayout times = new LinearLayout(this); times.setGravity(Gravity.CENTER_VERTICAL); content.addView(times, new LinearLayout.LayoutParams(-1, dp(22)));
        elapsedText = new TextView(this); elapsedText.setTextColor(Color.LTGRAY); elapsedText.setTextSize(11); times.addView(elapsedText, new LinearLayout.LayoutParams(0, -2, 1));
        durationText = new TextView(this); durationText.setTextColor(Color.LTGRAY); durationText.setTextSize(11); durationText.setGravity(Gravity.RIGHT); times.addView(durationText, new LinearLayout.LayoutParams(0, -2, 1));
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { if (fromUser && elapsedText != null) elapsedText.setText(formatTime(value * durationMillis / 1000)); }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                Intent seek = new Intent(MainActivity.this, PlaybackService.class).setAction(PlaybackService.ACTION_SEEK);
                seek.putExtra(PlaybackService.EXTRA_SEEK_POSITION, bar.getProgress() * durationMillis / 1000);
                startServiceCompat(seek);
            }
        });
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(-1, dp(72)); controlParams.topMargin = dp(18); content.addView(controls, controlParams);
        TextView previous = playerControl("⏮", 25), play = playerControl("⏯", 32), next = playerControl("⏭", 25);
        controls.addView(previous, new LinearLayout.LayoutParams(dp(64), dp(58)));
        controls.addView(play, new LinearLayout.LayoutParams(dp(80), dp(62)));
        controls.addView(next, new LinearLayout.LayoutParams(dp(64), dp(58)));
        previous.setOnClickListener(v -> startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_PREVIOUS)));
        next.setOnClickListener(v -> startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_NEXT)));
        play.setOnClickListener(v -> togglePlayback());
        TextView tools = new TextView(this); tools.setText("🔀 عشوائي     🔁 تكرار     🎚 معادل الصوت     ⏱ مؤقت النوم"); tools.setTextColor(accent); tools.setGravity(Gravity.CENTER); tools.setTextSize(12);
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, dp(44)); toolsParams.topMargin = dp(5); content.addView(tools, toolsParams);
        tools.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("أدوات التشغيل").setItems(new String[]{"تبديل التشغيل العشوائي", "تبديل التكرار", "فتح معادل الصوت", "مؤقت النوم"}, (d, which) -> {
            if (which == 0) startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_SHUFFLE));
            else if (which == 1) startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_REPEAT));
            else if (which == 2) startServiceCompat(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_EQUALIZER));
            else showSleepTimer();
        }).show());
        if (current >= 0 && current < songs.size()) { fullTitle.setText(songs.get(current).title); fullArtist.setText(songs.get(current).artist); }
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).setNegativeButton("إغلاق", null).create();
        fullPlayerDialog = dialog;
        dialog.setOnDismissListener(d -> {
            fullPlayerDialog = null; progressBar = null; fullTitle = null; fullArtist = null; elapsedText = null; durationText = null;
            playerUiHandler.removeCallbacks(playerUiUpdater);
            if (playerBackgroundAnimator != null) { playerBackgroundAnimator.cancel(); playerBackgroundAnimator = null; }
            if (coverPulseAnimator != null) { coverPulseAnimator.cancel(); coverPulseAnimator = null; }
        });
        dialog.show();
        if (dialog.getWindow() != null) { dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.getWindow().setLayout(-1, -1); }
        playerUiHandler.removeCallbacks(playerUiUpdater); playerUiHandler.post(playerUiUpdater);
    }

    private TextView playerControl(String label, int size) {
        TextView t = new TextView(this); t.setText(label); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setGravity(Gravity.CENTER);
        GradientDrawable controlBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(126, 92, 246), Color.rgb(94, 67, 203)}); controlBg.setCornerRadius(dp(24)); controlBg.setStroke(dp(1), Color.rgb(187, 166, 255)); t.setBackground(controlBg); return t;
    }

    private final Runnable playerUiUpdater = new Runnable() {
        @Override public void run() {
            if (fullPlayerDialog == null || !fullPlayerDialog.isShowing()) return;
            startServiceCompat(new Intent(MainActivity.this, PlaybackService.class).setAction(PlaybackService.ACTION_GET_PROGRESS));
            playerUiHandler.postDelayed(this, 1000);
        }
    };

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
