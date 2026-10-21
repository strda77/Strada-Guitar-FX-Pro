
package com.strada.guitarfXpro;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private final int bg = Color.rgb(16, 17, 20);
    private final int panel = Color.rgb(29, 31, 36);
    private final int orange = Color.rgb(255, 132, 40);

    private LinearLayout root;
    private TextView presetLabel;
    private TextView audioButton;
    private TextView ampButton;
    private TextView volumeLabel;
    private TextView statusLabel;

    private final TextView[] effectButtons = new TextView[6];

    private final String[] effectNames = {
        "Noise Gate", "Overdrive", "Distortion",
        "Chorus", "Delay", "Reverb"
    };

    private final String[] presets = {
        "Clean Glass", "Warm Clean", "Studio Clean",
        "Bright Clean", "Jazz Clean", "Chime Clean",
        "Blues Drive", "Vintage Blues", "Texas Blues",
        "Crunch Blues", "Classic Rock", "Arena Rock",
        "British Crunch", "Classic Lead", "British Lead",
        "Modern Lead", "Hard Rock", "Power Rock",
        "Vintage Stack", "Hot Stack", "Metal Power",
        "Classic Metal", "Heavy Metal", "Modern Metal",
        "Thrash Metal", "Dark Metal", "Tight Metal",
        "Djent Drive", "Fuzz Garage", "Vintage Fuzz",
        "Octave Fuzz", "Smooth Solo", "Singing Lead",
        "Sustain Lead", "Wide Chorus", "Dream Chorus",
        "Clean Chorus", "Slapback Rock", "Tape Echo",
        "Digital Echo", "Ambient Space", "Deep Ambient",
        "Shimmer Space", "Dark Ambient", "Lo-Fi Drive",
        "Indie Drive", "Alternative Rock", "Grunge Drive",
        "Punk Crunch", "Experimental"
    };

    private volatile boolean gate;
    private volatile boolean overdrive;
    private volatile boolean distortion;
    private volatile boolean chorus;
    private volatile boolean delay;
    private volatile boolean reverb;
    private volatile boolean ampEnabled = true;
    private volatile boolean audioRunning;
    private volatile float masterVolume = 0.75f;

    private volatile float ampGain = 3.0f;
    private volatile float cabCutoff = 0.12f;

    private int presetIndex = 0;
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

        TextView brand = text("STRADA GUITAR FX", 13, orange, true);
        brand.setLetterSpacing(0.16f);
        root.addView(brand);

        root.addView(text("Guitar FX Pro V3", 28, Color.WHITE, true));

        TextView subtitle = text(
            "GUITAR AUDIO ENGINE • FOUNDATION BUILD",
            10, Color.LTGRAY, true
        );
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout presetCard = card();
        root.addView(presetCard);

        presetCard.addView(text(
            "50 PRESET CONFIGURATIONS", 11, orange, true
        ));

        presetLabel = text(presets[0], 22, Color.WHITE, true);
        presetLabel.setPadding(0, dp(10), 0, dp(12));
        presetCard.addView(presetLabel);

        LinearLayout presetRow = new LinearLayout(this);
        presetCard.addView(presetRow);

        addButton(presetRow, "‹ PREV",
            () -> changePreset(-1), 1);

        addButton(presetRow, "NEXT ›",
            () -> changePreset(1), 1);

        root.addView(section("PEDALBOARD"));

        for (int i = 0; i < effectNames.length; i++) {
            addEffect(i);
        }

        root.addView(section("AMP & CAB SIM"));

        LinearLayout ampCard = card();
        root.addView(ampCard);

        ampCard.addView(text(
            "Basic amplifier saturation and cabinet-style filtering",
            12, Color.LTGRAY, false
        ));

        ampButton = addButton(
            ampCard, "AMP / CAB SIM: ON",
            this::toggleAmp, 1
        );

        root.addView(section("MASTER VOLUME"));

        LinearLayout volumeCard = card();
        root.addView(volumeCard);

        volumeLabel = text("MASTER: 75%", 15, Color.WHITE, true);
        volumeCard.addView(volumeLabel);

        SeekBar volumeSeek = new SeekBar(this);
        volumeSeek.setMax(100);
        volumeSeek.setProgress(75);
        volumeSeek.setProgressTintList(
            android.content.res.ColorStateList.valueOf(orange)
        );
        volumeSeek.setThumbTintList(
            android.content.res.ColorStateList.valueOf(orange)
        );
        volumeCard.addView(volumeSeek);

        volumeSeek.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar seekBar, int progress, boolean fromUser
                ) {
                    masterVolume = progress / 100.0f;
                    volumeLabel.setText("MASTER: " + progress + "%");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            }
        );

        root.addView(section("AUDIO INPUT / OUTPUT"));

        LinearLayout audioCard = card();
        root.addView(audioCard);

        audioCard.addView(text(
            "Input: microphone HP\n"
            + "Output: Android audio output / headphone\n"
            + "USB audio input belum dipilih secara manual.",
            12, Color.LTGRAY, false
        ));

        audioButton = addButton(
            audioCard, "START MIC FX",
            this::toggleAudio, 1
        );

        statusLabel = text(
            "Status: audio belum berjalan",
            11, Color.LTGRAY, false
        );
        statusLabel.setPadding(0, dp(10), 0, 0);
        audioCard.addView(statusLabel);

        root.addView(section("TOOLS"));

        LinearLayout tools = new LinearLayout(this);
        root.addView(tools);

        addButton(tools, "TUNER",
            () -> toast("Tuner akan dibuat pada tahap berikutnya."),
            1);

        addButton(tools, "LOOPER",
            () -> toast("Looper akan dibuat pada tahap berikutnya."),
            1);

        TextView footer = text(
            "V3 FOUNDATION • BELUM DIUJI PADA PERANGKAT",
            10, Color.GRAY, false
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(22), 0, dp(5));
        root.addView(footer);

        applyPreset(0);
    }

    private void addEffect(int id) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(row);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        labels.addView(text(
            effectNames[id], 15, Color.WHITE, true
        ));

        TextView description = text(
            effectDescription(id), 11, Color.LTGRAY, false
        );
        description.setPadding(0, dp(4), 0, 0);
        labels.addView(description);

        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView toggle = text("OFF", 12, Color.LTGRAY, true);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(14), dp(9), dp(14), dp(9));
        toggle.setBackground(roundBg(Color.rgb(50, 52, 58)));

        row.addView(toggle);
        effectButtons[id] = toggle;

        toggle.setOnClickListener(v -> {
            setEffect(id, !getEffect(id));
            updateEffectButton(id);
        });
    }

    private String effectDescription(int id) {
        switch (id) {
            case 0: return "Noise threshold";
            case 1: return "Soft saturation";
            case 2: return "Hard clipping";
            case 3: return "Modulated delay";
            case 4: return "Feedback echo";
            case 5: return "Simple feedback ambience";
            default: return "";
        }
    }

    private boolean getEffect(int id) {
        switch (id) {
            case 0: return gate;
            case 1: return overdrive;
            case 2: return distortion;
            case 3: return chorus;
            case 4: return delay;
            case 5: return reverb;
            default: return false;
        }
    }

    private void setEffect(int id, boolean value) {
        switch (id) {
            case 0: gate = value; break;
            case 1: overdrive = value; break;
            case 2: distortion = value; break;
            case 3: chorus = value; break;
            case 4: delay = value; break;
            case 5: reverb = value; break;
        }
        updateEffectButton(id);
    }

    private void updateEffectButton(int id) {
        TextView b = effectButtons[id];
        if (b == null) return;

        boolean enabled = getEffect(id);
        b.setText(enabled ? "ON" : "OFF");
        b.setTextColor(enabled ? Color.BLACK : Color.LTGRAY);
        b.setBackground(roundBg(
            enabled ? orange : Color.rgb(50, 52, 58)
        ));
    }

    private void toggleAmp() {
        ampEnabled = !ampEnabled;
        ampButton.setText(
            ampEnabled ? "AMP / CAB SIM: ON" : "AMP / CAB SIM: OFF"
        );
        ampButton.setTextColor(
            ampEnabled ? Color.BLACK : Color.WHITE
        );
        ampButton.setBackground(roundBg(
            ampEnabled ? orange : Color.rgb(54, 56, 63)
        ));
    }

    private void changePreset(int delta) {
        presetIndex = (presetIndex + delta + presets.length)
            % presets.length;
        applyPreset(presetIndex);
    }

    private void applyPreset(int index) {
        // Setiap indeks memakai konfigurasi pedalboard tersendiri.
        // Efek aktif disimpan sebagai bit dalam mask preset.
        int mask = index;

        gate = (mask & 1) != 0;
        overdrive = (mask & 2) != 0;
        distortion = (mask & 4) != 0;
        chorus = (mask & 8) != 0;
        delay = (mask & 16) != 0;
        reverb = (mask & 32) != 0;

        // Variasi parameter amplifier dan filter cabinet.
        ampGain = 2.0f + (index % 10) * 0.25f;
        cabCutoff = 0.06f + (index % 7) * 0.02f;

        presetLabel.setText(
            presets[index] + "  (" + (index + 1) + "/50)"
        );

        for (int i = 0; i < effectButtons.length; i++) {
            updateEffectButton(i);
        }
    }

    private void toggleAudio() {
        if (audioRunning) {
            audioRunning = false;
            audioButton.setText("START MIC FX");
            statusLabel.setText("Status: menghentikan audio...");
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 23
            && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO}, 1001
            );
            return;
        }

        audioRunning = true;
        audioButton.setText("STOP MIC FX");
        statusLabel.setText("Status: memulai audio...");

        audioThread = new Thread(this::runAudio, "StradaAudio");
        audioThread.start();
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] results
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, results
        );

        if (requestCode == 1001) {
            if (results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
                toggleAudio();
            } else {
                toast("Izin mikrofon diperlukan.");
            }
        }
    }

    private void runAudio() {
        final int rate = 44100;
        final int frames = 512;

        int inMin = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );

        int outMin = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );

        AudioRecord recorder = null;
        AudioTrack track = null;

        try {
            if (inMin <= 0 || outMin <= 0) {
                throw new IllegalStateException(
                    "Format audio tidak didukung."
                );
            }

            recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(inMin, frames * 4)
            );

            track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(outMin, frames * 4),
                AudioTrack.MODE_STREAM
            );

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED
                || track.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException(
                    "Perangkat audio gagal diinisialisasi."
                );
            }

            short[] input = new short[frames];
            short[] output = new short[frames];

            float[] chorusBuffer = new float[1800];
            float[] delayBuffer = new float[rate / 3];
            float[] reverbBuffer = new float[9000];

            int chorusPos = 0;
            int delayPos = 0;
            int reverbPos = 0;
            double lfo = 0;

            float cabState = 0;

            track.play();
            recorder.startRecording();

            runOnUiThread(() ->
                statusLabel.setText("Status: audio berjalan")
            );

            while (audioRunning) {
                int count = recorder.read(input, 0, frames);

                if (count < 0) {
                    throw new IllegalStateException(
                        "Gagal membaca mikrofon: " + count
                    );
                }

                for (int i = 0; i < count; i++) {
                    float x = input[i] / 32768.0f;

                    if (gate && Math.abs(x) < 0.025f) {
                        x = 0;
                    }

                    if (overdrive) {
                        x = (float) Math.tanh(x * 4.0f);
                    }

                    if (distortion) {
                        x = Math.max(-0.75f,
                            Math.min(0.75f, x * 5.0f));
                    }

                    if (chorus) {
                        lfo += 2.0 * Math.PI * 0.35 / rate;
                        if (lfo > 2.0 * Math.PI) lfo -= 2.0 * Math.PI;

                        int d = 350 + (int)(
                            180 * (1 + Math.sin(lfo))
                        );

                        int read = (chorusPos - d
                            + chorusBuffer.length)
                            % chorusBuffer.length;

                        float delayed = chorusBuffer[read];
                        chorusBuffer[chorusPos] = x;
                        x += delayed * 0.30f;

                        chorusPos = (chorusPos + 1)
                            % chorusBuffer.length;
                    }

                    if (delay) {
                        float echo = delayBuffer[delayPos];
                        delayBuffer[delayPos] = x + echo * 0.35f;
                        x += echo * 0.35f;
                        delayPos = (delayPos + 1)
                            % delayBuffer.length;
                    }

                    if (reverb) {
                        float room = reverbBuffer[reverbPos];
                        reverbBuffer[reverbPos] =
                            x + room * 0.40f;
                        x += room * 0.20f;
                        reverbPos = (reverbPos + 1)
                            % reverbBuffer.length;
                    }

                    if (ampEnabled) {
                        x = (float) Math.tanh(x * ampGain);

                        // Filter low-pass sederhana sebagai simulasi
                        // karakter cabinet, bukan model speaker akurat.
                        cabState += cabCutoff * (x - cabState);
                        x = cabState;
                    }

                    x *= masterVolume;
                    x = Math.max(-0.95f, Math.min(0.95f, x));

                    output[i] = (short) (x * 32767.0f);
                }

                if (count > 0) {
                    int written = track.write(output, 0, count);
                    if (written < 0) {
                        throw new IllegalStateException(
                            "Gagal mengirim audio: " + written
                        );
                    }
                }
            }
        } catch (Exception e) {
            runOnUiThread(() -> {
                toast("Audio error: " + e.getMessage());
                statusLabel.setText("Status: audio error");
            });
        } finally {
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (Exception ignored) {}
                try {
                    recorder.release();
                } catch (Exception ignored) {}
            }

            if (track != null) {
                try {
                    track.pause();
                    track.flush();
                } catch (Exception ignored) {}
                try {
                    track.release();
                } catch (Exception ignored) {}
            }

            audioRunning = false;

            runOnUiThread(() -> {
                if (audioButton != null) {
                    audioButton.setText("START MIC FX");
                }
                if (statusLabel != null) {
                    statusLabel.setText("Status: audio berhenti");
                }
            });
        }
    }
    
private TextView section(String title) {
    TextView t = new TextView(this);
    t.setText(title);
    t.setTextSize(13);
    t.setTextColor(orange);
    t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    t.setPadding(
        dp(2),
        dp(18),
        dp(2),
        dp(10)
    );
    t.setLetterSpacing(0.08f);
    return t;
}
    
    private LinearLayout card() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(15), dp(15), dp(15), dp(15));
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
        b.setBackground(roundBg(Color.rgb(54, 56, 63)));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(0, -2, weight);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));

        parent.addView(b, p);
        b.setOnClickListener(v -> action.run());

        return b;
    }

    private TextView text(
        String value, int size, int color, boolean bold
    ) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);

        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return t;
    }

    private GradientDrawable roundBg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        return d;
    }

    private void toast(String message) {
        Toast.makeText(
            this, message, Toast.LENGTH_SHORT
        ).show();
    }

    private int dp(int value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    @Override
    protected void onDestroy() {
        audioRunning = false;
        super.onDestroy();
    }
}
