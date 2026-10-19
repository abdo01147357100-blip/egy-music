package com.egymusic.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.Random;

public class PlaybackService extends Service {
    public static final String ACTION_PLAY = "com.egymusic.player.PLAY";
    public static final String ACTION_TOGGLE = "com.egymusic.player.TOGGLE";
    public static final String ACTION_NEXT = "com.egymusic.player.NEXT";
    public static final String ACTION_PREVIOUS = "com.egymusic.player.PREVIOUS";
    public static final String ACTION_SHUFFLE = "com.egymusic.player.SHUFFLE";
    public static final String ACTION_REPEAT = "com.egymusic.player.REPEAT";
    public static final String ACTION_EQUALIZER = "com.egymusic.player.EQUALIZER";
    public static final String ACTION_SLEEP_TIMER = "com.egymusic.player.SLEEP_TIMER";
    public static final String EXTRA_TIMER_MINUTES = "timer_minutes";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_URIS = "uris";
    public static final String EXTRA_TITLES = "titles";
    public static final String EXTRA_ARTISTS = "artists";
    private static final String CHANNEL_ID = "egy_music_playback";
    private static final int NOTIFICATION_ID = 1001;

    private MediaPlayer player;
    private MediaSession session;
    private final ArrayList<String> uris = new ArrayList<>();
    private final ArrayList<String> titles = new ArrayList<>();
    private final ArrayList<String> artists = new ArrayList<>();
    private int current = -1;
    private boolean prepared = false;
    private boolean shuffleEnabled = false;
    private boolean repeatEnabled = false;
    private final Random random = new Random();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        session = new MediaSession(this, "EGY MUSIC");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resumePlayback(); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onSkipToNext() { playNext(); }
            @Override public void onSkipToPrevious() { playPrevious(); }
            @Override public void onStop() { stopPlayback(); }
        });
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setActive(true);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            String[] newUris = intent.getStringArrayExtra(EXTRA_URIS);
            String[] newTitles = intent.getStringArrayExtra(EXTRA_TITLES);
            String[] newArtists = intent.getStringArrayExtra(EXTRA_ARTISTS);
            if (newUris != null && newTitles != null && newArtists != null) {
                uris.clear(); titles.clear(); artists.clear();
                for (String value : newUris) uris.add(value);
                for (String value : newTitles) titles.add(value);
                for (String value : newArtists) artists.add(value);
            }
            playIndex(intent.getIntExtra(EXTRA_INDEX, 0));
        } else if (ACTION_TOGGLE.equals(action)) {
            if (player == null && current >= 0) resumePlayback();
            else if (player == null && !uris.isEmpty()) playIndex(0);
            else if (player != null && prepared && player.isPlaying()) pausePlayback();
            else resumePlayback();
        } else if (ACTION_NEXT.equals(action)) {
            playNext();
        } else if (ACTION_PREVIOUS.equals(action)) {
            playPrevious();
        } else if (ACTION_SHUFFLE.equals(action)) {
            shuffleEnabled = !shuffleEnabled;
            Log.i("EGY MUSIC", "Shuffle enabled: " + shuffleEnabled);
        } else if (ACTION_REPEAT.equals(action)) {
            repeatEnabled = !repeatEnabled;
            Log.i("EGY MUSIC", "Repeat enabled: " + repeatEnabled);
        } else if (ACTION_EQUALIZER.equals(action)) {
            openEqualizer();
        } else if (ACTION_SLEEP_TIMER.equals(action)) {
            int minutes = intent.getIntExtra(EXTRA_TIMER_MINUTES, 0);
            if (sleepTimerRunnable != null) timerHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
            if (minutes > 0) {
                sleepTimerRunnable = () -> {
                    pausePlayback();
                    stopForeground(true);
                    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (manager != null) manager.cancel(NOTIFICATION_ID);
                    sleepTimerRunnable = null;
                    stopSelf();
                };
                timerHandler.postDelayed(sleepTimerRunnable, minutes * 60_000L);
            }
        }
        return START_STICKY;
    }

    private void openEqualizer() {
        try {
            Intent intent = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, player != null ? player.getAudioSessionId() : 0);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            else android.widget.Toast.makeText(this, "معادل الصوت غير متاح على هذا الجهاز", android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w("EGY MUSIC", "Equalizer panel unavailable", e);
            android.widget.Toast.makeText(this, "تعذر فتح معادل الصوت على هذا الجهاز", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void playIndex(int index) {
        if (index < 0 || index >= uris.size()) {
            if (player != null && prepared) pausePlayback();
            return;
        }
        current = index;
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this, Uri.parse(uris.get(index)));
            player.setOnPreparedListener(mp -> {
                if (player != mp) return;
                prepared = true;
                mp.start();
                updateSession(PlaybackState.STATE_PLAYING);
                showNotification(true);
            });
            player.setOnCompletionListener(mp -> {
                if (repeatEnabled && current >= 0) playIndex(current);
                else if (shuffleEnabled && uris.size() > 1) playIndex(random.nextInt(uris.size()));
                else if (current + 1 < uris.size()) playIndex(current + 1);
                else {
                    updateSession(PlaybackState.STATE_STOPPED);
                    showNotification(false);
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e("EGY MUSIC", "MediaPlayer error: " + what + "/" + extra);
                prepared = false;
                updateSession(PlaybackState.STATE_ERROR);
                showNotification(false);
                return true;
            });
            updateSession(PlaybackState.STATE_CONNECTING);
            startForeground(NOTIFICATION_ID, buildNotification(false));
            player.prepareAsync();
        } catch (Exception e) {
            Log.e("EGY MUSIC", "Unable to play audio", e);
            releasePlayer();
            stopForeground(true);
        }
    }

    private void playNext() {
        if (uris.isEmpty()) return;
        if (shuffleEnabled && uris.size() > 1) {
            int next = current;
            while (next == current) next = random.nextInt(uris.size());
            playIndex(next);
        } else if (current + 1 < uris.size()) playIndex(current + 1);
        else if (repeatEnabled) playIndex(0);
        else if (player != null && prepared) pausePlayback();
    }

    private void playPrevious() {
        if (uris.isEmpty()) return;
        if (shuffleEnabled && uris.size() > 1) {
            int previous = current;
            while (previous == current) previous = random.nextInt(uris.size());
            playIndex(previous);
        } else if (current > 0) playIndex(current - 1);
        else if (repeatEnabled) playIndex(uris.size() - 1);
    }

    private void resumePlayback() {
        if (player != null && prepared) {
            try { player.start(); updateSession(PlaybackState.STATE_PLAYING); showNotification(true); }
            catch (Exception e) { Log.e("EGY MUSIC", "Resume failed", e); }
        } else if (current >= 0) playIndex(current);
        else if (!uris.isEmpty()) playIndex(0);
    }

    private void pausePlayback() {
        if (player != null && prepared) {
            try { player.pause(); } catch (Exception ignored) {}
        }
        updateSession(PlaybackState.STATE_PAUSED);
        showNotification(false);
    }

    private void stopPlayback() {
        if (player != null && prepared) {
            try { player.pause(); player.seekTo(0); } catch (Exception ignored) {}
        }
        updateSession(PlaybackState.STATE_STOPPED);
        showNotification(false);
    }

    private void updateSession(int state) {
        if (session == null) return;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        PlaybackState playbackState = new PlaybackState.Builder().setActions(actions).setState(state,
                player != null && prepared ? player.getCurrentPosition() : 0, state == PlaybackState.STATE_PLAYING ? 1f : 0f).build();
        session.setPlaybackState(playbackState);
        if (current >= 0 && current < titles.size()) {
            session.setMetadata(new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, titles.get(current))
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, artists.get(current))
                    .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,
                            player != null && prepared ? player.getDuration() : 0).build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "تشغيل الموسيقى", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("أدوات التحكم في تشغيل EGY MUSIC");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(boolean playing) {
        String title = current >= 0 && current < titles.size() ? titles.get(current) : "EGY MUSIC";
        String artist = current >= 0 && current < artists.size() ? artists.get(current) : "مشغل الموسيقى";
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 10, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title).setContentText(artist)
                .setContentIntent(contentIntent).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true).setOngoing(playing)
                .addAction(action(android.R.drawable.ic_media_previous, "السابق", ACTION_PREVIOUS, 11))
                .addAction(action(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "إيقاف مؤقت" : "تشغيل", ACTION_TOGGLE, 12))
                .addAction(action(android.R.drawable.ic_media_next, "التالي", ACTION_NEXT, 13));
        if (Build.VERSION.SDK_INT >= 21 && session != null) {
            builder.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    private Notification.Action action(int icon, String title, String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        PendingIntent pending = PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        return new Notification.Action.Builder(icon, title, pending).build();
    }

    private void showNotification(boolean playing) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(playing));
        startForeground(NOTIFICATION_ID, buildNotification(playing));
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.setOnPreparedListener(null); player.setOnCompletionListener(null); player.setOnErrorListener(null); player.release(); }
            catch (Exception ignored) {}
            player = null;
        }
        prepared = false;
    }

    @Override public void onDestroy() {
        if (sleepTimerRunnable != null) timerHandler.removeCallbacks(sleepTimerRunnable);
        releasePlayer();
        if (session != null) { session.setActive(false); session.release(); }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
