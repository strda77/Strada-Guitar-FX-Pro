package com.strada.guitarfXpro;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * Strada Guitar FX Pro - Full Color UI foundation.
 * Audio engine is a basic microphone monitor with simple digital effects,
 * not a studio-grade guitar amp modeler. Test on the target phone before use.
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
        "Indie Drive", "Alternative Rock", "Grunge Drive", "Punk Crunch", "Experimental"
    };
    private final String[] effectNames = {
        "GATE", "COMP", "OVERDRIVE", "DISTORTION",
        "DISTORTION+", "MODULATION", "DELAY", "REVERB"
    };
    private final int[] effectColors = {
        CYAN, BLUE, GREEN, RED, PURPLE, Color.rgb(220, 55, 220),
        Color.rgb(0, 220, 230), ORANGE
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
    private final boolean[] effectOn = new boolean[8];
    private final float[][] params = new float[8][];
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
    private LinearLayout root;
    private TextView presetTitle, audioStatus, masterValue, tunerNote, loopStatus;
    private SeekBar masterSeek;
    private Spinner presetSpinner, ampSpinner, cabSpinner, inputSpinner, outputSpinner;
    private boolean audioRunning = false;
    private Thread audioThread;
    private volatile float masterVolume = 0.72f;
    private volatile float ampGain = 3.0f, ampBass = 0.50f, ampMiddle = 0.50f,
            ampTreble = 0.60f, ampVolume = 0.70f;
    private volatile boolean ampOn = true, cabOn = true;
    private volatile boolean loopRecording = false, loopPlaying = false;
    private volatile int selectedPreset = 0, selectedAmp = 0, selectedCab = 5;
    private volatile float tunerFrequency = 0f;
    private volatile String selectedInput = "Microphone (HP)";
    private volatile String selectedOutput = "System / Headphone";

    private LinearLayout pageContainer;
    private LinearLayout bottomNavigation;
    private ScrollView mainScroll;
    private String currentPage = "HOME";

    @Override 
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        for (int i = 0; i < params.length; i++) {
            params[i] = new float[paramNames[i].length];
            for (int j = 0; j < params[i].length; j++) params[i][j] = 50f;
        }
        applyPresetValues(0);
        buildUi();
    }
    
    private void buildUi() {
    LinearLayout screen = new LinearLayout(this);
    screen.setOrientation(LinearLayout.VERTICAL);
    screen.setBackgroundColor(BG);

    pageContainer = new LinearLayout(this);
    pageContainer.setOrientation(LinearLayout.VERTICAL);

    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(BG);
    scroll.addView(pageContainer);

    screen.addView(scroll, new LinearLayout.LayoutParams(
        -1, 0, 1
    ));

    bottomNavigation = new LinearLayout(this);
    bottomNavigation.setOrientation(LinearLayout.HORIZONTAL);
    bottomNavigation.setGravity(Gravity.CENTER);
    bottomNavigation.setPadding(dp(4), dp(6), dp(4), dp(6));
    bottomNavigation.setBackgroundColor(Color.rgb(24, 27, 36));

    screen.addView(bottomNavigation, new LinearLayout.LayoutParams(
        -1, dp(64)
    ));

        mainScroll = scroll;
        setContentView(screen);

        root = pageContainer;
        showPage("HOME");
        setupNavigation();
    }
    }
    
    private void showPage(String page) {
    currentPage = page;

    if (pageContainer == null) return;

    pageContainer.removeAllViews();
    root = pageContainer;

    switch (page) {
        case "HOME": {
            TextView logo = text("STRADA", 34, CYAN, true);
            logo.setLetterSpacing(0.12f);
            logo.setPadding(dp(12), dp(20), dp(12), 0);
            pageContainer.addView(logo);

            TextView subtitle = text(
                "GUITAR FX PRO  •  MULTI EFFECTS",
                13, PURPLE, true
            );
            subtitle.setPadding(
                dp(14), dp(2), dp(12), dp(18)
            );
            pageContainer.addView(subtitle);

            section(
                "MAIN CONTROL",
                "Pusat kontrol suara dan preset Strada."
            );

            Button presetButton = new Button(this);
            presetButton.setText("PRESET LIBRARY  •  50 TONES");
            presetButton.setAllCaps(false);
            presetButton.setOnClickListener(v -> {
                showPage("PRESETS");
                setupNavigation();
            });
            pageContainer.addView(presetButton);

            Button pedalButton = new Button(this);
            pedalButton.setText("OPEN PEDALBOARD  →");
            pedalButton.setAllCaps(false);
            pedalButton.setOnClickListener(v -> {
                showPage("PEDALBOARD");
                setupNavigation();
            });
            pageContainer.addView(pedalButton);

            Button ampButton = new Button(this);
            ampButton.setText("AMPLIFIER & CABINET  →");
            ampButton.setAllCaps(false);
            ampButton.setOnClickListener(v -> {
                showPage("AMP & CAB");
                setupNavigation();
            });
            pageContainer.addView(ampButton);

            Button tunerButton = new Button(this);
            tunerButton.setText("GUITAR TUNER  →");
            tunerButton.setAllCaps(false);
            tunerButton.setOnClickListener(v -> {
                showPage("TUNER");
                setupNavigation();
            });
            pageContainer.addView(tunerButton);

            Button looperButton = new Button(this);
            looperButton.setText("LOOPER  →");
            looperButton.setAllCaps(false);
            looperButton.setOnClickListener(v -> {
                showPage("LOOPER");
                setupNavigation();
            });
            pageContainer.addView(looperButton);

            Button audioButton = new Button(this);
            audioButton.setText("AUDIO & SETTINGS  →");
            audioButton.setAllCaps(false);
            audioButton.setOnClickListener(v -> {
                showPage("AUDIO");
                setupNavigation();
            });
            pageContainer.addView(audioButton);
            break;
        }

        case "PEDALBOARD":
            buildPedalboardPage();
            break;

        case "AMP & CAB":
            section(
                "AMPLIFIER & CABINET",
                "Pilih amplifier, cabinet, gain, dan karakter tone."
            );
            break;

        case "TUNER":
            section(
                "GUITAR TUNER",
                "Gunakan input audio untuk mendeteksi nada."
            );
            break;

        case "LOOPER":
            section(
                "LOOPER",
                "Kontrol perekaman dan pemutaran loop."
            );
            break;

        case "PRESETS":
            section(
                "PRESET LIBRARY",
                "Pilih dan atur preset suara gitar."
            );
            break;

        case "AUDIO":
            section(
                "AUDIO & SETTINGS",
                "Pengaturan input, output, dan audio."
            );
            break;

        default:
            section("STRADA", "Halaman tidak ditemukan.");
            break;
    }

    if (mainScroll != null) {
        mainScroll.post(() -> mainScroll.scrollTo(0, 0));
    }
}

    private void buildPedalboardPage() {  
    section("FULL COLOR PEDALBOARD",  
        "Tap each pedal to switch it ON/OFF");  

    for (int i = 0; i < effectNames.length; i++) {  
    addEffectCard(i);  
}
    }
        
    private void setupNavigation() {
    if (bottomNavigation == null) return;

    bottomNavigation.removeAllViews();

    String[] pages = {
        "HOME", "PEDALBOARD", "AMP & CAB",
        "TUNER", "LOOPER", "PRESETS", "AUDIO"
    };

    String[] labels = {
        "Home", "Pedal", "Amp/Cab",
        "Tuner", "Looper", "Preset", "Audio"
    };

    for (int i = 0; i < pages.length; i++) {
        final String page = pages[i];

        TextView button = text(
            labels[i],
            10,
            page.equals(currentPage) ? CYAN : TEXT,
            page.equals(currentPage)
        );

        button.setGravity(android.view.Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(2), dp(8), dp(2), dp(8));
        button.setBackgroundColor(Color.TRANSPARENT);

        bottomNavigation.addView(
            button,
            new LinearLayout.LayoutParams(0, -1, 1)
        );

        button.setOnClickListener(v -> {
            showPage(page);
            setupNavigation();
        });
    }
}

    private void scrollToPage(String page) {
    if (mainScroll == null || root == null) return;

    String target;

    switch (page) {
        case "PEDALBOARD":
            target = "FULL COLOR PEDALBOARD";
            break;
        case "AMP & CAB":
            target = "AMP SELECTOR";
            break;
        case "TUNER":
            target = "TUNER";
            break;
        case "LOOPER":
            target = "LOOPER";
            break;
        case "PRESETS":
            target = "PRESET LIBRARY";
            break;
        case "AUDIO":
            target = "AUDIO INPUT / OUTPUT";
            break;
        default:
            mainScroll.smoothScrollTo(0, 0);
            return;
    }

    View found = findTextTarget(root, target);

    if (found != null) {
        int[] location = new int[2];
        found.getLocationInWindow(location);
        int[] scrollLocation = new int[2];
        mainScroll.getLocationInWindow(scrollLocation);

        int y = mainScroll.getScrollY()
                + location[1] - scrollLocation[1];

        mainScroll.smoothScrollTo(0, Math.max(0, y));
    }
}

    private View findTextTarget(
        android.view.ViewGroup parent, String target) {

    for (int i = 0; i < parent.getChildCount(); i++) {
        View child = parent.getChildAt(i);

        if (child instanceof TextView) {
            String value = ((TextView) child)
                    .getText().toString();

            if (value.contains(target)) {
                return child;
            }
        }

        if (child instanceof android.view.ViewGroup) {
            View found = findTextTarget(
                    (android.view.ViewGroup) child, target);

            if (found != null) return found;
        }
    }

    return null;
        }
        
    private void addEffectCard(int id) {
    if (id < 0 || id >= effectNames.length) return;

    // Panel pedal hardware
    PedalFaceView pedal = new PedalFaceView(this, id);

    LinearLayout.LayoutParams pedalParams =
            new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(205));

    pedalParams.setMargins(dp(8), dp(8), dp(8), dp(4));
    root.addView(pedal, pedalParams);

    // Pertahankan kontrol parameter asli.
    // Metode ini adalah metode lama yang hanya diganti namanya.
    addEffectControls(id);
}

