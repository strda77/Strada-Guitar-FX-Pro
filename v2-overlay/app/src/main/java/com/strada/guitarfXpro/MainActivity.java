package com.strada.guitarfXpro;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Strada Guitar FX Pro - self-contained Java UI and basic real-time DSP engine.
 *
 * IMPORTANT: this is a functional prototype engine, not a certified/pro-grade amp modeller.
 * Amp/cab blocks are algorithmic approximations, not captured amp profiles or IR convolution.
 * Test with headphones and the actual target Android devices before publishing.
 */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 10, 18);
    private static final int PANEL = Color.rgb(15, 22, 36);
    private static final int TEXT = Color.rgb(238, 246, 255);
    private static final int MUTED = Color.rgb(151, 170, 195);
    private static final int CYAN = Color.rgb(0, 210, 255);
    private static final int PURPLE = Color.rgb(190, 70, 255);
    private static final int GREEN = Color.rgb(30, 230, 115);
    private static final int ORANGE = Color.rgb(255, 160, 30);
    private static final int BLUE = Color.rgb(35, 130, 255);
    private static final int RED = Color.rgb(255, 60, 75);
    private static final int REQUEST_AUDIO = 1001;
    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES_PER_READ = 256;
    private static final int EFFECT_COUNT = 8;
    private static final int PARAMS_PER_EFFECT = 3;

    private final String[] presetNames = {
        "Clean Glass", "Warm Clean", "Studio Clean", "Bright Clean", "Jazz Clean",
        "Chime Clean", "Blues Drive", "Vintage Blues", "Texas Blues", "Crunch Blues",
        "Classic Rock", "Arena Rock", "British Crunch", "Classic Lead", "British Lead",
        "Modern Lead", "Hard Rock", "Power Rock", "Vintage Stack", "Hot Stack",
        "Metal Power", "Classic Metal", "Heavy Metal", "Modern Metal", "Thrash Metal",
        "Dark Metal", "Tight Metal", "Djent Drive", "Fuzz Garage", "Vintage Fuzz",
        "Octave Fuzz", "Smooth Solo", "Singing Lead", "Sustain Lead", "Wide Chorus",
        "Dream Chorus", "Clean Chorus", "Slapback Rock", "Tape Echo", "Digital Echo",
        "Ambient Space", "Deep Ambient", "Shimmer Space", "Dark Ambient", "Lo-Fi Drive",
        "Indie Drive", "Alternative Rock", "Grunge Drive", "Punk Crunch", "Experimental",
        "Acoustic Shine", "Pop Lead", "Country Twang", "Funk Clean", "Fusion Lead",
        "Doom Fuzz", "Post Rock", "Shoegaze Wall", "Classic Solo", "Modern Solo"
    };

    private final String[] effectNames = {
        "GATE", "COMP", "OVERDRIVE", "DISTORTION",
        "DISTORTION+", "MODULATION", "DELAY", "REVERB"
    };
    private final int[] effectColors = {
        CYAN, BLUE, GREEN, RED, PURPLE, Color.rgb(220, 55, 220),
        Color.rgb(0, 220, 230), ORANGE
    };
    private final String[][] paramNames = {
        {"Threshold", "Release"},
        {"Sustain", "Level"},
        {"Drive", "Tone", "Level"},
        {"Gain", "Tone", "Level"},
        {"Gain", "Tone", "Level"},
        {"Rate", "Depth", "Mix"},
        {"Time", "Feedback", "Mix"},
        {"Decay", "Tone", "Mix"}
    };
    private final String[] ampNames = {
        "Clean Twin", "Warm Clean", "Blues Combo", "American Vintage",
        "British Crunch", "Classic Rock", "British Lead", "Modern High Gain",
        "Metal Extreme", "Jazz Clean", "Boutique Overdrive", "Vintage Stack"
    };
    private final String[] cabNames = {
        "1x8 Small Speaker", "1x10 Vintage", "1x12 Open Back",
        "2x12 Classic", "4x10 Bass Cabinet", "4x12 British Stack",
        "4x12 Modern V30", "2x12 Blue Alnico", "1x12 Boutique"
    };
    private final String[] inputNames = {
        "Microphone (HP)", "USB Audio Interface", "Wired Headset Mic"
    };
    private final String[] outputNames = {
        "System / Headphone", "Speaker", "USB Audio Output"
    };

    // Atomic values make controls safely visible to the audio thread without locking each sample.
    private final AtomicIntegerArray effectEnabled = new AtomicIntegerArray(EFFECT_COUNT);
    private final AtomicIntegerArray effectParams = new AtomicIntegerArray(EFFECT_COUNT * PARAMS_PER_EFFECT);

    private LinearLayout root;
    private LinearLayout pageContainer;
    private LinearLayout bottomNavigation;
    private ScrollView mainScroll;
    private String currentPage = "HOME";

    private TextView presetTitle;
    private TextView audioStatus;
    private TextView masterValue;
    private TextView tunerNote;
    private TextView loopStatus;
    private Button audioToggleButton;
    private Button looperRecordButton;
    private Button looperPlayButton;
    private Button looperAudioButton;
    private SeekBar masterSeek;
    private Spinner presetSpinner;
    private Spinner ampSpinner;
    private Spinner cabSpinner;
    private Spinner inputSpinner;
    private Spinner outputSpinner;
    private SeekBar[][] effectSeekBars = new SeekBar[EFFECT_COUNT][PARAMS_PER_EFFECT];
    private TextView[][] effectParamValues = new TextView[EFFECT_COUNT][PARAMS_PER_EFFECT];
    private PedalFaceView[] pedalViews = new PedalFaceView[EFFECT_COUNT];

    private volatile boolean audioRunning = false;
    private volatile boolean audioStopping = false;
    private volatile Thread audioThread;
    private volatile String audioMessage = "Status: audio belum berjalan";
    private volatile float masterVolume = 0.72f;
    private volatile float ampGain = 3.0f;
    private volatile float ampBass = 0.50f;
    private volatile float ampMiddle = 0.50f;
    private volatile float ampTreble = 0.60f;
    private volatile float ampVolume = 0.70f;
    private volatile boolean ampOn = true;
    private volatile boolean cabOn = true;
    private volatile boolean loopRecording = false;
    private volatile boolean loopPlaying = false;
    private volatile int loopLength = 0;
    private volatile int loopWritePos = 0;
    private volatile int loopReadPos = 0;
    private volatile int selectedPreset = 0;
    private volatile int selectedAmp = 0;
    private volatile int selectedCab = 5;
    private volatile float tunerFrequency = 0f;
    private volatile String selectedInput = "Microphone (HP)";
    private volatile String selectedOutput = "System / Headphone";

    // Up to 30 seconds of mono, 16-bit loop audio (about 2.6 MB).
    private final short[] loopBuffer = new short[SAMPLE_RATE * 30];

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        for (int i = 0; i < effectParams.length(); i++) effectParams.set(i, 50);
        applyPresetValues(0);
        buildUi();
    }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);

        pageContainer = new LinearLayout(this);
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        pageContainer.setPadding(dp(12), dp(8), dp(12), dp(14));
        pageContainer.setBackgroundColor(BG);

        mainScroll = new ScrollView(this);
        mainScroll.setFillViewport(true);
        mainScroll.setBackgroundColor(BG);
        mainScroll.addView(pageContainer);
        screen.addView(mainScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomNavigation = new LinearLayout(this);
        bottomNavigation.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavigation.setGravity(Gravity.CENTER);
        bottomNavigation.setPadding(dp(3), dp(4), dp(3), dp(4));
        bottomNavigation.setBackgroundColor(Color.rgb(18, 24, 37));
        screen.addView(bottomNavigation, new LinearLayout.LayoutParams(-1, dp(62)));

        setContentView(screen);
        root = pageContainer;
        showPage("HOME");
        setupNavigation();
    }

    private void showPage(String page) {
        currentPage = page;
        if (pageContainer == null) return;
        pageContainer.removeAllViews();
        root = pageContainer;
        presetTitle = null;
        audioStatus = null;
        masterValue = null;
        tunerNote = null;
        loopStatus = null;
        audioToggleButton = null;
        looperRecordButton = null;
        looperPlayButton = null;
        looperAudioButton = null;
        masterSeek = null;
        presetSpinner = null;
        ampSpinner = null;
        cabSpinner = null;
        inputSpinner = null;
        outputSpinner = null;
        effectSeekBars = new SeekBar[EFFECT_COUNT][PARAMS_PER_EFFECT];
        effectParamValues = new TextView[EFFECT_COUNT][PARAMS_PER_EFFECT];
        pedalViews = new PedalFaceView[EFFECT_COUNT];

        switch (page) {
            case "HOME": buildHomePage(); break;
            case "PEDALBOARD": buildPedalboardPage(); break;
            case "AMP & CAB": buildAmpCabPage(); break;
            case "TUNER": buildTunerPage(); break;
            case "LOOPER": buildLooperPage(); break;
            case "PRESETS": buildPresetsPage(); break;
            case "AUDIO": buildAudioPage(); break;
            default: section("STRADA", "Halaman tidak ditemukan."); break;
        }
        if (mainScroll != null) mainScroll.post(() -> mainScroll.scrollTo(0, 0));
    }

    private void buildHomePage() {
        TextView logo = text("STRADA", 34, CYAN, true);
        logo.setLetterSpacing(0.14f);
        logo.setPadding(dp(4), dp(8), dp(4), 0);
        root.addView(logo);
        TextView subtitle = text("GUITAR FX PRO  •  REAL-TIME EFFECTS", 12, PURPLE, true);
        subtitle.setPadding(dp(5), 0, 0, dp(10));
        root.addView(subtitle);

        LinearLayout hero = card(CYAN);
        root.addView(hero);
        sectionIn(hero, "MAIN CONTROL", "Preset, master output, and audio engine");
        presetTitle = text("PRESET  •  " + presetNames[selectedPreset], 16, TEXT, true);
        hero.addView(presetTitle);
        Spinner s = spinner(presetNames);
        presetSpinner = s;
        s.setSelection(selectedPreset);
        hero.addView(s, new LinearLayout.LayoutParams(-1, dp(48)));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) { first = false; return; }
                applyPresetValues(position);
                updatePresetLabels();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        addControl(hero, "MASTER VOLUME", 0, 100, Math.round(masterVolume * 100), CYAN,
                value -> { masterVolume = value / 100f; updateMasterValue(value); });
        masterValue = text("Master: " + Math.round(masterVolume * 100) + "%", 12, CYAN, true);
        hero.addView(masterValue);
        addAudioToggle(hero);
        audioStatus = text(audioMessage, 11, MUTED, false);
        audioStatus.setPadding(0, dp(7), 0, dp(2));
        hero.addView(audioStatus);

        section("QUICK ACCESS", "Buka modul yang ingin kamu atur.");
        addHomeButton("PEDALBOARD  →", CYAN, "PEDALBOARD");
        addHomeButton("AMPLIFIER & CABINET  →", PURPLE, "AMP & CAB");
        addHomeButton("GUITAR TUNER  →", GREEN, "TUNER");
        addHomeButton("LOOPER  →", ORANGE, "LOOPER");
        addHomeButton("PRESET LIBRARY  •  60 TONES  →", BLUE, "PRESETS");
        addHomeButton("AUDIO INPUT / OUTPUT  →", RED, "AUDIO");
        TextView note = text("Tip: gunakan headphone/earphone untuk mengurangi feedback dari mikrofon HP.", 10, MUTED, false);
        note.setPadding(dp(2), dp(12), dp(2), dp(8));
        root.addView(note);
    }

    private void addHomeButton(String label, int accent, String page) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(12), 0, dp(8), 0);
        b.setBackground(bgShape(PANEL, accent));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(4), 0, dp(4));
        root.addView(b, lp);
        b.setOnClickListener(v -> navigate(page));
    }

    private void buildPedalboardPage() {
        section("FULL COLOR PEDALBOARD", "Ketuk kenop pada gambar atau gunakan slider di bawahnya. Sakelar pedal mengubah ON/OFF.");
        for (int i = 0; i < effectNames.length; i++) addEffectCard(i);
    }

    private void addEffectCard(int id) {
        if (id < 0 || id >= EFFECT_COUNT) return;
        PedalFaceView pedal = new PedalFaceView(this, id);
        pedalViews[id] = pedal;
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(205));
        pp.setMargins(0, dp(7), 0, dp(2));
        root.addView(pedal, pp);
        addEffectControls(id);
    }

    private void addEffectControls(int id) {
        int accent = effectColors[id];
        LinearLayout c = card(accent);
        root.addView(c);
        LinearLayout head = row();
        c.addView(head);
        TextView title = text(effectNames[id] + "  PARAMETERS", 14, accent, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button toggle = new Button(this);
        styleButton(toggle, isEffectOn(id) ? "ON" : "OFF", accent);
        head.addView(toggle, new LinearLayout.LayoutParams(dp(78), dp(40)));
        toggle.setOnClickListener(v -> {
            setEffectOn(id, !isEffectOn(id));
            toggle.setText(isEffectOn(id) ? "ON" : "OFF");
            toggle.setBackground(bgShape(isEffectOn(id) ? accent : Color.rgb(43, 49, 65), accent));
            if (pedalViews[id] != null) pedalViews[id].invalidate();
        });
        c.addView(text(effectDescription(id), 10, MUTED, false));
        for (int j = 0; j < paramNames[id].length; j++) addEffectParamControl(c, id, j, accent);
    }

    private void addEffectParamControl(LinearLayout parent, int id, int paramIndex, int accent) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, dp(4), 0, dp(2));
        parent.addView(line);
        int initial = getParam(id, paramIndex);
        TextView value = text(paramNames[id][paramIndex] + "  " + initial, 11, TEXT, true);
        line.addView(value);
        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(initial);
        tintSeek(seek, accent);
        line.addView(seek);
        effectSeekBars[id][paramIndex] = seek;
        effectParamValues[id][paramIndex] = value;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                setParam(id, paramIndex, progress);
                value.setText(paramNames[id][paramIndex] + "  " + progress);
                if (pedalViews[id] != null) pedalViews[id].invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void buildAmpCabPage() {
        section("AMPLIFIER & CABINET", "Model amp dan cabinet ini adalah simulasi DSP sederhana, bukan capture/IR profesional.");
        LinearLayout ampCard = card(PURPLE);
        root.addView(ampCard);
        addToggleRow(ampCard, "AMPLIFIER BLOCK", ampOn, value -> ampOn = value, PURPLE);
        ampCard.addView(text("AMP MODEL", 11, MUTED, true));
        ampSpinner = spinner(ampNames);
        ampSpinner.setSelection(selectedAmp);
        ampCard.addView(ampSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        ampSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selectedAmp = position; }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        addControl(ampCard, "AMP GAIN", 0, 100, gainToUi(ampGain), PURPLE,
                value -> ampGain = 0.5f + value * 0.075f);
        addControl(ampCard, "AMP VOLUME", 0, 100, Math.round(ampVolume * 100), PURPLE,
                value -> ampVolume = value / 100f);
        addControl(ampCard, "BASS", 0, 100, Math.round(ampBass * 100), PURPLE,
                value -> ampBass = value / 100f);
        addControl(ampCard, "MIDDLE", 0, 100, Math.round(ampMiddle * 100), PURPLE,
                value -> ampMiddle = value / 100f);
        addControl(ampCard, "TREBLE", 0, 100, Math.round(ampTreble * 100), PURPLE,
                value -> ampTreble = value / 100f);

        LinearLayout cabCard = card(CYAN);
        root.addView(cabCard);
        addToggleRow(cabCard, "CABINET FILTER", cabOn, value -> cabOn = value, CYAN);
        cabCard.addView(text("CABINET MODEL", 11, MUTED, true));
        cabSpinner = spinner(cabNames);
        cabSpinner.setSelection(selectedCab);
        cabCard.addView(cabSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        cabSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selectedCab = position; }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        cabCard.addView(text("Catatan: nama model saat ini memilih karakter filter dasar. Belum ada IR loader atau model amplifier berbasis capture.", 10, MUTED, false));
    }

    private void buildTunerPage() {
        section("CHROMATIC GUITAR TUNER", "Deteksi pitch dari input mic/audio yang sedang berjalan. Hasil dipengaruhi noise dan kualitas input.");
        LinearLayout tunerCard = card(GREEN);
        root.addView(tunerCard);
        tunerNote = text(tunerFrequency > 0 ? noteText(tunerFrequency) : "--\nMenunggu sinyal audio", 28, GREEN, true);
        tunerNote.setGravity(Gravity.CENTER);
        tunerNote.setPadding(0, dp(28), 0, dp(28));
        tunerCard.addView(tunerNote, new LinearLayout.LayoutParams(-1, dp(150)));
        Button start = new Button(this);
        audioToggleButton = start;
        styleButton(start, audioRunning ? "STOP AUDIO ENGINE" : "START MIC / USB AUDIO", GREEN);
        tunerCard.addView(start, new LinearLayout.LayoutParams(-1, dp(48)));
        start.setOnClickListener(v -> toggleAudio());
        TextView advice = text("Untuk penyeteman yang lebih stabil: matikan efek, dekatkan sumber ke input, dan hindari suara speaker masuk kembali ke mikrofon.", 11, MUTED, false);
        advice.setPadding(0, dp(12), 0, dp(4));
        tunerCard.addView(advice);
        audioStatus = text(audioMessage, 10, MUTED, false);
        tunerCard.addView(audioStatus);
    }

    private void buildLooperPage() {
        section("30-SECOND LOOPER", "Merekam sinyal yang sudah diproses dan memutarnya berulang. Belum mendukung overdub.");
        LinearLayout c = card(ORANGE);
        root.addView(c);
        loopStatus = text(looperText(), 16, ORANGE, true);
        loopStatus.setPadding(0, dp(12), 0, dp(18));
        c.addView(loopStatus);
        Button record = new Button(this);
        looperRecordButton = record;
        styleButton(record, loopRecording ? "STOP RECORDING" : "RECORD LOOP", ORANGE);
        c.addView(record, new LinearLayout.LayoutParams(-1, dp(48)));
        record.setOnClickListener(v -> {
            if (!audioRunning) {
                Toast.makeText(this, "Jalankan audio engine terlebih dahulu.", Toast.LENGTH_LONG).show();
                return;
            }
            if (!loopRecording) {
                loopPlaying = false;
                loopLength = 0;
                loopWritePos = 0;
                loopRecording = true;
                Toast.makeText(this, "Recording loop dimulai", Toast.LENGTH_SHORT).show();
            } else {
                loopRecording = false;
                loopLength = loopWritePos;
                loopReadPos = 0;
                Toast.makeText(this, "Loop disimpan: " + String.format(Locale.US, "%.1f", loopLength / (float) SAMPLE_RATE) + " detik", Toast.LENGTH_SHORT).show();
            }
            refreshLooperUi();
        });
        Button play = new Button(this);
        looperPlayButton = play;
        styleButton(play, loopPlaying ? "STOP LOOP" : "PLAY LOOP", CYAN);
        c.addView(play, new LinearLayout.LayoutParams(-1, dp(48)));
        play.setOnClickListener(v -> {
            if (loopPlaying) {
                loopPlaying = false;
            } else if (loopLength > 0 && audioRunning) {
                loopReadPos = 0;
                loopPlaying = true;
            } else {
                Toast.makeText(this, "Rekam loop dan jalankan audio terlebih dahulu.", Toast.LENGTH_LONG).show();
            }
            refreshLooperUi();
        });
        Button clear = new Button(this);
        styleButton(clear, "CLEAR LOOP", RED);
        c.addView(clear, new LinearLayout.LayoutParams(-1, dp(48)));
        clear.setOnClickListener(v -> {
            loopRecording = false;
            loopPlaying = false;
            loopLength = 0;
            loopWritePos = 0;
            loopReadPos = 0;
            refreshLooperUi();
        });
        Button startAudio = new Button(this);
        looperAudioButton = startAudio;
        audioToggleButton = startAudio;
        styleButton(startAudio, audioRunning ? "STOP AUDIO ENGINE" : "START AUDIO ENGINE", BLUE);
        c.addView(startAudio, new LinearLayout.LayoutParams(-1, dp(48)));
        startAudio.setOnClickListener(v -> toggleAudio());
        audioStatus = text(audioMessage, 10, MUTED, false);
        audioStatus.setPadding(0, dp(10), 0, 0);
        c.addView(audioStatus);
    }

    private void buildPresetsPage() {
        section("PRESET LIBRARY", "60 preset dasar. Memilih preset mengubah state efek dan amp; hasil suara tetap bergantung pada input/output perangkat.");
        LinearLayout c = card(BLUE);
        root.addView(c);
        presetTitle = text("ACTIVE  •  " + presetNames[selectedPreset], 16, CYAN, true);
        c.addView(presetTitle);
        presetSpinner = spinner(presetNames);
        presetSpinner.setSelection(selectedPreset);
        c.addView(presetSpinner, new LinearLayout.LayoutParams(-1, dp(50)));
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) { first = false; return; }
                applyPresetValues(position);
                updatePresetLabels();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        Button load = new Button(this);
        styleButton(load, "LOAD SELECTED PRESET", BLUE);
        c.addView(load, new LinearLayout.LayoutParams(-1, dp(48)));
        load.setOnClickListener(v -> {
            applyPresetValues(presetSpinner.getSelectedItemPosition());
            updatePresetLabels();
            Toast.makeText(this, "Preset diterapkan: " + presetNames[selectedPreset], Toast.LENGTH_SHORT).show();
        });
        TextView info = text("Preset dapat kamu ubah lagi dari halaman Pedalboard dan Amp/Cab. Perubahan belum disimpan sebagai preset pengguna setelah aplikasi ditutup.", 10, MUTED, false);
        info.setPadding(0, dp(12), 0, dp(4));
        c.addView(info);
        LinearLayout bank = card(PURPLE);
        root.addView(bank);
        sectionIn(bank, "PRESET BANKS", "Gunakan tombol di bawah untuk berpindah cepat antar kelompok.");
        for (int start = 0; start < presetNames.length; start += 10) {
            final int bankStart = start;
            Button b = new Button(this);
            styleButton(b, String.format(Locale.US, "%02d–%02d  •  %s", start + 1, Math.min(start + 10, presetNames.length), presetNames[start]), PURPLE);
            bank.addView(b, new LinearLayout.LayoutParams(-1, dp(44)));
            b.setOnClickListener(v -> {
                int target = bankStart;
                applyPresetValues(target);
                if (presetSpinner != null) presetSpinner.setSelection(target);
                updatePresetLabels();
            });
        }
    }

    private void buildAudioPage() {
        section("AUDIO INPUT / OUTPUT", "Audio engine memakai AudioRecord dan AudioTrack pada 44,1 kHz mono. Pilihan USB meminta routing bila Android/perangkat mendukungnya.");
        LinearLayout c = card(CYAN);
        root.addView(c);
        c.addView(text("INPUT DEVICE", 11, MUTED, true));
        inputSpinner = spinner(inputNames);
        inputSpinner.setSelection(indexOf(inputNames, selectedInput));
        c.addView(inputSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        inputSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedInput = inputNames[position];
                if (audioRunning) setAudioMessage("Input berubah. Stop lalu start audio untuk menerapkan routing: " + selectedInput);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        c.addView(text("OUTPUT DEVICE", 11, MUTED, true));
        outputSpinner = spinner(outputNames);
        outputSpinner.setSelection(indexOf(outputNames, selectedOutput));
        c.addView(outputSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        outputSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedOutput = outputNames[position];
                if (audioRunning) setAudioMessage("Output berubah. Stop lalu start audio untuk menerapkan routing: " + selectedOutput);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        addAudioToggle(c);
        audioStatus = text(audioMessage, 11, MUTED, false);
        audioStatus.setPadding(0, dp(8), 0, dp(8));
        c.addView(audioStatus);
        TextView limitations = text("Perhatian: Android menentukan ketersediaan perangkat audio eksternal. Jika USB tidak terdeteksi atau tidak dapat di-route oleh sistem, aplikasi tidak bisa memaksakan input/output tersebut. Bluetooth audio umumnya memiliki latensi yang kurang cocok untuk monitoring gitar real-time.", 10, MUTED, false);
        limitations.setPadding(0, dp(10), 0, dp(5));
        c.addView(limitations);
    }

    private void addAudioToggle(LinearLayout parent) {
        Button b = new Button(this);
        audioToggleButton = b;
        styleButton(b, audioRunning ? "STOP AUDIO ENGINE" : "START MIC / USB AUDIO", audioRunning ? RED : GREEN);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(7), 0, dp(3));
        parent.addView(b, lp);
        b.setOnClickListener(v -> toggleAudio());
    }

    private void addControl(LinearLayout parent, String label, int min, int max, int initial,
                            int accent, IntChanged changed) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, dp(5), 0, dp(3));
        parent.addView(line);
        TextView value = text(label + "  " + initial, 11, TEXT, true);
        line.addView(value);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(initial - min);
        tintSeek(seek, accent);
        line.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int result = progress + min;
                value.setText(label + "  " + result);
                changed.onChanged(result);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private interface IntChanged { void onChanged(int value); }
    private interface ToggleChanged { void onChanged(boolean value); }

    private void addToggleRow(LinearLayout parent, String label, boolean initial,
                              ToggleChanged changed, int accent) {
        LinearLayout r = row();
        parent.addView(r);
        TextView name = text(label, 12, TEXT, true);
        r.addView(name, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button b = new Button(this);
        styleButton(b, initial ? "ON" : "OFF", accent);
        r.addView(b, new LinearLayout.LayoutParams(dp(80), dp(42)));
        b.setOnClickListener(v -> {
            boolean next = !initial;
            // initial is captured; maintain state with the button's current label instead.
            next = !"ON".equals(b.getText().toString());
            b.setText(next ? "ON" : "OFF");
            b.setBackground(bgShape(next ? accent : Color.DKGRAY, accent));
            changed.onChanged(next);
        });
        changed.onChanged(initial);
    }

    private void toggleAudio() {
        if (audioRunning) {
            audioRunning = false;
            audioStopping = true;
            setAudioMessage("Status: menghentikan audio...");
            return;
        }
        if (audioThread != null && audioThread.isAlive()) {
            setAudioMessage("Audio masih berhenti. Tunggu sebentar lalu coba lagi.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        audioRunning = true;
        audioStopping = false;
        setAudioMessage("Status: memulai audio • " + selectedInput + " → " + selectedOutput);
        audioThread = new Thread(this::runAudio, "StradaAudioEngine");
        audioThread.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) toggleAudio();
            else Toast.makeText(this, "Izin mikrofon diperlukan untuk memproses sinyal gitar.", Toast.LENGTH_LONG).show();
        }
    }

    private void runAudio() {
        AudioRecord recorder = null;
        AudioTrack track = null;
        String failure = null;
        try {
            int inMin = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int outMin = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (inMin <= 0 || outMin <= 0) throw new IllegalStateException("Format audio 44.1 kHz tidak didukung perangkat");
            int bufferBytes = Math.max(Math.max(inMin, outMin), FRAMES_PER_READ * 8);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes, AudioTrack.MODE_STREAM);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED || track.getState() != AudioTrack.STATE_INITIALIZED)
                throw new IllegalStateException("AudioRecord/AudioTrack gagal diinisialisasi");
            requestPreferredDevices(recorder, track);

            short[] in = new short[FRAMES_PER_READ];
            short[] out = new short[FRAMES_PER_READ];
            float[] delayBuf = new float[SAMPLE_RATE];
            float[] reverbBuf = new float[SAMPLE_RATE / 4];
            float[] modBuf = new float[SAMPLE_RATE / 8];
            int delayWrite = 0, reverbWrite = 0, modWrite = 0;
            float gateEnvelope = 0f, compEnvelope = 0f;
            float ampLow = 0f, ampMid = 0f, cabLow = 0f;
            float odToneState = 0f, distToneState = 0f, distPlusToneState = 0f;
            double modPhase = 0.0;
            PitchTracker pitchTracker = new PitchTracker();
            track.play();
            recorder.startRecording();
            setAudioMessage("Status: AUDIO RUNNING • gunakan headphone untuk mengurangi feedback");

            while (audioRunning) {
                int count = recorder.read(in, 0, in.length);
                if (count == AudioRecord.ERROR_INVALID_OPERATION || count == AudioRecord.ERROR_BAD_VALUE || count < 0)
                    throw new IllegalStateException("Mic read gagal: " + count);
                if (count == 0) continue;

                boolean[] on = new boolean[EFFECT_COUNT];
                int[][] p = new int[EFFECT_COUNT][PARAMS_PER_EFFECT];
                for (int e = 0; e < EFFECT_COUNT; e++) {
                    on[e] = effectEnabled.get(e) != 0;
                    for (int j = 0; j < PARAMS_PER_EFFECT; j++) p[e][j] = effectParams.get(e * PARAMS_PER_EFFECT + j);
                }
                float localMaster = masterVolume;
                float localAmpGain = ampGain;
                float localAmpVolume = ampVolume;
                float localBass = ampBass;
                float localMiddle = ampMiddle;
                float localTreble = ampTreble;
                int localAmpModel = selectedAmp;
                int localCabModel = selectedCab;
                boolean localAmpOn = ampOn;
                boolean localCabOn = cabOn;
                int delaySamples = clamp((int) ((0.03f + 0.0047f * p[6][0]) * SAMPLE_RATE), 1, delayBuf.length - 1);
                float delayFeedback = p[6][1] / 125f;
                float delayMix = p[6][2] / 100f;
                float reverbFeedback = 0.28f + p[7][0] / 180f;
                float reverbMix = p[7][2] / 100f;
                float modRate = 0.15f + (p[5][0] / 100f) * 5.0f;
                float modDepth = p[5][1] / 100f;
                float modMix = p[5][2] / 100f;

                for (int i = 0; i < count; i++) {
                    float x = in[i] / 32768f;

                    if (on[0]) {
                        float abs = Math.abs(x);
                        float attack = abs > gateEnvelope ? 0.25f : (0.003f + p[0][1] / 100f * 0.08f);
                        gateEnvelope += attack * (abs - gateEnvelope);
                        float threshold = 0.002f + p[0][0] / 100f * 0.095f;
                        if (gateEnvelope < threshold) x *= 0.025f;
                    }
                    if (on[1]) {
                        float abs = Math.abs(x);
                        compEnvelope += (abs > compEnvelope ? 0.18f : 0.012f) * (abs - compEnvelope);
                        float threshold = 0.38f - p[1][0] / 100f * 0.27f;
                        if (compEnvelope > threshold && compEnvelope > 0.0001f) {
                            float ratio = 1f + p[1][0] / 18f;
                            float target = (threshold + (compEnvelope - threshold) / ratio) / compEnvelope;
                            x *= target;
                        }
                        x *= 0.65f + p[1][1] / 100f * 0.7f;
                    }
                    if (on[2]) {
                        float drive = 1f + p[2][0] / 7.0f;
                        float shaped = (float) Math.tanh(x * drive);
                        float toneAlpha = 0.015f + p[2][1] / 100f * 0.22f;
                        odToneState += toneAlpha * (shaped - odToneState);
                        shaped = odToneState * (1f - p[2][1] / 150f) + shaped * (p[2][1] / 150f);
                        x = shaped * (0.5f + p[2][2] / 100f * 0.9f);
                    }
                    if (on[3]) {
                        float shaped = (float) Math.tanh(x * (1f + p[3][0] / 4.0f));
                        float toneAlpha = 0.015f + p[3][1] / 100f * 0.25f;
                        distToneState += toneAlpha * (shaped - distToneState);
                        shaped = distToneState * (1f - p[3][1] / 160f) + shaped * (p[3][1] / 160f);
                        x = shaped * (0.45f + p[3][2] / 100f * 0.95f);
                    }
                    if (on[4]) {
                        float shaped = (float) Math.tanh(x * (1.5f + p[4][0] / 3.2f));
                        float toneAlpha = 0.01f + p[4][1] / 100f * 0.28f;
                        distPlusToneState += toneAlpha * (shaped - distPlusToneState);
                        shaped = distPlusToneState * (1f - p[4][1] / 170f) + shaped * (p[4][1] / 170f);
                        x = shaped * (0.4f + p[4][2] / 100f * 0.9f);
                    }
                    if (on[5]) {
                        modBuf[modWrite] = x;
                        int modulation = (int) ((Math.sin(modPhase) * 0.5 + 0.5) * modDepth * SAMPLE_RATE * 0.018);
                        int modDelay = clamp((int) (SAMPLE_RATE * 0.008) + modulation, 1, modBuf.length - 2);
                        int read = modWrite - modDelay;
                        if (read < 0) read += modBuf.length;
                        float wet = modBuf[read];
                        x = x * (1f - modMix * 0.55f) + wet * (modMix * 0.75f);
                        modWrite = (modWrite + 1) % modBuf.length;
                        modPhase += (2.0 * Math.PI * modRate) / SAMPLE_RATE;
                        if (modPhase > Math.PI * 2) modPhase -= Math.PI * 2;
                    }
                    if (on[6]) {
                        int read = delayWrite - delaySamples;
                        if (read < 0) read += delayBuf.length;
                        float echo = delayBuf[read];
                        delayBuf[delayWrite] = x + echo * Math.min(0.82f, delayFeedback);
                        x = x * (1f - delayMix * 0.75f) + echo * (delayMix * 0.85f);
                        delayWrite = (delayWrite + 1) % delayBuf.length;
                    } else {
                        delayBuf[delayWrite] = x;
                        delayWrite = (delayWrite + 1) % delayBuf.length;
                    }
                    if (on[7]) {
                        float rv = reverbBuf[reverbWrite];
                        reverbBuf[reverbWrite] = x + rv * Math.min(0.86f, reverbFeedback);
                        float tone = p[7][1] / 100f;
                        float filteredRv = rv * (0.75f + tone * 0.25f);
                        x = x * (1f - reverbMix * 0.7f) + filteredRv * reverbMix;
                        reverbWrite = (reverbWrite + 1) % reverbBuf.length;
                    } else {
                        reverbBuf[reverbWrite] = x;
                        reverbWrite = (reverbWrite + 1) % reverbBuf.length;
                    }

                    if (localAmpOn) {
                        float driven = (float) Math.tanh(x * localAmpGain * ampDriveScale(localAmpModel));
                        ampLow += 0.035f * (driven - ampLow);
                        ampMid += 0.16f * (driven - ampMid);
                        float lowBand = ampLow;
                        float midBand = ampMid - ampLow;
                        float highBand = driven - ampMid;
                        x = lowBand * (0.35f + localBass * 1.15f)
                                + midBand * (0.35f + localMiddle * 1.15f)
                                + highBand * (0.25f + localTreble * 1.35f);
                        x *= (0.55f + localAmpVolume * 0.7f) * ampOutputScale(localAmpModel);
                    }
                    if (localCabOn) {
                        float cabCutoff = cabCutoff(localCabModel);
                        cabLow += cabCutoff * (x - cabLow);
                        x = cabLow * 0.88f + x * 0.12f;
                    }

                    // Looper records the processed signal and mixes playback into output.
                    float processed = x;
                    if (loopRecording && loopWritePos < loopBuffer.length) {
                        loopBuffer[loopWritePos++] = (short) (clampFloat(processed, -0.95f, 0.95f) * 32767f);
                        if (loopWritePos >= loopBuffer.length) {
                            loopRecording = false;
                            loopLength = loopWritePos;
                        }
                    }
                    if (loopPlaying && loopLength > 0) {
                        int rp = loopReadPos;
                        if (rp >= loopLength) rp = 0;
                        processed = processed * 0.75f + (loopBuffer[rp] / 32768f) * 0.75f;
                        loopReadPos = (rp + 1) % loopLength;
                    }
                    processed *= localMaster;
                    processed = clampFloat(processed, -0.95f, 0.95f);
                    out[i] = (short) (processed * 32767f);
                }
                int written = track.write(out, 0, count);
                if (written < 0) throw new IllegalStateException("AudioTrack write gagal: " + written);
                pitchTracker.add(in, count, this);
            }
        } catch (Exception e) {
            failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            if (recorder != null) {
                try { if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop(); } catch (Exception ignored) {}
                try { recorder.release(); } catch (Exception ignored) {}
            }
            if (track != null) {
                try { if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.stop(); } catch (Exception ignored) {}
                try { track.release(); } catch (Exception ignored) {}
            }
            audioRunning = false;
            audioStopping = false;
            if (loopRecording) { loopRecording = false; loopLength = loopWritePos; }
            loopPlaying = false;
            final String error = failure;
            if (error != null && !error.isEmpty()) setAudioMessage("Audio error: " + error);
            else setAudioMessage("Status: audio stopped");
            runOnUiThread(() -> {
                if (currentPage.equals("LOOPER")) refreshLooperUi();
                if (currentPage.equals("TUNER") && tunerNote != null && tunerFrequency <= 0f)
                    tunerNote.setText("--\nAudio berhenti");
                if (currentPage.equals("HOME") || currentPage.equals("AUDIO")) showPage(currentPage);
                setupNavigation();
            });
            audioThread = null;
        }
    }

    private void requestPreferredDevices(AudioRecord recorder, AudioTrack track) {
        if (Build.VERSION.SDK_INT < 23) {
            setAudioMessage("Audio aktif; pemilihan perangkat manual memerlukan Android 6 atau lebih baru.");
            return;
        }
        try {
            if (!"Microphone (HP)".equals(selectedInput)) {
                AudioDeviceInfo[] inputs = getAudioDevices(AudioManager.GET_DEVICES_INPUTS);
                AudioDeviceInfo chosen = null;
                for (AudioDeviceInfo d : inputs) {
                    int type = d.getType();
                    boolean usb = type == AudioDeviceInfo.TYPE_USB_DEVICE || (Build.VERSION.SDK_INT >= 26 && type == AudioDeviceInfo.TYPE_USB_HEADSET);
                    boolean wired = type == AudioDeviceInfo.TYPE_WIRED_HEADSET;
                    if ((selectedInput.startsWith("USB") && usb) || (selectedInput.startsWith("Wired") && wired)) { chosen = d; break; }
                }
                if (chosen != null) recorder.setPreferredDevice(chosen);
                else setAudioMessage("Input " + selectedInput + " tidak ditemukan; Android memakai input default.");
            }
            if ("Speaker".equals(selectedOutput)) {
                AudioDeviceInfo[] outputs = getAudioDevices(AudioManager.GET_DEVICES_OUTPUTS);
                for (AudioDeviceInfo d : outputs) {
                    if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { track.setPreferredDevice(d); break; }
                }
            } else if ("USB Audio Output".equals(selectedOutput)) {
                AudioDeviceInfo[] outputs = getAudioDevices(AudioManager.GET_DEVICES_OUTPUTS);
                AudioDeviceInfo chosen = null;
                for (AudioDeviceInfo d : outputs) {
                    int type = d.getType();
                    boolean usb = type == AudioDeviceInfo.TYPE_USB_DEVICE || (Build.VERSION.SDK_INT >= 26 && type == AudioDeviceInfo.TYPE_USB_HEADSET);
                    if (usb) { chosen = d; break; }
                }
                if (chosen != null) track.setPreferredDevice(chosen);
                else setAudioMessage("USB output tidak ditemukan; Android memakai output default.");
            }
        } catch (Exception e) {
            setAudioMessage("Routing perangkat tidak tersedia: " + e.getMessage());
        }
    }

    private AudioDeviceInfo[] getAudioDevices(int direction) {
        if (Build.VERSION.SDK_INT >= 23) {
            AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (manager != null) return manager.getDevices(direction);
        }
        return new AudioDeviceInfo[0];
    }

    private class PitchTracker {
        private final float[] window = new float[2048];
        private int writePosition = 0;
        private int filled = 0;
        private int sinceCheck = 0;

        void add(short[] samples, int count, MainActivity activity) {
            for (int i = 0; i < count; i++) {
                window[writePosition] = samples[i] / 32768f;
                writePosition = (writePosition + 1) % window.length;
                if (filled < window.length) filled++;
                sinceCheck++;
            }
            if (filled < window.length || sinceCheck < 1024) return;
            sinceCheck = 0;
            float[] ordered = new float[window.length];
            for (int i = 0; i < window.length; i++) ordered[i] = window[(writePosition + i) % window.length];
            float mean = 0f;
            for (float v : ordered) mean += v;
            mean /= ordered.length;
            for (int i = 0; i < ordered.length; i++) ordered[i] -= mean;
            float bestScore = 0f;
            int bestLag = -1;
            int minLag = SAMPLE_RATE / 1200;
            int maxLag = SAMPLE_RATE / 55;
            for (int lag = minLag; lag <= maxLag; lag++) {
                double corr = 0, energyA = 0, energyB = 0;
                int end = ordered.length - lag;
                for (int j = 0; j < end; j += 2) {
                    float a = ordered[j];
                    float b = ordered[j + lag];
                    corr += a * b;
                    energyA += a * a;
                    energyB += b * b;
                }
                if (energyA > 1e-7 && energyB > 1e-7) {
                    float score = (float) (corr / Math.sqrt(energyA * energyB));
                    if (score > bestScore) { bestScore = score; bestLag = lag; }
                }
            }
            if (bestLag > 0 && bestScore > 0.48f) {
                final float frequency = SAMPLE_RATE / (float) bestLag;
                if (frequency >= 50f && frequency <= 1200f) {
                    tunerFrequency = frequency;
                    runOnUiThread(() -> {
                        if (tunerNote != null) tunerNote.setText(noteText(frequency));
                    });
                }
            }
        }
    }

    private String noteText(float frequency) {
        if (frequency <= 0f) return "--\nMenunggu sinyal";
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int midi = (int) Math.round(69 + 12 * (Math.log(frequency / 440.0) / Math.log(2)));
        int noteIndex = (midi % 12 + 12) % 12;
        int octave = midi / 12 - 1;
        double target = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
        int cents = (int) Math.round(1200.0 * Math.log(frequency / target) / Math.log(2));
        return names[noteIndex] + octave + "\n" + String.format(Locale.US, "%.1f Hz  •  %+d cents", frequency, cents);
    }

    private void applyPresetValues(int index) {
        selectedPreset = clamp(index, 0, presetNames.length - 1);
        for (int i = 0; i < EFFECT_COUNT; i++) {
            boolean on;
            if (selectedPreset < 6) on = (i == 0 || i == 1 || i == 5) && ((selectedPreset + i) % 3 != 0);
            else if (selectedPreset < 20) on = i == 1 || i == 2 || (i == 6 && selectedPreset % 3 == 0) || (i == 7 && selectedPreset % 4 == 0);
            else if (selectedPreset < 34) on = i == 0 || i == 3 || i == 4 || (i == 1 && selectedPreset % 2 == 0);
            else if (selectedPreset < 45) on = i == 5 || i == 6 || i == 7 || (i == 2 && selectedPreset % 2 == 0);
            else on = ((selectedPreset + i * 2) % 5) < 2;
            setEffectOn(i, on);
            for (int j = 0; j < paramNames[i].length; j++) {
                int value = 25 + ((selectedPreset * 13 + i * 17 + j * 29) % 70);
                setParam(i, j, value);
            }
        }
        ampGain = 1.5f + (selectedPreset % 10) * 0.35f;
        ampVolume = 0.58f + (selectedPreset % 5) * 0.08f;
        ampBass = 0.35f + (selectedPreset % 4) * 0.12f;
        ampMiddle = 0.38f + ((selectedPreset + 1) % 4) * 0.12f;
        ampTreble = 0.35f + ((selectedPreset + 2) % 5) * 0.12f;
        selectedAmp = selectedPreset % ampNames.length;
        selectedCab = (selectedPreset + 5) % cabNames.length;
        masterVolume = 0.55f + (selectedPreset % 5) * 0.08f;
        if (masterSeek != null) masterSeek.setProgress(Math.round(masterVolume * 100));
        updateMasterValue(Math.round(masterVolume * 100));
        updatePresetLabels();
        refreshEffectControlUi();
    }

    private void updatePresetLabels() {
        if (presetTitle != null) {
            if (currentPage.equals("HOME")) presetTitle.setText("PRESET  •  " + presetNames[selectedPreset]);
            else presetTitle.setText("ACTIVE  •  " + presetNames[selectedPreset]);
        }
        if (presetSpinner != null && presetSpinner.getSelectedItemPosition() != selectedPreset) presetSpinner.setSelection(selectedPreset);
    }

    private void refreshEffectControlUi() {
        for (int i = 0; i < EFFECT_COUNT; i++) {
            if (pedalViews[i] != null) pedalViews[i].invalidate();
            for (int j = 0; j < paramNames[i].length; j++) {
                SeekBar seek = effectSeekBars[i][j];
                if (seek != null && seek.getProgress() != getParam(i, j)) seek.setProgress(getParam(i, j));
            }
        }
    }

    private void updateMasterValue(int value) {
        if (masterValue != null) masterValue.setText("Master: " + value + "%");
    }

    private void updateEffectParameterFromKnob(int id, int paramIndex, int value) {
        setParam(id, paramIndex, value);
        SeekBar seek = effectSeekBars[id][paramIndex];
        if (seek != null && seek.getProgress() != value) seek.setProgress(value);
        if (effectParamValues[id][paramIndex] != null) effectParamValues[id][paramIndex].setText(paramNames[id][paramIndex] + "  " + value);
        if (pedalViews[id] != null) pedalViews[id].invalidate();
    }

    private void refreshLooperUi() {
        if (loopStatus != null) loopStatus.setText(looperText());
        if (looperRecordButton != null) {
            looperRecordButton.setText(loopRecording ? "STOP RECORDING" : "RECORD LOOP");
            looperRecordButton.setBackground(bgShape(Color.rgb(25, 34, 53), loopRecording ? RED : ORANGE));
        }
        if (looperPlayButton != null) {
            looperPlayButton.setText(loopPlaying ? "STOP LOOP" : "PLAY LOOP");
            looperPlayButton.setBackground(bgShape(Color.rgb(25, 34, 53), loopPlaying ? RED : CYAN));
        }
        refreshAudioToggleButtons();
    }

    private String looperText() {
        if (loopRecording) return "● RECORDING  •  " + String.format(Locale.US, "%.1f s", loopWritePos / (float) SAMPLE_RATE);
        if (loopPlaying) return "▶ LOOP PLAYING  •  " + String.format(Locale.US, "%.1f s", loopLength / (float) SAMPLE_RATE);
        if (loopLength > 0) return "LOOP READY  •  " + String.format(Locale.US, "%.1f seconds", loopLength / (float) SAMPLE_RATE);
        return "LOOP EMPTY  •  max 30 seconds";
    }

    private String effectDescription(int id) {
        switch (id) {
            case 0: return "Noise gate • threshold / release";
            case 1: return "Compressor • sustain / output level";
            case 2: return "Overdrive • soft clipping / tone / level";
            case 3: return "Distortion • gain / tone / level";
            case 4: return "Distortion+ • harder clipping / tone";
            case 5: return "Modulation • chorus-like modulated delay";
            case 6: return "Delay • time / feedback / mix";
            default: return "Reverb • feedback ambience / tone / mix";
        }
    }

    private void setAudioMessage(String message) {
        audioMessage = message;
        runOnUiThread(() -> {
            if (audioStatus != null) audioStatus.setText(audioMessage);
            refreshAudioToggleButtons();
            refreshLooperUi();
        });
    }

    private float ampDriveScale(int model) {
        switch (model) {
            case 0: return 0.72f;
            case 1: return 0.82f;
            case 2: return 1.02f;
            case 3: return 0.86f;
            case 4: return 1.12f;
            case 5: return 1.20f;
            case 6: return 1.32f;
            case 7: return 1.62f;
            case 8: return 1.85f;
            case 9: return 0.68f;
            case 10: return 1.16f;
            default: return 1.42f;
        }
    }

    private float ampOutputScale(int model) {
        return 0.92f + (model % 4) * 0.04f;
    }

    private float cabCutoff(int model) {
        switch (model) {
            case 0: return 0.045f;
            case 1: return 0.060f;
            case 2: return 0.085f;
            case 3: return 0.105f;
            case 4: return 0.150f;
            case 5: return 0.095f;
            case 6: return 0.125f;
            case 7: return 0.080f;
            default: return 0.100f;
        }
    }

    private void refreshAudioToggleButtons() {
        if (audioToggleButton != null) {
            audioToggleButton.setText(audioRunning ? "STOP AUDIO ENGINE" : "START MIC / USB AUDIO");
            audioToggleButton.setBackground(bgShape(Color.rgb(25, 34, 53), audioRunning ? RED : GREEN));
        }
        if (looperAudioButton != null) {
            looperAudioButton.setText(audioRunning ? "STOP AUDIO ENGINE" : "START AUDIO ENGINE");
            looperAudioButton.setBackground(bgShape(Color.rgb(25, 34, 53), audioRunning ? RED : BLUE));
        }
    }

    private boolean isEffectOn(int id) { return effectEnabled.get(id) != 0; }
    private void setEffectOn(int id, boolean on) { effectEnabled.set(id, on ? 1 : 0); }
    private int getParam(int id, int param) { return effectParams.get(id * PARAMS_PER_EFFECT + param); }
    private void setParam(int id, int param, int value) { effectParams.set(id * PARAMS_PER_EFFECT + param, clamp(value, 0, 100)); }
    private int gainToUi(float gain) { return clamp(Math.round((gain - 0.5f) / 0.075f), 0, 100); }

    private void navigate(String page) {
        showPage(page);
        setupNavigation();
    }

    private void setupNavigation() {
        if (bottomNavigation == null) return;
        bottomNavigation.removeAllViews();
        String[] pages = {"HOME", "PEDALBOARD", "AMP & CAB", "TUNER", "LOOPER", "PRESETS", "AUDIO"};
        String[] labels = {"Home", "Pedal", "Amp/Cab", "Tuner", "Looper", "Preset", "Audio"};
        for (int i = 0; i < pages.length; i++) {
            final String page = pages[i];
            TextView b = text(labels[i], 10, page.equals(currentPage) ? CYAN : TEXT, page.equals(currentPage));
            b.setGravity(Gravity.CENTER);
            b.setClickable(true);
            b.setFocusable(true);
            b.setPadding(dp(1), dp(7), dp(1), dp(7));
            b.setBackgroundColor(Color.TRANSPARENT);
            bottomNavigation.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
            b.setOnClickListener(v -> navigate(page));
        }
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        return s;
    }

    private LinearLayout card(int accent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setBackground(bgShape(PANEL, accent));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private void section(String title, String subtitle) {
        TextView t = text(title, 14, CYAN, true);
        t.setPadding(dp(2), dp(12), 0, dp(3));
        root.addView(t);
        TextView sub = text(subtitle, 10, MUTED, false);
        sub.setPadding(dp(2), 0, 0, dp(8));
        root.addView(sub);
    }

    private void sectionIn(LinearLayout parent, String title, String subtitle) {
        TextView t = text(title, 14, CYAN, true);
        t.setPadding(0, dp(3), 0, dp(4));
        parent.addView(t);
        TextView s = text(subtitle, 10, MUTED, false);
        s.setPadding(0, 0, 0, dp(8));
        parent.addView(s);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void styleButton(Button b, String label, int accent) {
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setBackground(bgShape(Color.rgb(25, 34, 53), accent));
    }

    private GradientDrawable bgShape(int color, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), accent);
        return d;
    }

    private void tintSeek(SeekBar b, int accent) {
        if (Build.VERSION.SDK_INT >= 21) {
            b.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            b.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        }
    }

    private int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) if (array[i].equals(value)) return i;
        return 0;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static float clampFloat(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    private class PedalFaceView extends View {
        private final int effectId;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int accent;
        private final float density;
        private int draggingParam = -1;
        private float dragStartY;
        private int dragStartValue;

        PedalFaceView(Context context, int id) {
            super(context);
            effectId = id;
            accent = effectColors[id];
            density = getResources().getDisplayMetrics().density;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setClickable(true);
            setContentDescription(effectNames[id] + ", ketuk sakelar kaki untuk ON/OFF atau geser kenop untuk mengubah parameter");
        }

        private float d(float v) { return v * density; }
        private void setPaint(int color, float size, boolean bold) {
            paint.setColor(color);
            paint.setTextSize(d(size));
            paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setStrokeWidth(d(1));
        }
        private void centered(Canvas canvas, String label, float x, float y, int color, float size, boolean bold) {
            setPaint(color, size, bold);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, x, y, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawKnob(Canvas canvas, float x, float y, float radius, String label, int value) {
            setPaint(0x88000000, 1, false);
            canvas.drawCircle(x + d(1), y + d(2), radius + d(1), paint);
            setPaint(0xFF080D16, 1, false);
            canvas.drawCircle(x, y, radius + d(3), paint);
            setPaint(isEffectOn(effectId) ? accent : 0xFF526174, 1, false);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(2));
            canvas.drawCircle(x, y, radius + d(1), paint);
            paint.setStyle(Paint.Style.FILL);
            setPaint(0xFF263247, 1, false);
            canvas.drawCircle(x, y, radius, paint);
            float position = value / 100f;
            double angle = Math.toRadians(135 + 270 * position);
            float x1 = x + (float) Math.cos(angle) * radius * 0.2f;
            float y1 = y + (float) Math.sin(angle) * radius * 0.2f;
            float x2 = x + (float) Math.cos(angle) * radius * 0.76f;
            float y2 = y + (float) Math.sin(angle) * radius * 0.76f;
            setPaint(Color.WHITE, 1, false);
            paint.setStrokeWidth(d(2));
            canvas.drawLine(x1, y1, x2, y2, paint);
            centered(canvas, label, x, y + radius + d(17), 0xFFDCE7F5, 9, true);
            centered(canvas, String.valueOf(value), x, y + d(4), Color.WHITE, 9, true);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), pad = d(7);
            rect.set(pad, d(4), w - pad, h - d(4));
            setPaint(0xFF050811, 1, false);
            canvas.drawRoundRect(rect, d(17), d(17), paint);
            RectF face = new RectF(pad + d(4), d(8), w - pad - d(4), h - d(8));
            setPaint(isEffectOn(effectId) ? accent : 0xFF354052, 1, false);
            canvas.drawRoundRect(face, d(14), d(14), paint);
            RectF inner = new RectF(face.left + d(5), face.top + d(5), face.right - d(5), face.bottom - d(5));
            setPaint(0xFF111A2A, 1, false);
            canvas.drawRoundRect(inner, d(11), d(11), paint);
            setPaint(accent, 1, false);
            canvas.drawRoundRect(new RectF(inner.left, inner.top, inner.left + d(4), inner.bottom), d(3), d(3), paint);
            centered(canvas, "STRADA  /  EFFECTS", w / 2f, d(27), 0xFF9DB0C9, 8, true);
            centered(canvas, effectNames[effectId], w / 2f, d(48), isEffectOn(effectId) ? accent : 0xFFDCE5F2, 16, true);
            setPaint(isEffectOn(effectId) ? accent : 0xFF455063, 1, false);
            canvas.drawCircle(w - d(27), d(26), d(6), paint);
            int count = paramNames[effectId].length;
            float gap = w / (count + 1f);
            float cy = h * 0.49f;
            float radius = Math.min(d(18), w / (count == 2 ? 9f : 14f));
            for (int j = 0; j < count; j++) drawKnob(canvas, gap * (j + 1), cy, radius,
                    paramNames[effectId][j].toUpperCase(Locale.US), getParam(effectId, j));
            float sx = w / 2f, sy = h - d(30);
            setPaint(0xFF070B12, 1, false);
            canvas.drawCircle(sx, sy, d(13), paint);
            setPaint(isEffectOn(effectId) ? accent : 0xFF68768B, 1, false);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(2));
            canvas.drawCircle(sx, sy, d(9), paint);
            paint.setStyle(Paint.Style.FILL);
            centered(canvas, isEffectOn(effectId) ? "ACTIVE  •  TAP TO BYPASS" : "BYPASSED  •  TAP TO ACTIVATE",
                    sx, h - d(8), isEffectOn(effectId) ? Color.WHITE : 0xFF9AA8BB, 8, true);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY(), w = getWidth(), h = getHeight();
            float cy = h * 0.49f;
            int count = paramNames[effectId].length;
            float gap = w / (count + 1f);
            float radius = Math.min(d(18), w / (count == 2 ? 9f : 14f));
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (y > h - d(55)) {
                    draggingParam = -2;
                    return true;
                }
                if (y >= cy - radius - d(10) && y <= cy + radius + d(25)) {
                    int nearest = -1;
                    float distance = Float.MAX_VALUE;
                    for (int j = 0; j < count; j++) {
                        float centerX = gap * (j + 1);
                        float next = Math.abs(x - centerX);
                        if (next < distance) { distance = next; nearest = j; }
                    }
                    if (nearest >= 0 && distance <= gap * 0.48f) {
                        draggingParam = nearest;
                        dragStartY = y;
                        dragStartValue = getParam(effectId, nearest);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                }
                return false;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && draggingParam >= 0) {
                int value = clamp(dragStartValue + Math.round((dragStartY - y) / d(1.8f)), 0, 100);
                updateEffectParameterFromKnob(effectId, draggingParam, value);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (draggingParam == -2) {
                    setEffectOn(effectId, !isEffectOn(effectId));
                    invalidate();
                }
                boolean handled = draggingParam != -1;
                draggingParam = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                return handled;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                draggingParam = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return draggingParam >= 0;
        }
    }

    @Override protected void onDestroy() {
        audioRunning = false;
        loopRecording = false;
        loopPlaying = false;
        super.onDestroy();
    }
    }
