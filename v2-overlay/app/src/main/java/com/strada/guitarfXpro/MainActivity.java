package com.strada.guitarfXpro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements AudioEngine.Listener {
    private interface IntChanged {
        void onChanged(int value);
    }

    private interface ToggleChanged {
        void onChanged(boolean value);
    }

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
    private static final int EFFECT_COUNT = 8;

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
        {"Threshold", "Release", ""},
        {"Sustain", "Level", ""},
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

    private SharedPreferences preferences;
    private final ArrayList<String> customPresetNames = new ArrayList<>();
    private AudioEngine audioEngine;

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
    private SeekBar masterSeek;
    private Spinner presetSpinner;
    private Spinner ampSpinner;
    private Spinner cabSpinner;
    private Spinner inputSpinner;
    private Spinner outputSpinner;
    private SeekBar[][] effectSeekBars = new SeekBar[EFFECT_COUNT][3];
    private TextView[][] effectParamValues = new TextView[EFFECT_COUNT][3];
    private PedalFaceView[] pedalViews = new PedalFaceView[EFFECT_COUNT];
    private LinearLayout pedalList;
    private final int[] effectOrder = {
    0, 1, 2, 3, 4, 5, 6, 7
};

    private final boolean[] effectEnabled = new boolean[EFFECT_COUNT];
    private final int[][] effectParams = new int[EFFECT_COUNT][3];

    private float masterVolume = .72f;
    private float ampGain = 3f;
    private float ampBass = .5f;
    private float ampMiddle = .5f;
    private float ampTreble = .6f;
    private float ampVolume = .7f;
    private boolean ampOn = true;
    private boolean cabOn = true;
    private int selectedPreset;
    private int selectedAmp;
    private int selectedCab = 5;
    private String selectedInput = "Microphone (HP)";
    private String selectedOutput = "System / Headphone";
    private volatile float tunerFrequency;
    private volatile boolean loopRecording;
    private volatile boolean loopPlaying;
    private volatile int loopLength;
    private volatile String audioMessage = "Status: audio belum berjalan";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        preferences = getSharedPreferences("strada_preset_profiles", MODE_PRIVATE);
        for (int i = 0; i < EFFECT_COUNT; i++) {
            for (int j = 0; j < 3; j++) effectParams[i][j] = 50;
        }
        loadCustomPresetNames();
        applyPresetValues(0, false);
        audioEngine = new AudioEngine(this, this);
        pushSettings();
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
        mainScroll.addView(pageContainer);
        screen.addView(mainScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomNavigation = new LinearLayout(this);
        bottomNavigation.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavigation.setGravity(Gravity.CENTER);
        bottomNavigation.setPadding(dp(3), dp(4), dp(3), dp(4));
        bottomNavigation.setBackgroundColor(Color.rgb(18, 24, 37));
        screen.addView(bottomNavigation, new LinearLayout.LayoutParams(-1, dp(62)));

        setContentView(screen);
        showPage("HOME");
        setupNavigation();
    }

    private void showPage(String page) {
        currentPage = page;
        pageContainer.removeAllViews();
        presetTitle = null;
        audioStatus = null;
        masterValue = null;
        tunerNote = null;
        loopStatus = null;
        audioToggleButton = null;
        looperRecordButton = null;
        looperPlayButton = null;
        masterSeek = null;
        presetSpinner = null;
        ampSpinner = null;
        cabSpinner = null;
        inputSpinner = null;
        outputSpinner = null;
        effectSeekBars = new SeekBar[EFFECT_COUNT][3];
        effectParamValues = new TextView[EFFECT_COUNT][3];
        pedalViews = new PedalFaceView[EFFECT_COUNT];
        pedalList = null;

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
        setupNavigation();
        mainScroll.post(() -> mainScroll.scrollTo(0, 0));
    }

    private void buildHomePage() {
    LinearLayout header = row();
    header.setPadding(dp(2), dp(10), dp(2), dp(12));
    pageContainer.addView(header);

    LinearLayout brand = new LinearLayout(this);
    brand.setOrientation(LinearLayout.VERTICAL);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    TextView logo = text("STRADA", 28, CYAN, true);
    logo.setLetterSpacing(.12f);
    brand.addView(logo);
    brand.addView(text("GUITAR FX PRO  /  DIGITAL RIG", 9, MUTED, true));

    TextView live = text(audioEngine != null && audioEngine.isRunning()
            ? "● LIVE" : "● STANDBY", 11,
            audioEngine != null && audioEngine.isRunning() ? GREEN : ORANGE, true);
    live.setGravity(Gravity.CENTER);
    live.setPadding(dp(12), dp(8), dp(12), dp(8));
    live.setBackground(bgShape(PANEL,
            audioEngine != null && audioEngine.isRunning() ? GREEN : ORANGE));
    header.addView(live);

    LinearLayout hero = card(CYAN);
    pageContainer.addView(hero);
    hero.addView(text("NOW PLAYING", 10, CYAN, true));
    presetTitle = text(getPresetName(selectedPreset), 23, TEXT, true);
    presetTitle.setPadding(0, dp(5), 0, dp(2));
    hero.addView(presetTitle);
    hero.addView(text("PRESET  •  " + (selectedPreset + 1) + " / " + presetNames.length,
            10, MUTED, true));

    presetSpinner = spinner(getAllPresetNames());
    presetSpinner.setSelection(selectedPreset);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(48));
    sp.topMargin = dp(8);
    hero.addView(presetSpinner, sp);
    presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        boolean first = true;
        @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
            if (first) { first = false; return; }
            applyPresetValues(position, true);
        }
        @Override public void onNothingSelected(AdapterView<?> p) {}
    });

    LinearLayout masterPanel = card(PURPLE);
    pageContainer.addView(masterPanel);
    LinearLayout masterHeader = row();
    masterPanel.addView(masterHeader);
    masterHeader.addView(text("MASTER OUTPUT", 12, TEXT, true),
            new LinearLayout.LayoutParams(0, -2, 1));
    masterValue = text(Math.round(masterVolume * 100) + "%", 20, CYAN, true);
    masterHeader.addView(masterValue);
    addControl(masterPanel, "OUTPUT LEVEL", 0, 100,
            Math.round(masterVolume * 100), CYAN,
            value -> {
                masterVolume = value / 100f;
                updateMasterValue(value);
                pushSettings();
            });

    addAudioToggle(masterPanel);
    audioStatus = text(audioMessage, 11, MUTED, false);
    audioStatus.setPadding(0, dp(8), 0, dp(2));
    masterPanel.addView(audioStatus);

    section("SIGNAL CHAIN", "Delapan blok efek • ketuk untuk membuka pedalboard");
    LinearLayout chain = card(BLUE);
    pageContainer.addView(chain);
    LinearLayout chainRow = row();
    chain.addView(chainRow);
    for (int i = 0; i < EFFECT_COUNT; i++) {
        final int effect = i;
        TextView chip = text(effectNames[i], 9,
                effectEnabled[i] ? effectColors[i] : MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(5), dp(10), dp(5), dp(10));
        chip.setBackground(bgShape(Color.rgb(8, 14, 25),
                effectEnabled[i] ? effectColors[i] : Color.rgb(55, 66, 82)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.setMargins(dp(2), 0, dp(2), 0);
        chainRow.addView(chip, cp);
        chip.setOnClickListener(v -> showPage("PEDALBOARD"));
    }

    section("QUICK ACCESS", "Pilih modul untuk membentuk tone.");
    addHomeButton("PEDALBOARD  /  EFFECTS", CYAN, "PEDALBOARD");
    addHomeButton("AMPLIFIER  &  CABINET", PURPLE, "AMP & CAB");
    LinearLayout tools = row();
    pageContainer.addView(tools);
    addCompactHomeButton(tools, "TUNER", GREEN, "TUNER");
    addCompactHomeButton(tools, "LOOPER", ORANGE, "LOOPER");
    addHomeButton("PRESET LIBRARY  •  60 TONES", BLUE, "PRESETS");
    addHomeButton("AUDIO ROUTING  /  INPUT & OUTPUT", RED, "AUDIO");

    TextView foot = text("Gunakan headphone atau USB audio interface yang kompatibel. "
            + "Status LIVE hanya menunjukkan engine berjalan, bukan jaminan latensi atau kualitas profesional.",
            9, MUTED, false);
    foot.setPadding(dp(3), dp(10), dp(3), dp(16));
    pageContainer.addView(foot);
}

    private void addCompactHomeButton(LinearLayout parent, String label, int accent, String page) {
    Button b = new Button(this);
    styleButton(b, label, accent);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
    lp.setMargins(dp(3), dp(3), dp(3), dp(3));
    parent.addView(b, lp);
    b.setOnClickListener(v -> showPage(page));
}

    private void buildPedalboardPage() {
    section("FULL COLOR PEDALBOARD",
            "Tekan dan tahan pedal untuk memindahkan posisi. Ketuk untuk ON/OFF.");

    pedalList = new LinearLayout(this);
    pedalList.setOrientation(LinearLayout.VERTICAL);
    pageContainer.addView(pedalList);

    for (int position = 0; position < EFFECT_COUNT; position++) {
        final int id = effectOrder[position];

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setTag(id);

        LinearLayout.LayoutParams itemParams =
                new LinearLayout.LayoutParams(-1, -2);
        itemParams.setMargins(0, dp(7), 0, dp(5));

        pedalList.addView(item, itemParams);

        PedalFaceView pedal = new PedalFaceView(this, id);
        pedalViews[id] = pedal;

        LinearLayout.LayoutParams pp =
                new LinearLayout.LayoutParams(-1, dp(205));
        item.addView(pedal, pp);

        LinearLayout c = card(effectColors[id]);
        item.addView(c);

        LinearLayout head = row();
        c.addView(head);

        head.addView(
                text(effectNames[id] + " PARAMETERS",
                        14, effectColors[id], true),
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        Button toggle = new Button(this);
        styleButton(toggle,
                effectEnabled[id] ? "ON" : "OFF",
                effectColors[id]);

        head.addView(toggle,
                new LinearLayout.LayoutParams(dp(78), dp(40)));

        toggle.setOnClickListener(v -> {
            effectEnabled[id] = !effectEnabled[id];

            toggle.setText(effectEnabled[id] ? "ON" : "OFF");

            toggle.setBackground(bgShape(
                    effectEnabled[id] ? effectColors[id] : Color.DKGRAY,
                    effectColors[id]
            ));

            pedal.invalidate();
            pushSettings();
        });

        c.addView(text(
                effectDescription(id), 10, MUTED, false
        ));

        for (int j = 0; j < paramCount(id); j++) {
            addEffectParamControl(c, id, j);
        }

        // Tekan dan tahan pedal untuk mulai drag.
        pedal.setOnLongClickListener(v -> {
            android.content.ClipData data =
                    android.content.ClipData.newPlainText(
                            "StradaPedal", String.valueOf(id));

            android.view.View.DragShadowBuilder shadow =
                    new android.view.View.DragShadowBuilder(item);

            return item.startDrag(
                    data, shadow, item, 0
            );
        });

        item.setOnDragListener((target, event) -> {
            switch (event.getAction()) {

                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return event.getLocalState() instanceof LinearLayout;

                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    target.setAlpha(0.65f);
                    return true;

                case android.view.DragEvent.ACTION_DRAG_EXITED:
                    target.setAlpha(1f);
                    return true;

                case android.view.DragEvent.ACTION_DROP: {
                    Object state = event.getLocalState();

                    if (!(state instanceof LinearLayout)) {
                        return false;
                    }

                    LinearLayout dragged =
                            (LinearLayout) state;

                    if (dragged == target ||
                            dragged.getParent() != pedalList) {
                        return false;
                    }

                    int from = pedalList.indexOfChild(dragged);
                    int to = pedalList.indexOfChild(target);

                    if (from < 0 || to < 0) {
                        return false;
                    }

                    pedalList.removeView(dragged);

                    if (from < to) {
                        to--;
                    }

                    pedalList.addView(dragged, to);

                    updateEffectOrderFromPedalboard();
                    pushSettings();

                    return true;
                }

                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    target.setAlpha(1f);
                    return true;
            }

            return true;
        });
    }
}

    private void updateEffectOrderFromPedalboard() {
    if (pedalList == null) return;

    for (int i = 0; i < EFFECT_COUNT; i++) {
        Object tag = pedalList.getChildAt(i).getTag();

        if (tag instanceof Integer) {
            effectOrder[i] = (Integer) tag;
        }
    }
}
    
    private void saveEffectOrder(int presetIndex) {
    StringBuilder value = new StringBuilder();

    for (int i = 0; i < EFFECT_COUNT; i++) {
        if (i > 0) value.append(",");
        value.append(effectOrder[i]);
    }

    preferences.edit()
            .putString(
                    "preset_" + presetIndex + "_effect_order",
                    value.toString()
            )
            .apply();
}

    private void loadEffectOrder(int presetIndex) {
    String saved = preferences.getString(
            "preset_" + presetIndex + "_effect_order",
            "0,1,2,3,4,5,6,7"
    );

    String[] parts = saved.split(",");
    if (parts.length != EFFECT_COUNT) return;

    int[] loaded = new int[EFFECT_COUNT];
    boolean[] used = new boolean[EFFECT_COUNT];

    try {
        for (int i = 0; i < EFFECT_COUNT; i++) {
            int id = Integer.parseInt(parts[i].trim());

            if (id < 0 || id >= EFFECT_COUNT || used[id]) {
                return;
            }

            loaded[i] = id;
            used[id] = true;
        }
    } catch (NumberFormatException e) {
        return;
    }

    System.arraycopy(
            loaded, 0, effectOrder, 0, EFFECT_COUNT
    );

    applyEffectOrderToPedalboard();
}

    private void applyEffectOrderToPedalboard() {
    if (pedalList == null ||
            pedalList.getChildCount() != EFFECT_COUNT) {
        return;
    }

    for (int i = 0; i < EFFECT_COUNT; i++) {
        int wantedId = effectOrder[i];

        for (int j = i; j < EFFECT_COUNT; j++) {
            android.view.View child = pedalList.getChildAt(j);

            if (child.getTag() instanceof Integer
                    && ((Integer) child.getTag()) == wantedId) {

                pedalList.removeViewAt(j);
                pedalList.addView(child, i);
                break;
            }
        }
    }
}

    private void addEffectParamControl(LinearLayout parent, int id, int index) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, dp(4), 0, dp(2));
        parent.addView(line);
        int initial = effectParams[id][index];
        TextView value = text(paramNames[id][index] + "  " + initial, 11, TEXT, true);
        line.addView(value);
        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(initial);
        tintSeek(seek, effectColors[id]);
        line.addView(seek);
        effectSeekBars[id][index] = seek;
        effectParamValues[id][index] = value;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                effectParams[id][index] = progress;
                value.setText(paramNames[id][index] + "  " + progress);
                if (pedalViews[id] != null) pedalViews[id].invalidate();
                pushSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void buildAmpCabPage() {
        section("AMPLIFIER & CABINET", "Model amp/cab saat ini menggunakan algoritma dasar, bukan capture atau IR profesional.");
        LinearLayout ampCard = card(PURPLE);
        pageContainer.addView(ampCard);
        addToggleRow(ampCard, "AMPLIFIER BLOCK", ampOn, value -> { ampOn = value; pushSettings(); }, PURPLE);
        ampCard.addView(text("AMP MODEL", 11, MUTED, true));
        ampSpinner = spinner(ampNames);
        ampSpinner.setSelection(selectedAmp);
        ampCard.addView(ampSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        ampSpinner.setOnItemSelectedListener(simpleSelection(value -> { selectedAmp = value; pushSettings(); }));
        addControl(ampCard, "AMP GAIN", 0, 100, gainToUi(ampGain), PURPLE,
                value -> { ampGain = .5f + value * .075f; pushSettings(); });
        addControl(ampCard, "AMP VOLUME", 0, 100, Math.round(ampVolume * 100), PURPLE,
                value -> { ampVolume = value / 100f; pushSettings(); });
        addControl(ampCard, "BASS", 0, 100, Math.round(ampBass * 100), PURPLE,
                value -> { ampBass = value / 100f; pushSettings(); });
        addControl(ampCard, "MIDDLE", 0, 100, Math.round(ampMiddle * 100), PURPLE,
                value -> { ampMiddle = value / 100f; pushSettings(); });
        addControl(ampCard, "TREBLE", 0, 100, Math.round(ampTreble * 100), PURPLE,
                value -> { ampTreble = value / 100f; pushSettings(); });

        LinearLayout cabCard = card(CYAN);
        pageContainer.addView(cabCard);
        addToggleRow(cabCard, "CABINET FILTER", cabOn, value -> { cabOn = value; pushSettings(); }, CYAN);
        cabCard.addView(text("CABINET MODEL", 11, MUTED, true));
        cabSpinner = spinner(cabNames);
        cabSpinner.setSelection(selectedCab);
        cabCard.addView(cabSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        cabSpinner.setOnItemSelectedListener(simpleSelection(value -> { selectedCab = value; pushSettings(); }));
        cabCard.addView(text("Belum tersedia IR loader atau convolution cabinet.", 10, MUTED, false));
    }

    private void buildTunerPage() {
        section("CHROMATIC GUITAR TUNER", "Tuner membaca input audio saat mesin audio berjalan.");
        LinearLayout c = card(GREEN);
        pageContainer.addView(c);
        tunerNote = text(tunerFrequency > 0 ? noteText(tunerFrequency) : "--\nMenunggu sinyal audio",
                28, GREEN, true);
        tunerNote.setGravity(Gravity.CENTER);
        tunerNote.setPadding(0, dp(28), 0, dp(28));
        c.addView(tunerNote, new LinearLayout.LayoutParams(-1, dp(150)));
        Button start = new Button(this);
        styleButton(start, audioEngine.isRunning() ? "STOP AUDIO ENGINE" : "START AUDIO ENGINE", GREEN);
        c.addView(start, new LinearLayout.LayoutParams(-1, dp(48)));
        start.setOnClickListener(v -> toggleAudio());
        c.addView(text("Rentang deteksi: sekitar 50–1200 Hz. Akurasi dipengaruhi noise dan perangkat input.",
                10, MUTED, false));
    }

    private void buildLooperPage() {
        section("30 SECOND LOOPER", "Rekam sinyal setelah efek dan amp/cab, lalu putar sebagai loop.");
        LinearLayout c = card(ORANGE);
        pageContainer.addView(c);
        loopStatus = text(looperText(), 16, TEXT, true);
        loopStatus.setGravity(Gravity.CENTER);
        loopStatus.setPadding(0, dp(20), 0, dp(20));
        c.addView(loopStatus);
        looperRecordButton = new Button(this);
        styleButton(looperRecordButton, loopRecording ? "STOP RECORDING" : "RECORD LOOP", ORANGE);
        c.addView(looperRecordButton, new LinearLayout.LayoutParams(-1, dp(48)));
        looperRecordButton.setOnClickListener(v -> {
            if (!audioEngine.isRunning()) {
                Toast.makeText(this, "Mulai audio engine terlebih dahulu.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (loopRecording) audioEngine.stopLoopRecording();
            else audioEngine.startLoopRecording();
            refreshLooperUi();
        });
        looperPlayButton = new Button(this);
        styleButton(looperPlayButton, loopPlaying ? "STOP LOOP" : "PLAY LOOP", CYAN);
        c.addView(looperPlayButton, new LinearLayout.LayoutParams(-1, dp(48)));
        looperPlayButton.setOnClickListener(v -> {
            audioEngine.toggleLoopPlayback();
            refreshLooperUi();
        });
        Button clear = new Button(this);
        styleButton(clear, "CLEAR LOOP", RED);
        c.addView(clear, new LinearLayout.LayoutParams(-1, dp(48)));
        clear.setOnClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Hapus loop?")
                    .setMessage("Rekaman loop yang tersimpan akan dihapus.")
                    .setNegativeButton("BATAL", null)
                    .setPositiveButton("HAPUS", (d, w) -> {
                        audioEngine.clearLoop();
                        refreshLooperUi();
                    }).show();
        });
        Button audio = new Button(this);
        styleButton(audio, audioEngine.isRunning() ? "STOP AUDIO ENGINE" : "START AUDIO ENGINE", BLUE);
        c.addView(audio, new LinearLayout.LayoutParams(-1, dp(48)));
        audio.setOnClickListener(v -> toggleAudio());
    }

    private void buildPresetsPage() {
        section("PRESET LIBRARY", "60 preset bawaan. Simpan perubahan dengan SAVE atau buat variasi dengan SAVE AS.");
        LinearLayout c = card(BLUE);
        pageContainer.addView(c);
        presetTitle = text("ACTIVE  •  " + getPresetName(selectedPreset), 16, TEXT, true);
        c.addView(presetTitle);
        presetSpinner = spinner(getAllPresetNames());
        presetSpinner.setSelection(selectedPreset);
        c.addView(presetSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (first) { first = false; return; }
                applyPresetValues(position, true);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        Button save = new Button(this);
        styleButton(save, "SAVE • OVERWRITE SELECTED PRESET", GREEN);
        c.addView(save, new LinearLayout.LayoutParams(-1, dp(48)));
        save.setOnClickListener(v -> {
            savePresetProfile(selectedPreset);
            Toast.makeText(this, "Preset disimpan: " + getPresetName(selectedPreset), Toast.LENGTH_SHORT).show();
        });
        Button saveAs = new Button(this);
        styleButton(saveAs, "SAVE AS • CREATE NEW PRESET", CYAN);
        c.addView(saveAs, new LinearLayout.LayoutParams(-1, dp(48)));
        saveAs.setOnClickListener(v -> showSaveAsDialog());
        Button load = new Button(this);
        styleButton(load, "RELOAD SELECTED PRESET", PURPLE);
        c.addView(load, new LinearLayout.LayoutParams(-1, dp(48)));
        load.setOnClickListener(v -> {
            if (!loadPresetProfile(selectedPreset)) applyPresetValues(selectedPreset, false);
            refreshAllUi();
        });
        c.addView(text("Preset kustom disimpan secara lokal di penyimpanan aplikasi.", 10, MUTED, false));
    }

    private void buildAudioPage() {
        section("AUDIO INPUT / OUTPUT", "Pilih perangkat sebelum menjalankan mesin audio. USB memerlukan perangkat yang dikenali Android.");
        LinearLayout c = card(RED);
        pageContainer.addView(c);
        c.addView(text("INPUT DEVICE", 11, MUTED, true));
        inputSpinner = spinner(inputNames);
        inputSpinner.setSelection(indexOf(inputNames, selectedInput));
        c.addView(inputSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        inputSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (first) { first = false; return; }
                selectedInput = inputNames[position];
                pushSettings();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        c.addView(text("OUTPUT DEVICE", 11, MUTED, true));
        outputSpinner = spinner(outputNames);
        outputSpinner.setSelection(indexOf(outputNames, selectedOutput));
        c.addView(outputSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        outputSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (first) { first = false; return; }
                selectedOutput = outputNames[position];
                pushSettings();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        addAudioToggle(c);
        audioStatus = text(audioMessage, 11, MUTED, false);
        audioStatus.setPadding(0, dp(8), 0, dp(4));
        c.addView(audioStatus);
        c.addView(text("Catatan: dukungan USB audio bergantung pada interface, kabel OTG, Android, dan routing perangkat.", 10, MUTED, false));
    }

    private void addAudioToggle(LinearLayout parent) {
        audioToggleButton = new Button(this);
        styleButton(audioToggleButton, audioEngine.isRunning() ? "STOP AUDIO ENGINE" : "START MIC / USB AUDIO",
                audioEngine.isRunning() ? RED : GREEN);
        parent.addView(audioToggleButton, new LinearLayout.LayoutParams(-1, dp(50)));
        audioToggleButton.setOnClickListener(v -> toggleAudio());
    }

    private void toggleAudio() {
        if (audioEngine.isRunning()) {
            audioEngine.stop();
        } else if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
        } else {
            pushSettings();
            audioEngine.start();
        }
        refreshAudioButtons();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                pushSettings();
                audioEngine.start();
            } else {
                Toast.makeText(this, "Izin mikrofon diperlukan untuk memproses gitar.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void pushSettings() {
        if (audioEngine == null) return;
        AudioEngine.Settings s = new AudioEngine.Settings();
        for (int i = 0; i < EFFECT_COUNT; i++) {
            s.enabled[i] = effectEnabled[i];
            for (int j = 0; j < 3; j++) s.params[i][j] = effectParams[i][j];
        }
        s.master = masterVolume;
        s.gain = ampGain;
        s.bass = ampBass;
        s.middle = ampMiddle;
        s.treble = ampTreble;
        s.ampVolume = ampVolume;
        s.ampOn = ampOn;
        s.cabOn = cabOn;
        s.ampModel = selectedAmp;
        s.cabModel = selectedCab;
        s.input = selectedInput;
        s.output = selectedOutput;
        System.arraycopy(
        effectOrder,
        0,
        s.effectOrder,
        0,
        EFFECT_COUNT
);
        audioEngine.updateSettings(s);
    }

    private void applyPresetValues(int index, boolean refresh) {
        selectedPreset = clamp(index, 0, getAllPresetNames().length - 1):
        if (loadPresetProfile(selectedPreset)) {
           if (refresh) refreshAllUi();
           pushSettings();
           return;
}
        // Preset yang belum memiliki profil menggunakan urutan standar.
        loadEffectOrder(selectedPreset);
        }
        for (int i = 0; i < EFFECT_COUNT; i++) {
            boolean on;
            if (selectedPreset < 6) on = (i == 0 || i == 1 || i == 5) && ((selectedPreset + i) % 3 != 0);
            else if (selectedPreset < 20) on = i == 1 || i == 2 || (i == 6 && selectedPreset % 3 == 0) || (i == 7 && selectedPreset % 4 == 0);
            else if (selectedPreset < 34) on = i == 0 || i == 3 || i == 4 || (i == 1 && selectedPreset % 2 == 0);
            else if (selectedPreset < 45) on = i == 5 || i == 6 || i == 7 || (i == 2 && selectedPreset % 2 == 0);
            else on = ((selectedPreset + i * 2) % 5) < 2;
            effectEnabled[i] = on;
            for (int j = 0; j < 3; j++) {
                effectParams[i][j] = 25 + ((selectedPreset * 13 + i * 17 + j * 29) % 70);
            }
        }
        ampGain = 1.5f + (selectedPreset % 10) * .35f;
        ampVolume = .58f + (selectedPreset % 5) * .08f;
        ampBass = .35f + (selectedPreset % 4) * .12f;
        ampMiddle = .38f + ((selectedPreset + 1) % 4) * .12f;
        ampTreble = .35f + ((selectedPreset + 2) % 5) * .12f;
        selectedAmp = selectedPreset % ampNames.length;
        selectedCab = (selectedPreset + 5) % cabNames.length;
        masterVolume = .55f + (selectedPreset % 5) * .08f;
        ampOn = true;
        cabOn = true;
        pushSettings();
        if (refresh) refreshAllUi();
    }

    private void refreshAllUi() {
        updatePresetLabels();
        refreshEffectControlUi();
        refreshLooperUi();
        refreshAudioButtons();
        if (ampSpinner != null) ampSpinner.setSelection(selectedAmp);
        if (cabSpinner != null) cabSpinner.setSelection(selectedCab);
        if (masterSeek != null) masterSeek.setProgress(Math.round(masterVolume * 100));
        updateMasterValue(Math.round(masterVolume * 100));
    }

    private void showSaveAsDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Nama preset baru");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this).setTitle("SAVE AS • NEW PRESET")
                .setMessage("Masukkan nama preset yang ingin disimpan.")
                .setView(input).setNegativeButton("BATAL", null)
                .setPositiveButton("SIMPAN", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Nama preset tidak boleh kosong.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (String existing : getAllPresetNames()) {
                        if (existing.equalsIgnoreCase(name)) {
                            Toast.makeText(this, "Nama preset sudah digunakan.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    customPresetNames.add(name);
                    saveCustomPresetNames();
                    selectedPreset = getAllPresetNames().length - 1;
                    savePresetProfile(selectedPreset);
                    refreshAllUi();
                    Toast.makeText(this, "Preset baru disimpan: " + name, Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void saveCustomPresetNames() {
        android.content.SharedPreferences.Editor e = preferences.edit();
        org.json.JSONArray array = new org.json.JSONArray();
        for (String name : customPresetNames) array.put(name);
        e.putString("custom_preset_names", array.toString()).apply();
    }

    private void loadCustomPresetNames() {
        customPresetNames.clear();
        String raw = preferences.getString("custom_preset_names", "[]");
        try {
            org.json.JSONArray array = new org.json.JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String name = array.optString(i, "").trim();
                if (!name.isEmpty() && !customPresetNames.contains(name)) customPresetNames.add(name);
            }
        } catch (Exception ignored) {}
    }

    private String[] getAllPresetNames() {
        String[] all = new String[presetNames.length + customPresetNames.size()];
        System.arraycopy(presetNames, 0, all, 0, presetNames.length);
        for (int i = 0; i < customPresetNames.size(); i++) all[presetNames.length + i] = customPresetNames.get(i);
        return all;
    }

    private String getPresetName(int index) {
        String[] all = getAllPresetNames();
        return all[clamp(index, 0, all.length - 1)];
    }

    private void savePresetProfile(int index) {
        String key = "preset_" + index + "_";
        SharedPreferences.Editor e = preferences.edit();
        e.putInt(key + "amp", selectedAmp).putInt(key + "cab", selectedCab)
                .putBoolean(key + "ampOn", ampOn).putBoolean(key + "cabOn", cabOn)
                .putFloat(key + "gain", ampGain).putFloat(key + "bass", ampBass)
                .putFloat(key + "middle", ampMiddle).putFloat(key + "treble", ampTreble)
                .putFloat(key + "ampVolume", ampVolume).putFloat(key + "masterVolume", masterVolume);
        for (int i = 0; i < EFFECT_COUNT; i++) {
            e.putBoolean(key + "effect_" + i, effectEnabled[i]);
            for (int j = 0; j < 3; j++) e.putInt(key + "param_" + i + "_" + j, effectParams[i][j]);
        }
        e.apply();
saveEffectOrder(index);
    }

    private boolean loadPresetProfile(int index) {
        String key = "preset_" + index + "_";
        if (!preferences.contains(key + "amp")) return false;
        selectedAmp = clamp(preferences.getInt(key + "amp", 0), 0, ampNames.length - 1);
        selectedCab = clamp(preferences.getInt(key + "cab", 5), 0, cabNames.length - 1);
        ampOn = preferences.getBoolean(key + "ampOn", true);
        cabOn = preferences.getBoolean(key + "cabOn", true);
        ampGain = preferences.getFloat(key + "gain", 3f);
        ampBass = preferences.getFloat(key + "bass", .5f);
        ampMiddle = preferences.getFloat(key + "middle", .5f);
        ampTreble = preferences.getFloat(key + "treble", .6f);
        ampVolume = preferences.getFloat(key + "ampVolume", .7f);
        masterVolume = preferences.getFloat(key + "masterVolume", .72f);
        for (int i = 0; i < EFFECT_COUNT; i++) {
            effectEnabled[i] = preferences.getBoolean(key + "effect_" + i, false);
            for (int j = 0; j < 3; j++) effectParams[i][j] = preferences.getInt(key + "param_" + i + "_" + j, 50);
        }
        loadEffectOrder(index);
        pushSettings();
        return true;
    }

    private void updatePresetLabels() {
        if (presetTitle != null) {
            presetTitle.setText((currentPage.equals("HOME") ? "PRESET  •  " : "ACTIVE  •  ")
                    + getPresetName(selectedPreset));
        }
        if (presetSpinner != null && presetSpinner.getSelectedItemPosition() != selectedPreset) {
            presetSpinner.setSelection(selectedPreset);
        }
    }

    
    private void refreshEffectControlUi() {
        for (int i = 0; i < EFFECT_COUNT; i++) {
            if (pedalViews[i] != null) pedalViews[i].invalidate();
            for (int j = 0; j < 3; j++) {
                if (effectSeekBars[i][j] != null && effectSeekBars[i][j].getProgress() != effectParams[i][j])
                    effectSeekBars[i][j].setProgress(effectParams[i][j]);
                if (effectParamValues[i][j] != null && j < paramCount(i))
                    effectParamValues[i][j].setText(paramNames[i][j] + "  " + effectParams[i][j]);
            }
        }
    }

    private void updateMasterValue(int value) {
        if (masterValue != null) masterValue.setText("Master: " + value + "%");
    }

    private void updateEffectParameterFromKnob(int id, int index, int value) {
        effectParams[id][index] = clamp(value, 0, 100);
        if (effectSeekBars[id][index] != null) effectSeekBars[id][index].setProgress(effectParams[id][index]);
        if (effectParamValues[id][index] != null)
            effectParamValues[id][index].setText(paramNames[id][index] + "  " + effectParams[id][index]);
        if (pedalViews[id] != null) pedalViews[id].invalidate();
        pushSettings();
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
    }

    private String looperText() {
        if (loopRecording) return "● RECORDING • " + String.format(Locale.US, "%.1f s", audioEngine == null ? 0 : audioEngine.getLoopLengthSamples() / 44100f);
        if (loopPlaying) return "▶ LOOP PLAYING • " + String.format(Locale.US, "%.1f seconds", loopLength / 44100f);
        if (loopLength > 0) return "LOOP READY • " + String.format(Locale.US, "%.1f seconds", loopLength / 44100f);
        return "LOOP EMPTY • max 30 seconds";
    }

    private void addHomeButton(String label, int accent, String page) {
        Button b = new Button(this);
        styleButton(b, label, accent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(4), 0, dp(4));
        pageContainer.addView(b, lp);
        b.setOnClickListener(v -> showPage(page));
    }

    private void addControl(LinearLayout parent, String label, int min, int max,
        int initial, int accent, IntChanged changed) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, dp(5), 0, dp(3));
        parent.addView(group);
        TextView value = text(label + "  " + initial, 11, TEXT, true);
        group.addView(value);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(clamp(initial - min, 0, max - min));
        tintSeek(seek, accent);
        group.addView(seek);
        if (label.equals("MASTER VOLUME")) masterSeek = seek;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int v = progress + min;
                value.setText(label + "  " + v);
                changed.onChanged(v);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void addToggleRow(LinearLayout parent, String label, boolean initial,
                              ToggleChanged changed, int accent) {
        LinearLayout r = row();
        parent.addView(r);
        r.addView(text(label, 12, TEXT, true), new LinearLayout.LayoutParams(0, dp(44), 1));
        Button b = new Button(this);
        styleButton(b, initial ? "ON" : "OFF", accent);
        r.addView(b, new LinearLayout.LayoutParams(dp(80), dp(42)));
        b.setOnClickListener(v -> {
            boolean next = !"ON".equals(b.getText().toString());
            b.setText(next ? "ON" : "OFF");
            b.setBackground(bgShape(next ? accent : Color.DKGRAY, accent));
            changed.onChanged(next);
        });
    }

    private AdapterView.OnItemSelectedListener simpleSelection(IntChanged changed) {
        return new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (first) { first = false; return; }
                changed.onChanged(position);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
    }

    private int paramCount(int effect) {
        return effect <= 1 ? 2 : 3;
    }

    private String effectDescription(int id) {
        switch (id) {
            case 0: return "Noise gate • threshold / release";
            case 1: return "Compressor • sustain / output level";
            case 2: return "Overdrive • drive / tone / level";
            case 3: return "Distortion • gain / tone / level";
            case 4: return "Distortion+ • gain / tone / level";
            case 5: return "Modulation • rate / depth / mix";
            case 6: return "Delay • time / feedback / mix";
            default: return "Reverb • decay / tone / mix";
        }
    }

    private String noteText(float frequency) {
        if (frequency <= 0) return "--\nMenunggu sinyal";
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int midi = (int) Math.round(69 + 12 * (Math.log(frequency / 440.0) / Math.log(2)));
        int note = (midi % 12 + 12) % 12;
        int octave = midi / 12 - 1;
        double target = 440 * Math.pow(2, (midi - 69) / 12.0);
        int cents = (int) Math.round(1200 * Math.log(frequency / target) / Math.log(2));
        return names[note] + octave + "\n" + String.format(Locale.US, "%.1f Hz  •  %+d cents", frequency, cents);
    }

    @Override
    public void onStatus(String message) {
        runOnUiThread(() -> {
            audioMessage = message;
            if (audioStatus != null) audioStatus.setText(message);
            refreshAudioButtons();
        });
    }

    @Override
    public void onPitch(float frequencyHz) {
        tunerFrequency = frequencyHz;
        runOnUiThread(() -> {
            if (tunerNote != null) tunerNote.setText(noteText(frequencyHz));
        });
    }

    @Override
    public void onLooperChanged(boolean recording, boolean playing, int samples) {
        loopRecording = recording;
        loopPlaying = playing;
        loopLength = samples;
        runOnUiThread(this::refreshLooperUi);
    }

    private void refreshAudioButtons() {
        boolean running = audioEngine != null && audioEngine.isRunning();
        if (audioToggleButton != null) {
            audioToggleButton.setText(running ? "STOP AUDIO ENGINE" : "START MIC / USB AUDIO");
            audioToggleButton.setBackground(bgShape(Color.rgb(25, 34, 53), running ? RED : GREEN));
        }
        if (currentPage.equals("TUNER") && tunerNote != null && !running && tunerFrequency <= 0) {
            tunerNote.setText("--\nAudio berhenti");
        }
    }

    private void section(String title, String subtitle) {
        TextView t = text(title, 14, CYAN, true);
        t.setPadding(dp(2), dp(12), 0, dp(3));
        pageContainer.addView(t);
        TextView sub = text(subtitle, 10, MUTED, false);
        sub.setPadding(dp(2), 0, 0, dp(8));
        pageContainer.addView(sub);
    }

    private void sectionIn(LinearLayout parent, String title, String subtitle) {
        parent.addView(text(title, 14, CYAN, true));
        parent.addView(text(subtitle, 10, MUTED, false));
    }

    private void setupNavigation() {
    if (bottomNavigation == null) return;
    bottomNavigation.removeAllViews();
    bottomNavigation.setPadding(dp(4), dp(5), dp(4), dp(5));
    bottomNavigation.setBackgroundColor(Color.rgb(10, 16, 28));

    String[] pages = {"HOME", "PEDALBOARD", "AMP & CAB", "TUNER", "PRESETS"};
    String[] labels = {"HOME", "BOARD", "AMP", "TOOLS", "PRESET"};
    String[] symbols = {"⌂", "◈", "◉", "♫", "▤"};

    for (int i = 0; i < pages.length; i++) {
        final String page = pages[i];
        boolean active = page.equals(currentPage);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(3), dp(2), dp(3));
        item.setBackground(active
                ? bgShape(Color.rgb(20, 38, 55), CYAN)
                : bgShape(Color.rgb(10, 16, 28), Color.rgb(10, 16, 28)));

        TextView symbol = text(symbols[i], 17, active ? CYAN : MUTED, true);
        symbol.setGravity(Gravity.CENTER);
        item.addView(symbol);

        TextView caption = text(labels[i], 8, active ? TEXT : MUTED, true);
        caption.setGravity(Gravity.CENTER);
        item.addView(caption);

        bottomNavigation.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> {
            if ("TOOLS".equals(page)) showPage("TUNER");
            else showPage(page);
        });
    }
 }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
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

    private int gainToUi(float gain) {
        return clamp(Math.round((gain - .5f) / .075f), 0, 100);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void navigate(String page) {
        showPage(page);
    }

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
            setContentDescription(effectNames[id] + " pedal; ketuk bagian bawah untuk ON/OFF, geser kenop untuk parameter.");
        }

        private float d(float value) { return value * density; }

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
            setPaint(effectEnabled[effectId] ? accent : 0xFF526174, 1, false);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(2));
            canvas.drawCircle(x, y, radius + d(1), paint);
            paint.setStyle(Paint.Style.FILL);
            setPaint(0xFF263247, 1, false);
            canvas.drawCircle(x, y, radius, paint);
            double angle = Math.toRadians(135 + 270 * value / 100f);
            float x1 = x + (float) Math.cos(angle) * radius * .2f;
            float y1 = y + (float) Math.sin(angle) * radius * .2f;
            float x2 = x + (float) Math.cos(angle) * radius * .76f;
            float y2 = y + (float) Math.sin(angle) * radius * .76f;
            setPaint(Color.WHITE, 1, false);
            paint.setStrokeWidth(d(2));
            canvas.drawLine(x1, y1, x2, y2, paint);
            centered(canvas, label, x, y + radius + d(17), 0xFFDCE7F5, 8, true);
            centered(canvas, String.valueOf(value), x, y + d(4), Color.WHITE, 9, true);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), pad = d(7);
            rect.set(pad, d(4), w - pad, h - d(4));
            setPaint(0xFF050811, 1, false);
            canvas.drawRoundRect(rect, d(17), d(17), paint);
            RectF face = new RectF(pad + d(4), d(8), w - pad - d(4), h - d(8));
            setPaint(effectEnabled[effectId] ? accent : 0xFF354052, 1, false);
            canvas.drawRoundRect(face, d(14), d(14), paint);
            RectF inner = new RectF(face.left + d(5), face.top + d(5), face.right - d(5), face.bottom - d(5));
            setPaint(0xFF111A2A, 1, false);
            canvas.drawRoundRect(inner, d(11), d(11), paint);
            centered(canvas, "STRADA / EFFECTS", w / 2f, d(27), 0xFF9DB0C9, 8, true);
            centered(canvas, effectNames[effectId], w / 2f, d(48),
                    effectEnabled[effectId] ? accent : 0xFFDCE5F2, 16, true);
            float gap = w / (paramCount(effectId) + 1f);
            float cy = h * .49f;
            float radius = Math.min(d(18), w / (paramCount(effectId) == 2 ? 9f : 14f));
            for (int j = 0; j < paramCount(effectId); j++) {
                drawKnob(canvas, gap * (j + 1), cy, radius,
                        paramNames[effectId][j].toUpperCase(Locale.US), effectParams[effectId][j]);
            }
            float sx = w / 2f, sy = h - d(30);
            setPaint(0xFF070B12, 1, false);
            canvas.drawCircle(sx, sy, d(13), paint);
            setPaint(effectEnabled[effectId] ? accent : 0xFF68768B, 1, false);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(2));
            canvas.drawCircle(sx, sy, d(9), paint);
            paint.setStyle(Paint.Style.FILL);
            centered(canvas, effectEnabled[effectId] ? "ACTIVE • TAP TO BYPASS" : "BYPASSED • TAP TO ACTIVATE",
                    sx, h - d(8), effectEnabled[effectId] ? Color.WHITE : 0xFF9AA8BB, 8, true);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY(), w = getWidth(), h = getHeight();
            float cy = h * .49f;
            int count = paramCount(effectId);
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
                        float next = Math.abs(x - gap * (j + 1));
                        if (next < distance) { distance = next; nearest = j; }
                    }
                    if (nearest >= 0 && distance <= gap * .48f) {
                        draggingParam = nearest;
                        dragStartY = y;
                        dragStartValue = effectParams[effectId][nearest];
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
                    effectEnabled[effectId] = !effectEnabled[effectId];
                    pushSettings();
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
        if (audioEngine != null) audioEngine.release();
        super.onDestroy();
    }
    }