private class PedalFaceView extends View {
    private final int effectId;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private final int accent;
    private final float density;

    PedalFaceView(Context context, int id) {
        super(context);
        effectId = id;
        accent = effectColors[id];
        density = getResources().getDisplayMetrics().density;

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setClickable(true);

        setOnClickListener(v -> {
            effectOn[effectId] = !effectOn[effectId];
            invalidate();
        });

        setContentDescription(
                effectNames[id] + ", ketuk untuk mengubah ON/OFF");
    }

    private float d(float value) {
        return value * density;
    }

    private void paint(int color, float size, boolean bold) {
        p.setColor(color);
        p.setTextSize(d(size));
        p.setTypeface(bold
                ? Typeface.create("sans-serif", Typeface.BOLD)
                : Typeface.create("sans-serif", Typeface.NORMAL));
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setStrokeWidth(d(1));
    }

    private void centered(Canvas c, String text,
                          float x, float y, int color,
                          float size, boolean bold) {
        paint(color, size, bold);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, x, y, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void knob(Canvas c, float x, float y,
                      float radius, String label, float position) {
        // Bayangan kenop
        paint(0x88000000, 1, false);
        c.drawCircle(x + d(1), y + d(2), radius + d(1), p);

        // Cincin luar
        paint(0xFF080D16, 1, false);
        c.drawCircle(x, y, radius + d(3), p);

        paint(effectOn[effectId] ? accent : 0xFF526174, 1, false);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d(2));
        c.drawCircle(x, y, radius + d(1), p);
        p.setStyle(Paint.Style.FILL);

        // Badan kenop
        paint(0xFF263247, 1, false);
        c.drawCircle(x, y, radius, p);

        paint(0xFF64758C, 1, false);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d(1));
        c.drawCircle(x, y, radius * 0.72f, p);
        p.setStyle(Paint.Style.FILL);

