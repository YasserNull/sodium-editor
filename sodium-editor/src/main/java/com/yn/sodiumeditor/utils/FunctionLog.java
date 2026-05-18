package com.yn.sodiumeditor.utils;

import android.util.Log;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FunctionLog {
    public static boolean ENABLE_LOGGING = true;
    public static final String DEBUG_TAG = "SodiumDbg";
    /**
     * Minimum time between logs for the same call-site key.
     * Set to 0 to disable throttling (useful for debugging key input).
     */
    public static volatile long THROTTLE_MS = 1000;
    private static final Map<String, Long> lastLogTime = new ConcurrentHashMap<>();

    public static void f(String className, String methodName, Object... args) {
        if (!ENABLE_LOGGING) return;
        
        long now = System.currentTimeMillis();
        String key = (className == null ? "" : className) + "#" + (methodName == null ? "" : methodName);
        Long last = lastLogTime.get(key);
        long throttle = THROTTLE_MS;
        if (throttle > 0 && last != null && (now - last) < throttle) return;
        
        lastLogTime.put(key, now);
        Log.i(DEBUG_TAG, (className == null ? "" : className) + "." + methodName + " " + Arrays.toString(args));
    }

    public static void d(String scope, String message) {
        if (!ENABLE_LOGGING) return;
        Log.d(DEBUG_TAG, "[" + scope + "] " + message);
    }
}
