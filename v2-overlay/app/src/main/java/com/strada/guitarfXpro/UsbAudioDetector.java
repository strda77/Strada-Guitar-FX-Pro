
package com.strada.guitarfXpro;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class UsbAudioDetector {

    public interface Listener {
        void onDevicesChanged(String report);
    }

    private final AudioManager audioManager;
    private final Listener listener;
    private AudioDeviceCallback callback;

    public UsbAudioDetector(Context context, Listener listener) {
        this.audioManager = (AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
    }

    public void start() {
        if (Build.VERSION.SDK_INT < 23 || audioManager == null) {
            notifyListener("Deteksi audio USB membutuhkan Android 6.0 atau lebih baru.");
            return;
        }

        if (callback != null) {
            refresh();
            return;
        }

        callback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                refresh();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                refresh();
            }
        };

        audioManager.registerAudioDeviceCallback(callback, null);
        refresh();
    }

    public void stop() {
        if (Build.VERSION.SDK_INT >= 23
                && audioManager != null && callback != null) {
            audioManager.unregisterAudioDeviceCallback(callback);
            callback = null;
        }
    }

    public void refresh() {
        if (Build.VERSION.SDK_INT < 23 || audioManager == null) return;

        AudioDeviceInfo[] devices =
                audioManager.getDevices(AudioManager.GET_DEVICES_ALL);

        StringBuilder report = new StringBuilder();
        int usbCount = 0;

        for (AudioDeviceInfo device : devices) {
            int type = device.getType();

            boolean isUsb =
                    type == AudioDeviceInfo.TYPE_USB_DEVICE
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_ACCESSORY;

            if (!isUsb) continue;

            usbCount++;

            CharSequence product = device.getProductName();
            String name = product == null
                    ? "USB Audio"
                    : product.toString();

            report.append("• ").append(name).append("\n");
            report.append("  ID: ").append(device.getId()).append("\n");
            report.append("  Input: ")
                    .append(device.isSource() ? "Ya" : "Tidak")
                    .append("\n");
            report.append("  Output: ")
                    .append(device.isSink() ? "Ya" : "Tidak")
                    .append("\n\n");
        }

        if (usbCount == 0) {
            notifyListener("Belum ada perangkat audio USB yang terdeteksi.");
        } else {
            notifyListener("Perangkat audio USB terdeteksi: "
                    + usbCount + "\n\n" + report);
        }
    }

    private void notifyListener(String message) {
        if (listener != null) {
            listener.onDevicesChanged(message);
        }
    }
}