        // Garis penunjuk posisi
        float angle = (float) Math.toRadians(
                135 + 270 * Math.max(0f, Math.min(1f, position)));

        float x1 = x + (float) Math.cos(angle) * radius * 0.2f;
        float y1 = y + (float) Math.sin(angle) * radius * 0.2f;
        float x2 = x + (float) Math.cos(angle) * radius * 0.76f;
        float y2 = y + (float) Math.sin(angle) * radius * 0.76f;

        paint(0xFFFFFFFF, 1, false);
        p.setStrokeWidth(d(2));
        c.drawLine(x1, y1, x2, y2, p);

        centered(c, label, x, y + radius + d(19),
                0xFFDCE7F5, 10, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float pad = d(8);

        // Dasar pedal
        body.set(pad, d(5), w - pad, h - d(5));

        paint(0xFF050811, 1, false);
        canvas.drawRoundRect(body, d(18), d(18), p);

        // Badan berwarna
        RectF face = new RectF(
                pad + d(4), d(9),
                w - pad - d(4), h - d(9));

        paint(effectOn[effectId] ? accent : 0xFF354052, 1, false);
        canvas.drawRoundRect(face, d(15), d(15), p);

        // Panel tengah
        RectF inner = new RectF(
                face.left + d(5), face.top + d(5),
                face.right - d(5), face.bottom - d(5));

        paint(0xFF111A2A, 1, false);
        canvas.drawRoundRect(inner, d(12), d(12), p);

        // Garis aksen
        paint(accent, 1, false);
        canvas.drawRoundRect(
                new RectF(inner.left, inner.top,
                          inner.left + d(5), inner.bottom),
                d(3), d(3), p);

        centered(canvas, "STRADA  /  EFFECTS",
                w / 2f, d(31), 0xFF9DB0C9, 9, true);

        String name = effectNames[effectId];
        float titleSize = name.length() > 12 ? 15 : 18;

        centered(canvas, name,
                w / 2f, d(55),
                effectOn[effectId] ? accent : 0xFFDCE5F2,
                titleSize, true);

        // Lampu status
        float ledX = w - d(35);
        float ledY = d(31);

        paint(effectOn[effectId] ? accent : 0xFF455063, 1, false);
        canvas.drawCircle(ledX, ledY, d(7), p);

        paint(0xFF080D16, 1, false);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d(1));
        canvas.drawCircle(ledX, ledY, d(9), p);
        p.setStyle(Paint.Style.FILL);

