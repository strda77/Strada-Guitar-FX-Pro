
package com.strada.guitarfXpro;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(16,17,20);
    private final int panel = Color.rgb(29,31,36);
    private final int orange = Color.rgb(255,132,40);

    private LinearLayout root;
    private TextView presetLabel;
    private TextView audioButton;
    private int presetIndex = 0;

    private final String[] presets = {
        "Clean Glass", "Blues Drive", "Classic Rock",
        "British Lead", "Metal Power", "Ambient Space"
    };

    private volatile boolean gate, overdrive, distortion;
    private volatile boolean chorus, delay, reverb;
    private volatile boolean audioRunning;
    private Thread audioThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView brand = text("STRADA", 14, orange, true);
        brand.setLetterSpacing(.22f);
        root.addView(brand);
        root.addView(text("Guitar FX Pro V2", 30, Color.WHITE, true));

        TextView subtitle = text(
            "AUDIO ENGINE • HEADPHONE TEST", 11, Color.LTGRAY, true
        );
        subtitle.setPadding(0, dp(3), 0, dp(18));
        root.addView(subtitle);

        LinearLayout preset = card();
        root.addView(preset);
        preset.addView(text("CURRENT PRESET", 11, orange, true));
        presetLabel = text(presets[presetIndex], 23, Color.WHITE, true);
        presetLabel.setPadding(0, dp(8), 0, dp(12));
        preset.addView(presetLabel);

        LinearLayout nav = new LinearLayout(this);
        preset.addView(nav);
        addButton(nav, "‹ PREV", () -> changePreset(-1), 1);
        addButton(nav, "NEXT ›", () -> changePreset(1), 1);

        TextView heading = text("EFFECT CHAIN", 16, Color.WHITE, true);
        heading.setPadding(0, dp(20), 0, dp(10));
        root.addView(heading);

        String[] names = {
            "Noise Gate", "Overdrive", "Distortion",
            "Chorus", "Delay", "Reverb"
        };
        String[] descriptions = {
            "Reduce quiet signal", "Warm saturation",
            "Hard clipping", "Modulated tone",
            "Echo repeats", "Simple room ambience"
        };

        for (int i = 0; i < names.length; i++) {
            addEffect(names[i], descriptions[i]);
        }

        TextView tools = text("AUDIO TEST", 16, Color.WHITE, true);
        tools.setPadding(0, dp(20), 0, dp(10));
        root.addView(tools);

        LinearLayout testRow = new LinearLayout(this);
        root.addView(testRow);
        audioButton = addButton(
            testRow, "START TEST TONE", this::toggleAudio, 1
        );

        TextView info = text(
            "Suara uji sintetis • Output mengikuti pengaturan audio HP",
            12, Color.LTGRAY, false
        );
        info.setPadding(0, dp(10), 0, dp(4));
        root.addView(info);

        LinearLayout toolRow = new LinearLayout(this);
        root.addView(toolRow);
        addButton(toolRow, "GUITAR TUNER",
            () -> toast("Tuner belum tersedia."), 1);
        addButton(toolRow, "LOOPER",
            () -> toast("Looper belum tersedia."), 1);

        TextView note = text(
            "V2 TEST BUILD • REAL GUITAR INPUT NOT IMPLEMENTED",
            10, Color.GRAY, false
        );
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(24), 0, dp(4));
        root.addView(note);
    }

    private void addEffect(String name, String description) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(name, 16, Color.WHITE, true));

        TextView d = text(description, 11, Color.LTGRAY, false);
        d.setPadding(0, dp(4), 0, 0);
        labels.addView(d);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView toggle = text("OFF", 12, Color.LTGRAY, true);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(14), dp(8), dp(14), dp(8));
        toggle.setBackground(roundBg(Color.rgb(50,52,58)));
        row.addView(toggle);

        toggle.setOnClickListener(v -> {
            boolean enabled = "OFF".contentEquals(toggle.getText());
            setEffect(name, enabled);
            toggle.setText(enabled ? "ON" : "OFF");
            toggle.setTextColor(enabled ? Color.BLACK : Color.LTGRAY);
            toggle.setBackground(roundBg(
                enabled ? orange : Color.rgb(50,52,58)
            ));
            toast(name + (enabled ? " ON" : " OFF"));
        });
    }

    private void setEffect(String name, boolean enabled) {
        switch (name) {
            case "Noise Gate": gate = enabled; break;
            case "Overdrive": overdrive = enabled; break;
            case "Distortion": distortion = enabled; break;
            case "Chorus": chorus = enabled; break;
            case "Delay": delay = enabled; break;
            case "Reverb": reverb = enabled; break;
        }
    }

    private void toggleAudio() {
        if (audioRunning) {
            audioRunning = false;
            audioButton.setText("START TEST TONE");
            toast("Menghentikan suara uji...");
            return;
        }

        audioRunning = true;
        audioButton.setText("STOP TEST TONE");
        audioThread = new Thread(this::runAudio, "StradaAudio");
        audioThread.start();
        toast("Suara uji dimulai. Atur volume rendah dahulu.");
    }

    private void runAudio() {
        final int sampleRate = 44100;
        final int frames = 512;
        int min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );

        if (min <= 0) {
            audioRunning = false;
            runOnUiThread(() -> {
                audioButton.setText("START TEST TONE");
                toast("Perangkat audio tidak dapat dimulai.");
            });
            return;
        }

        AudioTrack track = null;

        try {
            track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(min, frames * 4),
                AudioTrack.MODE_STREAM
            );

            short[] output = new short[frames];
            float[] chorusBuffer = new float[1800];
            float[] delayBuffer = new float[12000];
            float[] reverbBuffer = new float[6000];

            int chorusPos = 0, delayPos = 0, reverbPos = 0;
            double phase = 0, lfo = 0;
            double step = 2.0 * Math.PI * 220.0 / sampleRate;
            double lfoStep = 2.0 * Math.PI * 0.35 / sampleRate;

            track.play();

            while (audioRunning) {
                for (int i = 0; i < frames; i++) {
                    float x = (float)(
                        Math.sin(phase)
                        + 0.35 * Math.sin(2 * phase)
                        + 0.15 * Math.sin(3 * phase)
                    ) * 0.18f;

                    phase += step;
                    lfo += lfoStep;
                    if (phase >= 2 * Math.PI) phase -= 2 * Math.PI;
                    if (lfo >= 2 * Math.PI) lfo -= 2 * Math.PI;

                    if (gate && Math.abs(x) < 0.025f) x = 0;

                    if (overdrive) {
                        x = (float)Math.tanh(x * 8.0);

                    if (distortion) {
                        x = Math.max(-0.72f, Math.min(0.72f, x * 12.0f));

                    if (chorus) {
                        int d = 350 + (int)(180 * (1 + Math.sin(lfo)));
                        int read = (chorusPos - d + chorusBuffer.length)
                            % chorusBuffer.length;
                        float delayed = chorusBuffer[read];
                        chorusBuffer[chorusPos] = x;
                        x += delayed * 0.35f;
                        chorusPos = (chorusPos + 1) % chorusBuffer.length;
                    }

                    if (delay) {
                        float echo = delayBuffer[delayPos];
                        delayBuffer[delayPos] = x + echo * 0.35f;
                        x += echo * 0.40f;
                        delayPos = (delayPos + 1) % delayBuffer.length;
                    }

                    if (reverb) {
                        float room = reverbBuffer[reverbPos];
                        reverbBuffer[reverbPos] = x + room * 0.40f;
                        x += room * 0.25f;
                        reverbPos = (reverbPos + 1) % reverbBuffer.length;
                    }

                    x = Math.max(-0.95f, Math.min(0.95f, x));
                    output[i] = (short)(x * 32767);
                }

                track.write(output, 0, output.length);
            }
        } catch (Exception e) {
            runOnUiThread(() -> toast("Audio error: " + e.getMessage()));
        } finally {
            if (track != null) {
                try { track.pause(); } catch (Exception ignored) {}
                try { track.flush(); } catch (Exception ignored) {}
                try { track.release(); } catch (Exception ignored) {}
            }
            audioRunning = false;
            runOnUiThread(() -> {
                if (audioButton != null) audioButton.setText("START TEST TONE");
            });
        }
    }

    private LinearLayout card() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(16), dp(16), dp(16));
        b.setBackground(roundBg(panel));
        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    private TextView addButton(
        LinearLayout parent, String label, Runnable action, float weight
    ) {
        TextView b = text(label, 12, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(13), dp(10), dp(13));
        b.setBackground(roundBg(Color.rgb(54,56,63)));
        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(0, -2, weight);
        p.setMargins(dp(3), dp(2), dp(3), dp(2));
        parent.addView(b, p);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private android.graphics.drawable.GradientDrawable roundBg(int color) {
        android.graphics.drawable.GradientDrawable d =
            new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        return d;
    }

    private void changePreset(int delta) {
        presetIndex = (presetIndex + delta + presets.length) % presets.length;
        presetLabel.setText(presets[presetIndex]);
        toast("Preset dipilih: " + presets[presetIndex]);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        audioRunning = false;
        super.onDestroy();
    }
}
