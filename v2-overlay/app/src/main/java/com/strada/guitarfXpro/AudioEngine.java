package com.strada.guitarfXpro;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Strada Guitar FX Pro audio engine.
 *
 * Audio path: AudioRecord -> DSP -> looper -> AudioTrack.
 * Amp and cabinet models are algorithmic starting points, not captured models
 * or impulse responses (IRs). Actual USB routing depends on Android/device support.
 */
public final class AudioEngine {
    public static final int RATE = 44100;
    public static final int BLOCK = 256;
    public static final int EFFECTS = 8;
    private static final int LOOP_SECONDS = 30;

    public interface Listener {
        void onStatus(String message);
        void onPitch(float frequencyHz);
        void onLooperChanged(boolean recording, boolean playing, int samples);
    }

    public static final class Settings {
        public final boolean[] enabled = new boolean[EFFECTS];
        public final int[][] params = new int[EFFECTS][3];

        public float master = .72f;
        public float gain = 3f;
        public float bass = .5f;
        public float middle = .5f;
        public float treble = .6f;
        public float ampVolume = .7f;

        public boolean ampOn = true;
        public boolean cabOn = true;
        public int ampModel = 0;
        public int cabModel = 5;
        public String input = "Microphone (HP)";
        public String output = "System / Headphone";

        public Settings copy() {
            Settings copy = new Settings();
            synchronized (this) {
                System.arraycopy(enabled, 0, copy.enabled, 0, EFFECTS);
                for (int i = 0; i < EFFECTS; i++) {
                    System.arraycopy(params[i], 0, copy.params[i], 0, 3);
                }
                copy.master = master;
                copy.gain = gain;
                copy.bass = bass;
                copy.middle = middle;
                copy.treble = treble;
                copy.ampVolume = ampVolume;
                copy.ampOn = ampOn;
                copy.cabOn = cabOn;
                copy.ampModel = ampModel;
                copy.cabModel = cabModel;
                copy.input = input;
                copy.output = output;
            }
            return copy;
        }
    }

    private final Context context;
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final short[] loopBuffer = new short[RATE * LOOP_SECONDS];

    private volatile Settings settings = new Settings();
    private volatile Thread audioThread;
    private volatile AudioRecord recorder;
    private volatile AudioTrack track;

    private volatile boolean loopRecording;
    private volatile boolean loopPlaying;
    private volatile int loopLength;
    private volatile int loopWritePosition;
    private volatile int loopReadPosition;

    // DSP state is owned by the audio thread.
    private final float[] delay = new float[RATE * 2];
    private final float[] reverb = new float[RATE / 2];
    private final float[] chorus = new float[RATE / 5];
    private int delayPosition, reverbPosition, chorusPosition;
    private float gateEnvelope, compressorEnvelope;
    private float ampLow, ampMid, cabLow;
    private float overdriveState, distortionState, fuzzState;
    private double chorusPhase;
    private final PitchDetector pitchDetector = new PitchDetector();

