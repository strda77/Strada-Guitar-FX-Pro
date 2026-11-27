
package com.strada.guitarfXpro;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioEngine {

    public static final int RATE = 44100;
    public static final int BLOCK = 256;
    public static final int EFFECTS = 8;

    private static final int LOOP_SECONDS = 30;
    private static final int LOOP_IDLE = 0;
    private static final int LOOP_RECORDING = 1;
    private static final int LOOP_PLAYING = 2;

    private static final int CMD_NONE = 0;
    private static final int CMD_RECORD = 1;
    private static final int CMD_STOP_RECORD = 2;
    private static final int CMD_PLAY = 3;
    private static final int CMD_CLEAR = 4;

    public interface Listener {
        void onStatus(String message);
        void onPitch(float frequencyHz);
        void onLooperChanged(boolean recording, boolean playing, int samples);
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    public static final class Settings {

        public final boolean[] enabled = new boolean[EFFECTS];
        public final int[][] params = new int[EFFECTS][3];

        public final int[] effectOrder = {
                0, 1, 2, 3, 4, 5, 6, 7
        };

        public float master = 0.72f;
        public float gain = 3.0f;
        public float bass = 0.5f;
        public float middle = 0.5f;
        public float treble = 0.6f;
        public float ampVolume = 0.7f;

        public boolean ampOn = true;
        public boolean cabOn = true;

        public int ampModel = 0;
        public int cabModel = 5;

        public String input = "Microphone (HP)";
        public String output = "System / Headphone";

        public Settings copy() {
            Settings result = new Settings();

            System.arraycopy(enabled, 0, result.enabled, 0, EFFECTS);
            System.arraycopy(effectOrder, 0, result.effectOrder, 0, EFFECTS);

            for (int i = 0; i < EFFECTS; i++) {
                System.arraycopy(params[i], 0, result.params[i], 0, 3);
            }

            result.master = master;
            result.gain = gain;
            result.bass = bass;
            result.middle = middle;
            result.treble = treble;
            result.ampVolume = ampVolume;

            result.ampOn = ampOn;
            result.cabOn = cabOn;

            result.ampModel = ampModel;
            result.cabModel = cabModel;

            result.input = input;
            result.output = output;

            return result;
        }
    }

    // =========================================================
    // AUDIO STATE
    // =========================================================

    private final Context context;
    private final Listener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean released = new AtomicBoolean(false);

    private volatile Settings settings = new Settings();

    private volatile AudioRecord recorder;
    private volatile AudioTrack track;
    private volatile Thread audioThread;

    private final short[] loopBuffer = new short[RATE * LOOP_SECONDS];

    private volatile boolean loopRecording;
    private volatile boolean loopPlaying;

    private volatile int loopLength;
    private int loopWritePosition;
    private int loopReadPosition;

    private final AtomicInteger loopCommand = new AtomicInteger(CMD_NONE);

    // =========================================================
    // DSP MEMORY
    // =========================================================

    private final float[] delayBuffer = new float[RATE * 2];

    private final float[] reverbBufferA = new float[4093];
    private final float[] reverbBufferB = new float[3251];
    private final float[] reverbBufferC = new float[2719];

    private final float[] chorusBuffer = new float[RATE / 5];

    private int delayPosition;
    private int reverbPositionA;
    private int reverbPositionB;
    private int reverbPositionC;
    private int chorusPosition;

    private double chorusPhase;

    private float gateEnvelope;
    private float compressorEnvelope;

    private float ampLow;
    private float ampMid;

    private float cabLow;

    private float dcInput;
    private float dcOutput;

    private float limiterEnvelope;

    private int statusCounter;

    private final PitchDetector pitchDetector = new PitchDetector();

    public AudioEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    // =========================================================
    // SETTINGS CONTROL
    // =========================================================

    public void updateSettings(Settings value) {
        if (value == null || released.get()) {
            return;
        }

        settings = value.copy();
    }

    public synchronized boolean setEffectOrder(int[] order) {

        if (order == null || order.length != EFFECTS) {
            return false;
        }

        boolean[] used = new boolean[EFFECTS];

        for (int id : order) {
            if (id < 0 || id >= EFFECTS || used[id]) {
                return false;
            }
            used[id] = true;
        }

        Settings updated = settings.copy();

        System.arraycopy(order, 0, updated.effectOrder, 0, EFFECTS);

        settings = updated;

        return true;
    }

    public int[] getEffectOrder() {
        return settings.copy().effectOrder.clone();
    }

    public boolean isRunning() {
        return running.get();
    }

    // =========================================================
    // AUDIO START / STOP
    // =========================================================

    public synchronized void start() {

        if (released.get()) {
            status("AudioEngine sudah dilepas.");
            return;
        }

        if (running.get()) {
            status("Audio sudah berjalan.");
            return;
        }

        if (audioThread != null && audioThread.isAlive()) {
            status("Audio sedang menyelesaikan proses berhenti.");
            return;
        }

        running.set(true);

        audioThread = new Thread(
                this::runAudio,
                "StradaAudioEngine"
        );

        audioThread.start();
    }

    public void stop() {

        if (!running.getAndSet(false)) {
            return;
        }

        AudioRecord current = recorder;

        if (current != null) {
            try {
                if (current.getRecordingState()
                        == AudioRecord.RECORDSTATE_RECORDING) {
                    current.stop();
                }
            } catch (Exception ignored) {
            }
        }

        status("Menghentikan audio...");
    }

    public synchronized void release() {

        if (!released.compareAndSet(false, true)) {
            return;
        }

        running.set(false);

        AudioRecord current = recorder;

        if (current != null) {
            try {
                if (current.getRecordingState()
                        == AudioRecord.RECORDSTATE_RECORDING) {
                    current.stop();
                }
            } catch (Exception ignored) {
            }
        }

        Thread worker = audioThread;

        if (worker != null && worker != Thread.currentThread()) {
            try {
                worker.join(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        pitchDetector.shutdown();
    }

    // =========================================================
    // LOOPER COMMANDS
    // Commands are applied by the audio thread.
    // =========================================================

    public void startLoopRecording() {
    if (!running.get()) {
        status("Jalankan audio sebelum merekam loop.");
        return;
    }
    loopCommand.set(CMD_RECORD);
}

    public void stopLoopRecording() {
    if (loopRecording) {
        loopCommand.set(CMD_STOP_RECORD);
    }
}

    public void toggleLoopPlayback() {
    if (!running.get()) {
        status("Jalankan audio sebelum memutar loop.");
        return;
    }
    loopCommand.set(CMD_PLAY);
}

    public void clearLoop() {
    loopCommand.set(CMD_CLEAR);
}  

    public boolean isLoopRecording() {
        return loopRecording;
    }

    public boolean isLoopPlaying() {
        return loopPlaying;
    }

    public int getLoopLengthSamples() {
        return loopLength;
    }

    private void applyLoopCommand() {

    int command = loopCommand.getAndSet(CMD_NONE);

    // Jangan kirim callback jika tidak ada perintah baru.
    if (command == CMD_NONE) {
        return;
    }

    switch (command) {

        case CMD_RECORD:
            if (running.get()) {
                loopPlaying = false;
                loopRecording = true;
                loopLength = 0;
                loopWritePosition = 0;
                loopReadPosition = 0;
            }
            break;

        case CMD_STOP_RECORD:
            if (loopRecording) {
                loopRecording = false;
                loopLength = loopWritePosition;
                loopReadPosition = 0;
            }
            break;

        case CMD_PLAY:
            if (loopPlaying) {
                loopPlaying = false;
            } else if (running.get() && loopLength > 0) {
                loopReadPosition = 0;
                loopPlaying = true;
            } else {
                status("Belum ada loop tersimpan.");
            }
            break;

        case CMD_CLEAR:
            loopRecording = false;
            loopPlaying = false;
            loopLength = 0;
            loopWritePosition = 0;
            loopReadPosition = 0;
            break;

        default:
            return;
    }

    notifyLooper();
    }

    // =========================================================
    // AUDIO THREAD
    // =========================================================

    private void runAudio() {

        String error = null;

        AudioRecord localRecorder = null;
        AudioTrack localTrack = null;

        try {

            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

            int inputMin = AudioRecord.getMinBufferSize(
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            int outputMin = AudioTrack.getMinBufferSize(
                    RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            if (inputMin <= 0 || outputMin <= 0) {
                throw new IllegalStateException(
                        "Format PCM 44.1 kHz tidak didukung."
                );
            }

            int bytes = Math.max(
                    Math.max(inputMin, outputMin),
                    BLOCK * 8
            );

            localRecorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bytes
            );

            localTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bytes,
                    AudioTrack.MODE_STREAM
            );

            recorder = localRecorder;
            track = localTrack;

            if (localRecorder.getState()
                    != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException(
                        "AudioRecord gagal diinisialisasi."
                );
            }

            if (localTrack.getState()
                    != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException(
                        "AudioTrack gagal diinisialisasi."
                );
            }

            Settings initialSettings = settings;

            applyRouting(initialSettings, localRecorder, localTrack);

            localTrack.play();
            localRecorder.startRecording();

            if (localRecorder.getRecordingState()
                    != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException(
                        "Input audio gagal dimulai."
                );
            }

            status("AUDIO RUNNING • 44.1 kHz");

            short[] input = new short[BLOCK];
            short[] output = new short[BLOCK];

            while (running.get()) {

                int count = localRecorder.read(
                        input,
                        0,
                        input.length,
                        AudioRecord.READ_BLOCKING
                );

                if (count == AudioRecord.ERROR_DEAD_OBJECT) {
                    throw new IllegalStateException(
                            "Perangkat audio terputus."
                    );
                }

                if (count < 0) {
                    if (running.get()) {
                        throw new IllegalStateException(
                                "Input audio error: " + count
                        );
                    }
                    break;
                }

                if (count == 0) {
                    continue;
                }

                // Settings is an immutable snapshot.
                Settings current = settings;

                processBlock(input, output, count, current);

                // Tuner calculation runs outside the DSP path.
                pitchDetector.add(input, count);

                int offset = 0;

                while (offset < count && running.get()) {

                    int written = localTrack.write(
                            output,
                            offset,
                            count - offset,
                            AudioTrack.WRITE_BLOCKING
                    );

                    if (written < 0) {
                        throw new IllegalStateException(
                                "Output audio error: " + written
                        );
                    }

                    if (written == 0) {
                        break;
                    }

                    offset += written;
                }

                statusCounter++;

                if (statusCounter >= 400) {
                    statusCounter = 0;
                    notifyLooper();
                }
            }

        } catch (Exception e) {

            if (running.get()) {
                error = e.getMessage();

                if (error == null || error.length() == 0) {
                    error = e.getClass().getSimpleName();
                }
            }

        } finally {

            running.set(false);

            loopRecording = false;
            loopPlaying = false;

            if (localRecorder != null) {
                try {
                    if (localRecorder.getRecordingState()
                            == AudioRecord.RECORDSTATE_RECORDING) {
                        localRecorder.stop();
                    }
                } catch (Exception ignored) {
                }

                try {
                    localRecorder.release();
                } catch (Exception ignored) {
                }
            }

            if (localTrack != null) {
                try {
                    if (localTrack.getPlayState()
                            == AudioTrack.PLAYSTATE_PLAYING) {
                        localTrack.stop();
                    }
                } catch (Exception ignored) {
                }

                try {
                    localTrack.release();
                } catch (Exception ignored) {
                }
            }

            recorder = null;
            track = null;

            notifyLooper();

            status(error == null
                    ? "Audio berhenti."
                    : "Audio error: " + error);

            synchronized (this) {
                audioThread = null;
            }
        }
    }

    // =========================================================
    // INPUT / OUTPUT ROUTING
    // =========================================================

    private void applyRouting(
            Settings value,
            AudioRecord input,
            AudioTrack output) {

        if (Build.VERSION.SDK_INT < 23) {
            if (!"Microphone (HP)".equals(value.input)
                    || !"System / Headphone".equals(value.output)) {
                throw new IllegalStateException(
                        "Routing perangkat membutuhkan Android 6.0+."
                );
            }
            return;
        }

        AudioManager manager =
                (AudioManager) context.getSystemService(
                        Context.AUDIO_SERVICE
                );

        if (manager == null) {
            throw new IllegalStateException(
                    "AudioManager tidak tersedia."
            );
        }

        AudioDeviceInfo inputDevice = findDevice(
                manager.getDevices(AudioManager.GET_DEVICES_INPUTS),
                value.input,
                true
        );

        if (inputDevice == null
                && !"Microphone (HP)".equals(value.input)) {
            throw new IllegalStateException(
                    "Input tidak ditemukan: " + value.input
            );
        }

        if (inputDevice != null
                && !input.setPreferredDevice(inputDevice)) {
            throw new IllegalStateException(
                    "Routing input ditolak Android."
            );
        }

        if (!"System / Headphone".equals(value.output)) {

            AudioDeviceInfo outputDevice = findDevice(
                    manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS),
                    value.output,
                    false
            );

            if (outputDevice == null) {
                throw new IllegalStateException(
                        "Output tidak ditemukan: " + value.output
                );
            }

            if (!output.setPreferredDevice(outputDevice)) {
                throw new IllegalStateException(
                        "Routing output ditolak Android."
                );
            }
        }
    }

    private AudioDeviceInfo findDevice(
        AudioDeviceInfo[] devices,
        String wanted,
        boolean input) {

    if (devices == null || wanted == null) {
        return null;
    }

    for (AudioDeviceInfo device : devices) {

        if (device == null) {
            continue;
        }

        if (input && !device.isSource()) {
            continue;
        }

        if (!input && !device.isSink()) {
            continue;
        }

        int type = device.getType();

        if (input) {

            if ("Microphone (HP)".equals(wanted)
                    && type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                return device;
            }

            if ("Wired Headset Mic".equals(wanted)
                    && type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                return device;
            }

            if ("USB Audio Interface".equals(wanted)
                    && isUsbType(type)) {
                return device;
            }

        } else {

            if ("Speaker".equals(wanted)
                    && type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                return device;
            }

            if ("USB Audio Output".equals(wanted)
                    && isUsbType(type)) {
                return device;
            }
        }
    }

    return null;
    }

    private boolean isUsbType(int type) {

        if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= 26) {
            return type == AudioDeviceInfo.TYPE_USB_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_ACCESSORY;
        }

        return false;
    }

    // =========================================================
    // DSP PROCESSOR
    // =========================================================

    private void processBlock(
            short[] input,
            short[] output,
            int count,
            Settings s) {

        applyLoopCommand();

        for (int i = 0; i < count; i++) {

            float x = input[i] / 32768.0f;

            // DC blocker
            float dc = x - dcInput + 0.995f * dcOutput;

            dcInput = x;
            dcOutput = dc;

            x = dc;

            // Effect chain
            for (int position = 0; position < EFFECTS; position++) {

                int effect = s.effectOrder[position];

                if (effect < 0 || effect >= EFFECTS) {
                    continue;
                }

                if (!s.enabled[effect]) {
                    advanceBypass(effect, x);
                    continue;
                }

                x = processEffect(effect, x, s);
            }

            if (s.ampOn) {
                x = amplifier(x, s);
            }

            if (s.cabOn) {
                x = cabinet(x, s);
            }

            if (loopRecording) {
                recordLoop(x);
            }

            if (loopPlaying && loopLength > 0) {
                x = x * 0.70f + readLoop() * 0.70f;
            }

            x = limiter(x);

            x *= clamp(s.master, 0f, 1f);

            output[i] = (short) (
                    clamp(x, -0.98f, 0.98f) * 32767f
            );
        }
    }

    private float processEffect(int effect, float x, Settings s) {

        switch (effect) {
            case 0: return noiseGate(x, s);
            case 1: return compressor(x, s);
            case 2: return overdrive(x, s);
            case 3: return distortion(x, s);
            case 4: return fuzz(x, s);
            case 5: return modulation(x, s);
            case 6: return delayEffect(x, s);
            case 7: return reverbEffect(x, s);
            default: return x;
        }
    }

    // =========================================================
    // EFFECT 0 - NOISE GATE
    // =========================================================

    private float noiseGate(float x, Settings s) {

        float level = Math.abs(x);

        float attack = 0.25f;
        float release = 0.003f + param(s, 0, 1) * 0.0008f;

        float coefficient = level > gateEnvelope
                ? attack : release;

        gateEnvelope += coefficient * (level - gateEnvelope);

        float threshold = 0.002f + param(s, 0, 0) * 0.0012f;
        float floor = threshold * 0.65f;

        if (gateEnvelope < floor) {
            return x * 0.02f;
        }

        if (gateEnvelope < threshold) {
            float gain = (gateEnvelope - floor)
                    / Math.max(0.0001f, threshold - floor);

            return x * gain;
        }

        return x;
    }

    // =========================================================
    // EFFECT 1 - COMPRESSOR
    // =========================================================

    private float compressor(float x, Settings s) {

        float level = Math.abs(x);

        float coefficient = level > compressorEnvelope
                ? 0.18f : 0.008f;

        compressorEnvelope += coefficient
                * (level - compressorEnvelope);

        float threshold = Math.max(
                0.08f,
                0.42f - param(s, 1, 0) * 0.003f
        );

        float ratio = 1.0f + param(s, 1, 0) * 0.08f;

        float gain = 1.0f;

        if (compressorEnvelope > threshold) {

            float compressed = threshold
                    + (compressorEnvelope - threshold) / ratio;

            gain = compressed
                    / Math.max(0.0001f, compressorEnvelope);
        }

        float makeup = 0.75f + param(s, 1, 1) * 0.008f;

        return x * gain * makeup;
    }

    // =========================================================
    // EFFECT 2 - OVERDRIVE
    // =========================================================

    private float overdrive(float x, Settings s) {

        float drive = 1.0f + param(s, 2, 0) * 0.12f;
        float tone = param(s, 2, 1) / 100.0f;

        float shaped = (float) Math.tanh(x * drive);

        float result = shaped * (0.7f + tone * 0.3f);

        return result * (0.65f + param(s, 2, 2) * 0.006f);
    }

    // =========================================================
    // EFFECT 3 - DISTORTION
    // =========================================================

    private float distortion(float x, Settings s) {

        float drive = 1.0f + param(s, 3, 0) * 0.22f;
        float tone = param(s, 3, 1) / 100.0f;

        float shaped = (float) Math.tanh(x * drive);

        float asymmetry = shaped >= 0
                ? shaped * 0.96f
                : shaped * 1.04f;

        return asymmetry
                * (0.55f + tone * 0.25f)
                * (0.7f + param(s, 3, 2) * 0.006f);
    }

    // =========================================================
    // EFFECT 4 - FUZZ
    // =========================================================

    private float fuzz(float x, Settings s) {

        float drive = 2.0f + param(s, 4, 0) * 0.28f;

        float shaped = (float) Math.tanh(x * drive);

        float quantized = Math.round(shaped * 18f) / 18f;

        float mix = 0.25f + param(s, 4, 1) * 0.006f;

        float result = shaped * (1f - mix) + quantized * mix;

        return result * (0.65f + param(s, 4, 2) * 0.006f);
    }

    // =========================================================
    // EFFECT 5 - CHORUS
    // =========================================================

    private float modulation(float x, Settings s) {

        chorusBuffer[chorusPosition] = x;

        float rate = 0.15f + param(s, 5, 0) * 0.045f;
        float depth = param(s, 5, 1) / 100f;
        float mix = param(s, 5, 2) / 100f;

        chorusPhase += (2.0 * Math.PI * rate) / RATE;

        if (chorusPhase >= 2.0 * Math.PI) {
            chorusPhase -= 2.0 * Math.PI;
        }

        float lfo = (float) Math.sin(chorusPhase);

        int distance = (int) (
                RATE * 0.012f + lfo * depth * RATE * 0.006f
        );

        distance = Math.max(
                1,
                Math.min(chorusBuffer.length - 1, distance)
        );

        int readPosition = (
                chorusPosition - distance + chorusBuffer.length
        ) % chorusBuffer.length;

        float wet = chorusBuffer[readPosition];

        chorusPosition = (chorusPosition + 1) % chorusBuffer.length;

        return x * (1f - mix * 0.5f) + wet * mix * 0.8f;
    }

    // =========================================================
    // EFFECT 6 - DELAY
    // =========================================================

    private float delayEffect(float x, Settings s) {

        int time = (int) (
                (0.04f + param(s, 6, 0) * 0.0045f) * RATE
        );

        time = Math.max(1, Math.min(delayBuffer.length - 1, time));

        int readPosition = (
                delayPosition - time + delayBuffer.length
        ) % delayBuffer.length;

        float echo = delayBuffer[readPosition];

        float feedback = Math.min(0.82f, param(s, 6, 1) / 100f);
        float mix = param(s, 6, 2) / 100f;

        delayBuffer[delayPosition] = clamp(
                x + echo * feedback,
                -1f,
                1f
        );

        delayPosition = (delayPosition + 1) % delayBuffer.length;

        return x * (1f - mix * 0.75f) + echo * mix;
    }

    // =========================================================
    // EFFECT 7 - REVERB
    // =========================================================

    private float reverbEffect(float x, Settings s) {

        float decay = param(s, 7, 0) / 100f;
        float tone = param(s, 7, 1) / 100f;
        float mix = param(s, 7, 2) / 100f;

        float a = reverbBufferA[reverbPositionA];
        float b = reverbBufferB[reverbPositionB];
        float c = reverbBufferC[reverbPositionC];

        float reflection = (a + b + c) / 3f;
        float feedback = 0.35f + decay * 0.48f;

        reverbBufferA[reverbPositionA] =
                clamp(x + reflection * feedback, -1f, 1f);

        reverbBufferB[reverbPositionB] =
                clamp(x * 0.72f + reflection * feedback, -1f, 1f);

        reverbBufferC[reverbPositionC] =
                clamp(x * 0.54f + reflection * feedback, -1f, 1f);

        reverbPositionA = (reverbPositionA + 1) % reverbBufferA.length;
        reverbPositionB = (reverbPositionB + 1) % reverbBufferB.length;
        reverbPositionC = (reverbPositionC + 1) % reverbBufferC.length;

        float wet = reflection * (0.65f + tone * 0.25f);

        return x * (1f - mix * 0.7f) + wet * mix;
    }

    // =========================================================
    // BYPASS BUFFER MAINTENANCE
    // =========================================================

    private void advanceBypass(int effect, float x) {

        switch (effect) {

            case 5:
                chorusBuffer[chorusPosition] = x;
                chorusPosition = (chorusPosition + 1) % chorusBuffer.length;
                break;

            case 6:
                delayBuffer[delayPosition] = x;
                delayPosition = (delayPosition + 1) % delayBuffer.length;
                break;

            case 7:
                reverbBufferA[reverbPositionA] = x;
                reverbBufferB[reverbPositionB] = x;
                reverbBufferC[reverbPositionC] = x;

                reverbPositionA = (reverbPositionA + 1) % reverbBufferA.length;
                reverbPositionB = (reverbPositionB + 1) % reverbBufferB.length;
                reverbPositionC = (reverbPositionC + 1) % reverbBufferC.length;
                break;
        }
    }

    // =========================================================
    // AMPLIFIER MODELING
    // =========================================================

    private float amplifier(float x, Settings s) {

        float gain = Math.max(0.1f, s.gain);
        float driven = x * gain * ampPreGain(s.ampModel);

        float saturated = (float) Math.tanh(driven);

        ampLow += 0.025f * (saturated - ampLow);
        ampMid += 0.13f * (saturated - ampMid);

        float lowBand = ampLow;
        float midBand = ampMid - ampLow;
        float highBand = saturated - ampMid;

        float bass = clamp(s.bass, 0f, 1f);
        float middle = clamp(s.middle, 0f, 1f);
        float treble = clamp(s.treble, 0f, 1f);

        float tone =
                lowBand * (0.35f + bass * 1.25f)
                + midBand * (0.35f + middle * 1.25f)
                + highBand * (0.25f + treble * 1.35f);

        return tone
                * ampModelLevel(s.ampModel)
                * clamp(s.ampVolume, 0f, 1.5f);
    }

    private float ampPreGain(int model) {

        switch (model) {
            case 0: return 0.65f;
            case 1: return 0.75f;
            case 2: return 0.90f;
            case 3: return 0.95f;
            case 4: return 1.05f;
            case 5: return 1.15f;
            case 6: return 1.25f;
            case 7: return 1.45f;
            case 8: return 1.70f;
            case 9: return 0.62f;
            case 10: return 1.30f;
            case 11: return 1.50f;
            default: return 1.0f;
        }
    }

    private float ampModelLevel(int model) {

        switch (model) {
            case 0: return 0.95f;
            case 1: return 0.92f;
            case 2: return 0.90f;
            case 3: return 0.90f;
            case 4: return 0.88f;
            case 5: return 0.88f;
            case 6: return 0.85f;
            case 7: return 0.82f;
            case 8: return 0.78f;
            case 9: return 0.96f;
            case 10: return 0.84f;
            case 11: return 0.82f;
            default: return 0.90f;
        }
    }

    // =========================================================
    // CABINET FILTERING
    // This is not an IR convolution engine yet.
    // =========================================================

    private float cabinet(float x, Settings s) {

        float cutoff = cabCutoff(s.cabModel);

        cabLow += cutoff * (x - cabLow);

        float cabHigh = x - cabLow;

        float highAmount;

        switch (s.cabModel) {
            case 0: highAmount = 0.10f; break;
            case 1: highAmount = 0.13f; break;
            case 2: highAmount = 0.16f; break;
            case 3: highAmount = 0.18f; break;
            case 4: highAmount = 0.12f; break;
            case 5: highAmount = 0.20f; break;
            case 6: highAmount = 0.22f; break;
            case 7: highAmount = 0.19f; break;
            default: highAmount = 0.16f; break;
        }

        return cabLow * 0.85f + cabHigh * highAmount;
    }

    private float cabCutoff(int model) {

        switch (model) {
            case 0: return 0.035f;
            case 1: return 0.045f;
            case 2: return 0.060f;
            case 3: return 0.075f;
            case 4: return 0.095f;
            case 5: return 0.085f;
            case 6: return 0.105f;
            case 7: return 0.080f;
            case 8: return 0.070f;
            default: return 0.080f;
        }
    }

    // =========================================================
    // OUTPUT LIMITER
    // =========================================================

    private float limiter(float x) {

        float level = Math.abs(x);

        limiterEnvelope +=
                (level > limiterEnvelope ? 0.25f : 0.015f)
                * (level - limiterEnvelope);

        if (limiterEnvelope > 0.88f) {
            x *= 0.88f / Math.max(0.0001f, limiterEnvelope);
        }

        return (float) Math.tanh(x * 1.05f);
    }

    // =========================================================
    // LOOPER PROCESSING
    // =========================================================

    private void recordLoop(float sample) {

        int position = loopWritePosition;

        if (position >= loopBuffer.length) {
            loopRecording = false;
            loopLength = loopBuffer.length;
            notifyLooper();
            return;
        }

        loopBuffer[position] = (short) (
                clamp(sample, -0.98f, 0.98f) * 32767f
        );

        loopWritePosition = position + 1;

        if (loopWritePosition >= loopBuffer.length) {
            loopRecording = false;
            loopLength = loopBuffer.length;
            notifyLooper();
        }
    }

    private float readLoop() {

        int length = loopLength;

        if (length <= 0) {
            return 0f;
        }

        int position = loopReadPosition;

        if (position >= length) {
            position = 0;
        }

        float sample = loopBuffer[position] / 32768f;

        loopReadPosition = (position + 1) % length;

        return sample;
    }

    // =========================================================
    // TUNER WORKER
    // Pitch analysis is moved away from the audio processing loop.
    // =========================================================


    private final class PitchDetector {

    private final Object lock = new Object();

    private final float[] ring = new float[2048];
    private final float[] frame = new float[2048];

    private int position;
    private int filled;
    private int samplesSinceCheck;

    private boolean frameReady;
    private boolean active = true;

    private final Thread worker;

    PitchDetector() {
        worker = new Thread(
                this::run,
                "StradaPitchDetector"
        );

        worker.setPriority(Thread.NORM_PRIORITY);
        worker.start();
    }

    void add(short[] input, int count) {

        if (input == null || count <= 0) {
            return;
        }

        int length = Math.min(count, input.length);

        for (int i = 0; i < length; i++) {

            ring[position] = input[i] / 32768f;

            position++;
            if (position >= ring.length) {
                position = 0;
            }

            if (filled < ring.length) {
                filled++;
            }

            samplesSinceCheck++;
        }

        if (filled < ring.length ||
                samplesSinceCheck < 2048) {
            return;
        }

        samplesSinceCheck = 0;

        synchronized (lock) {

            if (!active || frameReady) {
                return;
            }

            for (int i = 0; i < frame.length; i++) {

                int index = position + i;

                if (index >= ring.length) {
                    index -= ring.length;
                }

                frame[i] = ring[index];
            }

            frameReady = true;
            lock.notifyAll();
        }
    }

    private void run() {

        float[] work = new float[2048];

        while (true) {

            synchronized (lock) {

                while (active && !frameReady) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        if (!active) {
                            return;
                        }
                    }
                }

                if (!active) {
                    return;
                }

                System.arraycopy(
                        frame,
                        0,
                        work,
                        0,
                        frame.length
                );

                frameReady = false;
            }

            detect(work);
        }
    }

    private void detect(float[] samples) {

        float mean = 0f;

        for (float sample : samples) {
            mean += sample;
        }

        mean /= samples.length;

        float bestScore = 0f;
        int bestLag = -1;

        int minLag = RATE / 1200;
        int maxLag = RATE / 55;

        for (int lag = minLag; lag <= maxLag; lag++) {

            double correlation = 0;
            double energyA = 0;
            double energyB = 0;

            for (int j = 0;
                 j < samples.length - lag;
                 j += 2) {

                float a = samples[j] - mean;
                float b = samples[j + lag] - mean;

                correlation += a * b;
                energyA += a * a;
                energyB += b * b;
            }

            if (energyA > 0.0000001 &&
                    energyB > 0.0000001) {

                float score = (float) (
                        correlation /
                        Math.sqrt(energyA * energyB)
                );

                if (score > bestScore) {
                    bestScore = score;
                    bestLag = lag;
                }
            }
        }

        if (bestLag > 0 && bestScore > 0.48f) {

            float frequency = RATE / (float) bestLag;

            if (frequency >= 50f &&
                    frequency <= 1200f &&
                    listener != null) {

                listener.onPitch(frequency);
            }
        }
    }

    void shutdown() {

        synchronized (lock) {
            active = false;
            lock.notifyAll();
        }

        worker.interrupt();
    }
        }

        private void detect(float[] samples) {

            float mean = 0f;

            for (float sample : samples) {
                mean += sample;
            }

            mean /= samples.length;

            float bestScore = 0f;
            int bestLag = -1;

            int minLag = RATE / 1200;
            int maxLag = RATE / 55;

            for (int lag = minLag; lag <= maxLag; lag++) {

                double correlation = 0;
                double energyA = 0;
                double energyB = 0;

                for (int j = 0; j < samples.length - lag; j += 2) {

                    float a = samples[j] - mean;
                    float b = samples[j + lag] - mean;

                    correlation += a * b;
                    energyA += a * a;
                    energyB += b * b;
                }

                if (energyA > 0.0000001 && energyB > 0.0000001) {

                    float score = (float) (
                            correlation / Math.sqrt(energyA * energyB)
                    );

                    if (score > bestScore) {
                        bestScore = score;
                        bestLag = lag;
                    }
                }
            }

            if (bestLag > 0 && bestScore > 0.48f) {

                float frequency = RATE / (float) bestLag;

                if (frequency >= 50f && frequency <= 1200f
                        && listener != null) {
                    listener.onPitch(frequency);
                }
            }
        }

        void shutdown() {

            synchronized (lock) {
                active = false;
                lock.notifyAll();
            }

            worker.interrupt();
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private int param(Settings s, int effect, int index) {

        if (effect < 0 || effect >= EFFECTS
                || index < 0 || index >= 3) {
            return 50;
        }

        return Math.max(
                0,
                Math.min(100, s.params[effect][index])
        );
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void status(String message) {
        if (listener != null) {
            listener.onStatus(message);
        }
    }

    private void notifyLooper() {
        if (listener != null) {
            listener.onLooperChanged(
                    loopRecording,
                    loopPlaying,
                    loopLength
            );
        }
    }
}
