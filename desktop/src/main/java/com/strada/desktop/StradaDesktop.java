
package com.strada.desktop;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class StradaDesktop extends JFrame {
    private final JComboBox<String> inputBox = new JComboBox<>();
    private final JComboBox<String> outputBox = new JComboBox<>();
    private final JLabel status = new JLabel("Audio stopped");
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final JSlider gain = slider(100, 0, 200);
    private final JSlider drive = slider(20, 0, 100);
    private final JSlider tone = slider(60, 0, 100);
    private final JSlider delay = slider(0, 0, 80);
    private final JSlider master = slider(70, 0, 100);

    private TargetDataLine input;
    private SourceDataLine output;
    private Thread audioThread;

    public StradaDesktop() {
        super("Strada Guitar FX Pro Desktop");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(620, 650);
        setLocationRelativeTo(null);

        JPanel root = new JPanel();
        root.setBackground(new Color(20, 22, 28));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("STRADA GUITAR FX PRO");
        title.setForeground(new Color(255, 145, 45));
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        root.add(title);
        root.add(Box.createVerticalStrut(12));

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getTargetLineInfo().length > 0)
                inputBox.addItem(info.getName());
            if (mixer.getSourceLineInfo().length > 0)
                outputBox.addItem(info.getName());
        }

        root.add(label("AUDIO INPUT"));
        root.add(inputBox);
        root.add(Box.createVerticalStrut(8));
        root.add(label("AUDIO OUTPUT"));
        root.add(outputBox);
        root.add(Box.createVerticalStrut(12));

        addControl(root, "Gain", gain);
        addControl(root, "Drive", drive);
        addControl(root, "Tone", tone);
        addControl(root, "Delay", delay);
        addControl(root, "Master Volume", master);

        JButton start = new JButton("START AUDIO FX");
        start.setBackground(new Color(30, 170, 100));
        start.setForeground(Color.WHITE);
        start.addActionListener(e -> startAudio());

        JButton stop = new JButton("STOP AUDIO FX");
        stop.setBackground(new Color(190, 55, 55));
        stop.setForeground(Color.WHITE);
        stop.addActionListener(e -> stopAudio());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.add(start);
        buttons.add(stop);
        root.add(Box.createVerticalStrut(12));
        root.add(buttons);

        status.setForeground(Color.WHITE);
        root.add(Box.createVerticalStrut(12));
        root.add(status);

        JScrollPane scroll = new JScrollPane(root);
        setContentPane(scroll);
        getContentPane().setBackground(root.getBackground());
    }

    private static JSlider slider(int value, int min, int max) {
        JSlider s = new JSlider(min, max, value);
        s.setMajorTickSpacing((max - min) / 4);
        s.setPaintTicks(true);
        s.setPaintLabels(true);
        return s;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(220, 225, 235));
        return l;
    }

    private void addControl(JPanel root, String name, JSlider slider) {
        root.add(label(name));
        root.add(slider);
    }

    private void startAudio() {
        if (running.get()) return;
        if (inputBox.getSelectedItem() == null ||
            outputBox.getSelectedItem() == null) {
            status.setText("No compatible audio device found");
            return;
        }

        try {
            AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
            Mixer inMixer = findMixer((String) inputBox.getSelectedItem());
            Mixer outMixer = findMixer((String) outputBox.getSelectedItem());

            DataLine.Info inInfo = new DataLine.Info(TargetDataLine.class, format);
            DataLine.Info outInfo = new DataLine.Info(SourceDataLine.class, format);

            input = (TargetDataLine) inMixer.getLine(inInfo);
            output = (SourceDataLine) outMixer.getLine(outInfo);

            input.open(format, 4096);
            output.open(format, 4096);
            input.start();
            output.start();

            running.set(true);
            status.setText("Audio running — use headphones");
            audioThread = new Thread(() -> processAudio(format), "StradaAudio");
            audioThread.setPriority(Thread.MAX_PRIORITY);
            audioThread.start();
        } catch (Exception ex) {
            running.set(false);
            closeLines();
            status.setText("Audio error: " + ex.getMessage());
        }
    }

    private Mixer findMixer(String name) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(name))
                return AudioSystem.getMixer(info);
        }
        throw new IllegalStateException("Audio device not found: " + name);
    }

    private void processAudio(AudioFormat format) {
        byte[] buffer = new byte[1024];
        short[] previous = new short[buffer.length / 2];
        int delaySamples = 44100 / 5;
        short[] delayBuffer = new short[delaySamples];
        int delayPos = 0;

        while (running.get()) {
            int count = input.read(buffer, 0, buffer.length);
            if (count <= 0) continue;

            float g = gain.getValue() / 100f;
            float d = drive.getValue() / 100f;
            float t = tone.getValue() / 100f;
            float echo = delay.getValue() / 100f * 0.55f;
            float volume = master.getValue() / 100f;

            for (int i = 0; i + 1 < count; i += 2) {
                int raw = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff));
                float x = raw * g / 32768f;

                x *= 1.0f + d * 5.0f;
                x = (float) Math.tanh(x);

                int sample = (int) (x * 32767f);
                sample = (int) (sample * (0.35f + t * 0.65f));

                int delayed = delayBuffer[delayPos];
                delayBuffer[delayPos] = (short) Math.max(-32768,
                        Math.min(32767, sample));
                delayPos = (delayPos + 1) % delayBuffer.length;

                sample = (int) ((sample + delayed * echo) * volume);
                sample = Math.max(-32768, Math.min(32767, sample));

                buffer[i] = (byte) (sample & 0xff);
                buffer[i + 1] = (byte) ((sample >> 8) & 0xff);
            }
            output.write(buffer, 0, count);
        }
    }

    private void stopAudio() {
        running.set(false);
        closeLines();
        status.setText("Audio stopped");
    }

    private void closeLines() {
        if (input != null) {
            input.stop();
            input.close();
            input = null;
        }
        if (output != null) {
            output.stop();
            output.close();
            output = null;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
                new StradaDesktop().setVisible(true));
    }
}