    public AudioEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void updateSettings(Settings value) {
        if (value != null) settings = value.copy();
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start() {
        if (running.get()) {
            status("Audio sudah berjalan.");
            return;
        }
        if (audioThread != null && audioThread.isAlive()) {
            status("Mesin audio masih menyelesaikan proses berhenti.");
            return;
        }
        running.set(true);
        audioThread = new Thread(this::runAudio, "StradaAudioEngine");
        audioThread.start();
    }

    public void stop() {
        running.set(false);
        AudioRecord current = recorder;
        if (current != null) {
            try {
                if (current.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    current.stop();
                }
            } catch (Exception ignored) {
            }
        }
        status("Mematikan mesin audio...");
    }

    public synchronized void release() {
        stop();
        Thread current = audioThread;
        if (current != null && current != Thread.currentThread()) {
            try {
                current.join(1800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized void startLoopRecording() {
        if (!running.get()) {
            status("Jalankan audio sebelum merekam loop.");
            return;
        }
        loopPlaying = false;
        loopLength = 0;
        loopWritePosition = 0;
        loopReadPosition = 0;
        loopRecording = true;
        notifyLooper();
    }

    public synchronized void stopLoopRecording() {
        loopRecording = false;
        loopLength = loopWritePosition;
        loopReadPosition = 0;
        notifyLooper();
    }

    public synchronized void toggleLoopPlayback() {
        if (loopPlaying) {
            loopPlaying = false;
        } else if (running.get() && loopLength > 0) {
            loopReadPosition = 0;
            loopPlaying = true;
        } else {
            status("Belum ada loop tersimpan atau audio belum berjalan.");
        }
        notifyLooper();
    }

    public synchronized void clearLoop() {
        loopRecording = false;
        loopPlaying = false;
        loopLength = 0;
        loopWritePosition = 0;
        loopReadPosition = 0;
        notifyLooper();
    }

    public boolean isLoopRecording() { return loopRecording; }
    public boolean isLoopPlaying() { return loopPlaying; }
    public int getLoopLengthSamples() { return loopLength; }

    private void runAudio() {
        String error = null;
        try {
            int inputMin = AudioRecord.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int outputMin = AudioTrack.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            if (inputMin <= 0 || outputMin <= 0) {
                throw new IllegalStateException("PCM 44.1 kHz tidak didukung perangkat.");
            }

            int bytes = Math.max(Math.max(inputMin, outputMin), BLOCK * 8);
            Settings initial = settings.copy();

            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bytes);

            track = new AudioTrack(
                    AudioManager.STREAM_MUSIC, RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bytes, AudioTrack.MODE_STREAM);

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED
                    || track.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord/AudioTrack gagal diinisialisasi.");
            }

            applyRouting(initial);
            track.play();
            recorder.startRecording();

            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Input audio gagal dimulai.");
            }

            status("AUDIO RUNNING • 44.1 kHz • " + initial.input + " → " + initial.output);

            short[] input = new short[BLOCK];
            short[] output = new short[BLOCK];

            while (running.get()) {
                AudioRecord currentRecorder = recorder;
                if (currentRecorder == null) break;

                int count = currentRecorder.read(input, 0, input.length);
                if (count == AudioRecord.ERROR_DEAD_OBJECT) {
                    throw new IllegalStateException("Perangkat audio terputus.");
                }
                if (count == AudioRecord.ERROR_INVALID_OPERATION
                        || count == AudioRecord.ERROR_BAD_VALUE || count < 0) {
                    if (!running.get()) break;
                    throw new IllegalStateException("Input audio error: " + count);
                }
                if (count == 0) continue;

                Settings current = settings.copy();
                processBlock(input, output, count, current);
                pitchDetector.add(input, count);

                int written = track.write(output, 0, count);
                if (written < 0 && running.get()) {
                    throw new IllegalStateException("Output audio error: " + written);
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                error = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
            }
        } finally {
            running.set(false);
            AudioRecord input = recorder;
            recorder = null;
            if (input != null) {
                try {
                    if (input.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) input.stop();
                } catch (Exception ignored) {}
                try { input.release(); } catch (Exception ignored) {}
            }

            AudioTrack output = track;
            track = null;
            if (output != null) {
                try {
                    if (output.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) output.stop();
                } catch (Exception ignored) {}
                try { output.release(); } catch (Exception ignored) {}
            }

            loopRecording = false;
            loopPlaying = false;
            notifyLooper();
            status(error == null ? "Audio berhenti." : "Audio error: " + error);

            synchronized (this) {
                audioThread = null;
            }
        }
    }

    private void applyRouting(Settings value) {
        if (Build.VERSION.SDK_INT < 23) {
            if (!"Microphone (HP)".equals(value.input)
                    || !"System / Headphone".equals(value.output)) {
                throw new IllegalStateException("Pemilihan perangkat membutuhkan Android 6.0+.");
            }
            return;
        }

        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) throw new IllegalStateException("AudioManager tidak tersedia.");

        AudioDeviceInfo inputDevice = findDevice(
                manager.getDevices(AudioManager.GET_DEVICES_INPUTS), value.input, true);

        if (inputDevice == null && !"Microphone (HP)".equals(value.input)) {
            throw new IllegalStateException("Input " + value.input
                    + " tidak ditemukan. Tidak dialihkan diam-diam ke mikrofon HP.");
        }
        if (inputDevice != null && !recorder.setPreferredDevice(inputDevice)) {
            throw new IllegalStateException("Android menolak routing input " + value.input + ".");
        }

        if (!"System / Headphone".equals(value.output)) {
            AudioDeviceInfo outputDevice = findDevice(
                    manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS), value.output, false);
            if (outputDevice == null) {
                throw new IllegalStateException("Output " + value.output + " tidak ditemukan.");
            }
            if (!track.setPreferredDevice(outputDevice)) {
                throw new IllegalStateException("Android menolak routing output " + value.output + ".");
            }
        }
    }

    private AudioDeviceInfo findDevice(AudioDeviceInfo[] devices, String wanted, boolean input) {
        for (AudioDeviceInfo device : devices) {
            if (input && !device.isSource()) continue;
            if (!input && !device.isSink()) continue;
            int type = device.getType();

            if (input && "Microphone (HP)".equals(wanted)
                    && type == AudioDeviceInfo.TYPE_BUILTIN_MIC) return device;
            if (input && "Wired Headset Mic".equals(wanted)
                    && type == AudioDeviceInfo.TYPE_WIRED_HEADSET) return device;
            if ("USB Audio Interface".equals(wanted)
                    || "USB Audio Output".equals(wanted)) {
                if (isUsbType(type)) return device;
            }
            if (!input && "Speaker".equals(wanted)
                    && type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return device;
        }
        return null;
    }

    private boolean isUsbType(int type) {
        if (type == AudioDeviceInfo.TYPE_USB_DEVICE) return true;
        if (Build.VERSION.SDK_INT >= 26) {
            return type == AudioDeviceInfo.TYPE_USB_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_ACCESSORY;
        }
        return false;
    }

    private int parameter(Settings s, int effect, int index) {
        return Math.max(0, Math.min(100, s.params[effect][index]));
    }

    private void processBlock(short[] input, short[] output, int count, Settings s) {
        for (int i = 0; i < count; i++) {
            float x = input[i] / 32768f;

            if (s.enabled[0]) {
                float level = Math.abs(x);
                float coefficient = level > gateEnvelope
                        ? .25f : .004f + parameter(s, 0, 1) * .0008f;
                gateEnvelope += coefficient * (level - gateEnvelope);
                if (gateEnvelope < .002f + parameter(s, 0, 0) * .00095f) x *= .025f;
            }

            if (s.enabled[1]) {
                float level = Math.abs(x);
                compressorEnvelope += (level > compressorEnvelope ? .18f : .012f)
                        * (level - compressorEnvelope);
                float threshold = .38f - parameter(s, 1, 0) * .0027f;
                if (compressorEnvelope > threshold && compressorEnvelope > .0001f) {
                    float ratio = 1f + parameter(s, 1, 0) / 18f;
                    x *= (threshold + (compressorEnvelope - threshold) / ratio)
                            / compressorEnvelope;
                }
                x *= .65f + parameter(s, 1, 1) * .007f;
            }

            if (s.enabled[2]) {
                float shaped = (float) Math.tanh(x * (1f + parameter(s, 2, 0) / 7f));
                overdriveState += (.015f + parameter(s, 2, 1) * .0022f)
                        * (shaped - overdriveState);
                x = (overdriveState * (1f - parameter(s, 2, 1) / 150f)
                        + shaped * parameter(s, 2, 1) / 150f)
                        * (.5f + parameter(s, 2, 2) * .009f);
            }

            if (s.enabled[3]) {
                float shaped = (float) Math.tanh(x * (1f + parameter(s, 3, 0) / 4f));
                distortionState += (.015f + parameter(s, 3, 1) * .0025f)
                        * (shaped - distortionState);
                x = (distortionState * (1f - parameter(s, 3, 1) / 160f)
                        + shaped * parameter(s, 3, 1) / 160f)
                        * (.45f + parameter(s, 3, 2) * .0095f);
            }

            if (s.enabled[4]) {
                float shaped = (float) Math.tanh(x * (1.5f + parameter(s, 4, 0) / 3.2f));
                fuzzState += (.01f + parameter(s, 4, 1) * .0028f) * (shaped - fuzzState);
                x = (fuzzState * (1f - parameter(s, 4, 1) / 170f)
                        + shaped * parameter(s, 4, 1) / 170f)
                        * (.4f + parameter(s, 4, 2) * .009f);
            }

            if (s.enabled[5]) {
                chorus[chorusPosition] = x;
                int modulation = (int) ((Math.sin(chorusPhase) * .5 + .5)
                        * parameter(s, 5, 1) * RATE * .00018f);
                int distance = Math.max(1, Math.min(chorus.length - 1,
                        (int) (RATE * .008f) + modulation));
                float wet = chorus[(chorusPosition - distance + chorus.length) % chorus.length];
                float mix = parameter(s, 5, 2) / 100f;
                x = x * (1f - mix * .55f) + wet * mix * .75f;
                chorusPosition = (chorusPosition + 1) % chorus.length;
                chorusPhase += 2 * Math.PI * (.15 + parameter(s, 5, 0) * .05) / RATE;
            }

            if (s.enabled[6]) {
                int distance = Math.max(1, Math.min(delay.length - 1,
                        (int) ((.03f + parameter(s, 6, 0) * .0047f) * RATE)));
                float echo = delay[(delayPosition - distance + delay.length) % delay.length];
                float mix = parameter(s, 6, 2) / 100f;
                delay[delayPosition] = x + echo * Math.min(.82f, parameter(s, 6, 1) / 125f);
                x = x * (1f - mix * .75f) + echo * mix * .85f;
                delayPosition = (delayPosition + 1) % delay.length;
            } else {
                delay[delayPosition] = x;
                delayPosition = (delayPosition + 1) % delay.length;
            }

            if (s.enabled[7]) {
                float reflection = reverb[reverbPosition];
                float mix = parameter(s, 7, 2) / 100f;
                reverb[reverbPosition] = x + reflection
                        * Math.min(.86f, .28f + parameter(s, 7, 0) / 180f);
                x = x * (1f - mix * .7f)
                        + reflection * (.75f + parameter(s, 7, 1) / 400f) * mix;
                reverbPosition = (reverbPosition + 1) % reverb.length;
            } else {
                reverb[reverbPosition] = x;
                reverbPosition = (reverbPosition + 1) % reverb.length;
            }

            if (s.ampOn) {
                float driven = (float) Math.tanh(x * s.gain * ampDrive(s.ampModel));
                ampLow += .035f * (driven - ampLow);
                ampMid += .16f * (driven - ampMid);
                x = ampLow * (.35f + s.bass * 1.15f)
                        + (ampMid - ampLow) * (.35f + s.middle * 1.15f)
                        + (driven - ampMid) * (.25f + s.treble * 1.35f);
                x *= .55f + s.ampVolume * .7f;
            }

            if (s.cabOn) {
                cabLow += cabCutoff(s.cabModel) * (x - cabLow);
                x = cabLow * .88f + x * .12f;
            }

            // Looper captures the processed signal before master volume.
            if (loopRecording) {
                int position = loopWritePosition;
                if (position < loopBuffer.length) {
                    loopBuffer[position] = (short) (clamp(x) * 32767f);
                    loopWritePosition = position + 1;
                    if (loopWritePosition >= loopBuffer.length) {
                        loopRecording = false;
                        loopLength = loopWritePosition;
                        notifyLooper();
                    }
                }
            }

            if (loopPlaying && loopLength > 0) {
                int position = loopReadPosition;
                if (position >= loopLength) position = 0;
                x = x * .75f + (loopBuffer[position] / 32768f) * .75f;
                loopReadPosition = (position + 1) % loopLength;
            }

            output[i] = (short) (clamp(x * s.master) * 32767f);
        }
    }

    private final class PitchDetector {
        private final float[] samples = new float[2048];
        private int position;
        private int filled;
        private int sinceCheck;

        void add(short[] input, int count) {
            for (int i = 0; i < count; i++) {
                samples[position] = input[i] / 32768f;
                position = (position + 1) % samples.length;
                if (filled < samples.length) filled++;
                sinceCheck++;
            }
            if (filled < samples.length || sinceCheck < 1024) return;
            sinceCheck = 0;

            float mean = 0f;
            for (float value : samples) mean += value;
            mean /= samples.length;

            float bestScore = 0f;
            int bestLag = -1;
            int minLag = RATE / 1200;
            int maxLag = RATE / 55;
            for (int lag = minLag; lag <= maxLag; lag++) {
                double correlation = 0, energyA = 0, energyB = 0;
                for (int j = 0; j < samples.length - lag; j += 2) {
                    float a = samples[(position + j) % samples.length] - mean;
                    float b = samples[(position + j + lag) % samples.length] - mean;
                    correlation += a * b;
                    energyA += a * a;
                    energyB += b * b;
                }
                if (energyA > 1e-7 && energyB > 1e-7) {
                    float score = (float) (correlation / Math.sqrt(energyA * energyB));
                    if (score > bestScore) {
                        bestScore = score;
                        bestLag = lag;
                    }
                }
            }
            if (bestLag > 0 && bestScore > .48f) {
                float frequency = RATE / (float) bestLag;
                if (frequency >= 50f && frequency <= 1200f && listener != null) {
                    listener.onPitch(frequency);
                }
            }
        }
    }

    private float ampDrive(int model) {
        switch (model) {
            case 0: return .72f;
            case 1: return .82f;
            case 2: return 1.02f;
            case 3: return .86f;
            case 4: return 1.12f;
            case 5: return 1.20f;
            case 6: return 1.32f;
            case 7: return 1.62f;
            case 8: return 1.85f;
            case 9: return .68f;
            case 10: return 1.16f;
            default: return 1.42f;
        }
    }

    private float cabCutoff(int model) {
        switch (model) {
            case 0: return .045f;
            case 1: return .060f;
            case 2: return .085f;
            case 3: return .105f;
            case 4: return .150f;
            case 5: return .095f;
            case 6: return .125f;
            case 7: return .080f;
            default: return .100f;
        }
    }

    private float clamp(float value) {
        return Math.max(-.95f, Math.min(.95f, value));
    }

    private void status(String message) {
        if (listener != null) listener.onStatus(message);
    }

    private void notifyLooper() {
        if (listener != null) {
            listener.onLooperChanged(loopRecording, loopPlaying, loopLength);
        }
    }
    }