        // Tiga kenop dekoratif.
        // Tidak mengubah params; kontrol asli tetap berada di bawah pedal.
        float cy = h * 0.49f;
        float gap = w / 4f;
        float radius = Math.min(d(19), w / 14f);

        knob(canvas, gap, cy, radius, "DRIVE", 0.60f);
        knob(canvas, gap * 2, cy, radius, "TONE", 0.48f);
        knob(canvas, gap * 3, cy, radius, "LEVEL", 0.72f);

        // Sakelar kaki
        float sx = w / 2f;
        float sy = h - d(34);

        paint(0xFF070B12, 1, false);
        canvas.drawCircle(sx, sy, d(15), p);

        paint(effectOn[effectId] ? accent : 0xFF68768B, 1, false);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d(2));
        canvas.drawCircle(sx, sy, d(11), p);
        p.setStyle(Paint.Style.FILL);

        paint(effectOn[effectId] ? accent : 0xFF526174, 1, false);
        canvas.drawCircle(sx, sy, d(5), p);

        centered(canvas,
                effectOn[effectId] ? "● ACTIVE — TAP TO BYPASS"
                                   : "○ BYPASSED — TAP TO ACTIVATE",
                sx, h - d(10),
                effectOn[effectId] ? 0xFFFFFFFF : 0xFF9AA8BB,
                9, true);
    }
}

    private void addEffectControls(int id) {
        final int accent = effectColors[id];
        LinearLayout c = card(accent);
        root.addView(c);
        LinearLayout head = row();
        c.addView(head);
        TextView title = text("●  " + effectNames[id], 16, accent, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button toggle = new Button(this);
        toggle.setText(effectOn[id] ? "ON" : "OFF");
        toggle.setTextColor(TEXT);
        toggle.setTextSize(11);
        toggle.setAllCaps(false);
        toggle.setBackground(bgShape(effectOn[id] ? accent : Color.rgb(43, 49, 65), accent));
        head.addView(toggle, new LinearLayout.LayoutParams(dp(82), dp(42)));
        toggle.setOnClickListener(v -> {
            effectOn[id] = !effectOn[id];
            toggle.setText(effectOn[id] ? "ON" : "OFF");
            toggle.setBackground(bgShape(effectOn[id] ? accent : Color.rgb(43, 49, 65), accent));
        });
        c.addView(text(effectDescription(id), 11, MUTED, false));
        for (int j = 0; j < paramNames[id].length; j++) {
            final int paramIndex = j;
            addControl(c, paramNames[id][j], 0, 100, (int) params[id][j], accent,
                p -> params[id][paramIndex] = p);
        }
    }

    private String effectDescription(int id) {
        switch (id) {
            case 0: return "Noise gate • threshold / release";
            case 1: return "Compressor • sustain and output level";
            case 2: return "Overdrive • soft clipping / tone";
            case 3: return "Distortion • high-gain clipping";
            case 4: return "Distortion+ • brighter hard clipping";
            case 5: return "Modulation • chorus-style modulation";
            case 6: return "Delay • feedback echo";
            default: return "Reverb • simple ambience";
        }
    }

    private void refreshEffectCards() {
        // Rebuild UI so controls show the new preset values.
        // Avoid calling buildUi() because it would reset the whole screen and listeners.
        // Current selected preset values are stored in params/effectOn.
        for (int i = 0; i < effectOn.length; i++) {
            effectOn[i] = ((selectedPreset + i * 3) % 7) < 3;
        }
        // State changes apply immediately to the audio thread; visual toggles update on next screen rebuild.
        // The preset title and all parameter values remain available in state.
    }

    private void applyPresetValues(int index) {
        for (int i = 0; i < effectOn.length; i++) effectOn[i] = ((index + i * 3) % 7) < 3;
        ampGain = 1.5f + (index % 10) * 0.35f;
        masterVolume = 0.55f + (index % 5) * 0.08f;
        if (masterSeek != null) masterSeek.setProgress((int)(masterVolume * 100));
    }

    private void addControl(LinearLayout parent, String label, int min, int max, int initial,
                            int accent, java.util.function.IntConsumer changed) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, dp(4), 0, dp(3));
        parent.addView(line);
        TextView value = text(label + "  " + initial, 11, TEXT, true);
        line.addView(value);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(initial - min);
        tintSeek(seek, accent);
        line.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                value.setText(label + "  " + (p + min));
                changed.accept(p + min);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
    }

    private interface ToggleChanged { void onChanged(boolean value); }
    private void addToggleRow(LinearLayout parent, String label, boolean initial,
                              ToggleChanged changed, int accent) {
        LinearLayout r = row();
        parent.addView(r);
        TextView name = text(label, 12, TEXT, true);
        r.addView(name, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button b = new Button(this);
        b.setText(initial ? "ON" : "OFF");
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackground(bgShape(initial ? accent : Color.DKGRAY, accent));
        r.addView(b, new LinearLayout.LayoutParams(dp(80), dp(44)));
        final boolean[] state = {initial};
        b.setOnClickListener(v -> {
            state[0] = !state[0];
            b.setText(state[0] ? "ON" : "OFF");
            b.setBackground(bgShape(state[0] ? accent : Color.DKGRAY, accent));
            changed.onChanged(state[0]);
        });
        changed.onChanged(initial);
    }

    private void toggleAudio() {
        if (audioRunning) {
            audioRunning = false;
            audioStatus.setText("Status: stopping audio...");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            return;
        }
        audioRunning = true;
        audioStatus.setText("Status: starting • " + selectedInput + " → " + selectedOutput);
        audioThread = new Thread(this::runAudio, "StradaAudio");
        audioThread.start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1001) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) toggleAudio();
            else Toast.makeText(this, "Izin mikrofon diperlukan.", Toast.LENGTH_LONG).show();
        }
    }

    private void runAudio() {
        final int rate = 44100, frames = 256;
        int inMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int outMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord recorder = null;
        AudioTrack track = null;
        try {
            if (inMin <= 0 || outMin <= 0) throw new IllegalStateException("Unsupported audio format");
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(inMin, frames * 8));
            track = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(outMin, frames * 8), AudioTrack.MODE_STREAM);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED || track.getState() != AudioTrack.STATE_INITIALIZED)
                throw new IllegalStateException("Audio device initialization failed");
            short[] in = new short[frames], out = new short[frames];
            float[] delayBuf = new float[rate / 2];
            float[] reverbBuf = new float[rate / 5];
            int dp = 0, rp = 0;
            float lowState = 0, dcState = 0;
            track.play();
            recorder.startRecording();
            runOnUiThread(() -> audioStatus.setText("Status: audio running • use headphones to reduce feedback"));
            while (audioRunning) {
                int count = recorder.read(in, 0, frames);
                if (count < 0) throw new IllegalStateException("Mic read error " + count);
                for (int i = 0; i < count; i++) {
                    float x = in[i] / 32768f;
                    if (effectOn[0] && Math.abs(x) < (params[0][0] / 10000f)) x = 0;
                    if (effectOn[1]) x *= 1.0f + params[1][0] / 100f;
                    if (effectOn[2]) x = (float)Math.tanh(x * (1.0f + params[2][0] / 12f));
                    if (effectOn[3]) x = Math.max(-0.8f, Math.min(0.8f, x * (1.0f + params[3][0] / 10f)));
                    if (effectOn[4]) x = (float)Math.tanh(x * (1.0f + params[4][0] / 8f));
                    if (effectOn[5]) x *= 1.0f + 0.18f * (float)Math.sin(System.nanoTime() / 1.0e8) * params[5][1] / 100f;
                    if (effectOn[6]) {
                        float echo = delayBuf[dp];
                        delayBuf[dp] = x + echo * (params[6][1] / 140f);
                        x += echo * (params[6][2] / 150f);
                        dp = (dp + 1) % delayBuf.length;
                    }
                    if (effectOn[7]) {
                        float rv = reverbBuf[rp];
                        reverbBuf[rp] = x + rv * 0.55f;
                        x += rv * (params[7][2] / 220f);
                        rp = (rp + 1) % reverbBuf.length;
                    }
                    if (ampOn) {
                        x *= ampGain * (0.5f + ampVolume);
                        // Basic one-pole filter placeholder for cabinet/tone; not an IR amp model.
                        float cutoff = Math.max(0.03f, Math.min(0.8f, 0.15f + ampTreble * 0.45f - ampBass * 0.08f));
                        lowState += cutoff * (x - lowState);
                        x = lowState * (0.75f + ampMiddle * 0.5f);
                    }
                    if (cabOn) {
                        dcState += 0.12f * (x - dcState);
                        x = dcState;
                    }
                    x *= masterVolume;
                    x = Math.max(-0.92f, Math.min(0.92f, x));
                    out[i] = (short)(x * 32767f);
                }
                if (count > 0) track.write(out, 0, count);
                estimatePitch(in, count, rate);
            }
        } catch (Exception e) {
            runOnUiThread(() -> {
                audioStatus.setText("Audio error: " + e.getMessage());
                Toast.makeText(this, "Audio error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
                } finally {
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) {}
                recorder.release();
            }

            if (track != null) {
                try {
                    track.pause();
                    track.flush();
                } catch (Exception ignored) {}
                track.release();
            }

            audioRunning = false;

            runOnUiThread(() -> {
                if (audioStatus != null &&
                        !audioStatus.getText().toString().startsWith("Audio error:")) {
                    audioStatus.setText("Status: audio stopped");
                }
            });
        }
    }

    private void estimatePitch(short[] samples, int count, int rate) {
        if (count < 128) return;
        double crossings = 0;
        for (int i = 1; i < count; i++) if (samples[i - 1] <= 0 && samples[i] > 0) crossings++;
        double freq = crossings * rate / count / 2.0;
        if (freq > 50 && freq < 1200) {
            tunerFrequency = (float)freq;
            String[] names = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
            int midi = (int)Math.round(69 + 12 * (Math.log(freq / 440.0) / Math.log(2)));
            String note = names[(midi % 12 + 12) % 12];
            runOnUiThread(() -> tunerNote.setText(note + "\n" + String.format(Locale.US, "%.1f Hz", tunerFrequency)));
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(9);
        c.setLayoutParams(p);
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
        t.setPadding(dp(2), dp(14), 0, dp(3));
        root.addView(t);
        TextView sub = text(subtitle, 10, MUTED, false);
        sub.setPadding(dp(2), 0, 0, dp(7));
        root.addView(sub);
    }

    private void button(LinearLayout parent, String label, int accent, float weight, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(TEXT);
    b.setTextSize(11);
    b.setAllCaps(false);
    b.setBackground(bgShape(Color.rgb(25, 34, 53), accent));

    LinearLayout.LayoutParams p;

    if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
        p = new LinearLayout.LayoutParams(0, dp(46), weight);
    } else {
        p = new LinearLayout.LayoutParams(-1, dp(46));
    }

    p.setMargins(dp(3), dp(4), dp(3), dp(4));
    parent.addView(b, p);
    b.setOnClickListener(v -> action.run());
}

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
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

    private void space(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        audioRunning = false;
        super.onDestroy();
    }
}
