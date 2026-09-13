package com.sikumilab.plasmatouch;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 振動の「振幅制御」を Web から呼べるようにするだけの検証用プラグイン。
 * Web の navigator.vibrate() では作れないもの（振幅指定・触覚プリミティブ）を出すのが目的。
 */
@CapacitorPlugin(name = "HapticLab")
public class HapticLabPlugin extends Plugin {

    private Vibrator vibrator() {
        Context ctx = getContext();
        if (ctx == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private static int primitiveId(String name) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return -1;
        switch (name) {
            case "CLICK":       return VibrationEffect.Composition.PRIMITIVE_CLICK;
            case "THUD":        return VibrationEffect.Composition.PRIMITIVE_THUD;
            case "SPIN":        return VibrationEffect.Composition.PRIMITIVE_SPIN;
            case "QUICK_RISE":  return VibrationEffect.Composition.PRIMITIVE_QUICK_RISE;
            case "SLOW_RISE":   return VibrationEffect.Composition.PRIMITIVE_SLOW_RISE;
            case "QUICK_FALL":  return VibrationEffect.Composition.PRIMITIVE_QUICK_FALL;
            case "TICK":        return VibrationEffect.Composition.PRIMITIVE_TICK;
            case "LOW_TICK":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
                }
                return -1;
            default:            return -1;
        }
    }

    private static final String[] ALL_PRIMITIVES = {
        "CLICK", "TICK", "LOW_TICK", "THUD", "SPIN", "QUICK_RISE", "SLOW_RISE", "QUICK_FALL"
    };

    @PluginMethod
    public void info(PluginCall call) {
        Vibrator v = vibrator();
        JSObject r = new JSObject();
        r.put("sdkInt", Build.VERSION.SDK_INT);
        r.put("manufacturer", Build.MANUFACTURER);
        r.put("model", Build.MODEL);
        r.put("hasVibrator", v != null && v.hasVibrator());
        r.put("hasAmplitudeControl", v != null && v.hasAmplitudeControl());

        JSObject prims = new JSObject();
        if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (String name : ALL_PRIMITIVES) {
                int id = primitiveId(name);
                if (id < 0) { prims.put(name, false); continue; }
                boolean[] sup = v.arePrimitivesSupported(id);
                prims.put(name, sup.length > 0 && sup[0]);
            }
        }
        r.put("primitives", prims);
        call.resolve(r);
    }

    /** 振幅を指定した単発振動。これが Web では不可能な部分。 */
    @PluginMethod
    public void oneShot(PluginCall call) {
        Vibrator v = vibrator();
        if (v == null || !v.hasVibrator()) { call.reject("no vibrator"); return; }

        int ms = call.getInt("ms", 50);
        if (ms <= 0) { call.resolve(); return; }
        if (ms > 5000) ms = 5000;

        int amp = call.getInt("amplitude", 128);
        if (amp < 1) amp = 1;
        if (amp > 255) amp = 255;

        if (v.hasAmplitudeControl()) {
            v.vibrate(VibrationEffect.createOneShot(ms, amp));
        } else {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        call.resolve();
    }

    /** 触覚プリミティブの合成。PWM では原理的に作れない質感。 */
    @PluginMethod
    public void composition(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            call.reject("composition requires Android 11 (API 30) or newer");
            return;
        }
        Vibrator v = vibrator();
        if (v == null || !v.hasVibrator()) { call.reject("no vibrator"); return; }

        JSArray arr = call.getArray("primitives");
        if (arr == null || arr.length() == 0) { call.reject("primitives is empty"); return; }

        VibrationEffect.Composition comp = VibrationEffect.startComposition();
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                int id = primitiveId(item.optString("type", ""));
                if (id < 0) continue;
                float scale = (float) item.optDouble("scale", 1.0d);
                if (scale < 0f) scale = 0f;
                if (scale > 1f) scale = 1f;
                int delay = item.optInt("delay", 0);
                if (delay < 0) delay = 0;
                comp.addPrimitive(id, scale, delay);
            }
        } catch (JSONException e) {
            call.reject("bad primitives: " + e.getMessage());
            return;
        }

        v.vibrate(comp.compose());
        call.resolve();
    }

    /** パルス列を native 側で正確に鳴らす。repeat=0 でキャンセルするまでループする。 */
    @PluginMethod
    public void waveform(PluginCall call) {
        Vibrator v = vibrator();
        if (v == null || !v.hasVibrator()) { call.reject("no vibrator"); return; }

        JSArray t = call.getArray("timings");
        JSArray a = call.getArray("amplitudes");
        if (t == null || a == null || t.length() == 0 || t.length() != a.length()) {
            call.reject("timings and amplitudes must be same-length non-empty arrays");
            return;
        }

        long[] timings = new long[t.length()];
        int[] amps = new int[a.length()];
        try {
            for (int i = 0; i < t.length(); i++) {
                timings[i] = Math.max(0, t.getInt(i));
                amps[i] = Math.max(0, Math.min(255, a.getInt(i)));
            }
        } catch (JSONException e) {
            call.reject("bad arrays: " + e.getMessage());
            return;
        }

        int repeat = call.getInt("repeat", -1);
        if (repeat < -1 || repeat >= timings.length) repeat = -1;

        if (v.hasAmplitudeControl()) {
            v.vibrate(VibrationEffect.createWaveform(timings, amps, repeat));
        } else {
            v.vibrate(VibrationEffect.createWaveform(timings, repeat));
        }
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        Vibrator v = vibrator();
        if (v != null) v.cancel();
        call.resolve();
    }
}
