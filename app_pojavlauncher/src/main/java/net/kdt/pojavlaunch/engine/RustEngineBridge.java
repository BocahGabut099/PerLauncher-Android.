package net.kdt.pojavlaunch.engine;

/**
 * JNI facade for the PerLauncher Rust engine.
 *
 * The lifecycle API is available, but the native Minecraft runtime is not
 * implemented yet. launchGame() therefore reports runtime_unavailable and
 * never claims that a game process has started.
 */
public final class RustEngineBridge {
    private static final String BRIDGE_UNAVAILABLE_JSON =
            "{\"ok\":false,\"status\":\"bridge_unavailable\",\"runtime_available\":false,\"active_session\":false,\"jvm_free_gameplay\":false}";
    private static final String INVALID_REQUEST_JSON =
            "{\"ok\":false,\"status\":\"invalid_request\",\"message\":\"Launch request must not be empty.\"}";

    private static final boolean AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("perlauncher_engine");
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private RustEngineBridge() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String getNativeEngineVersion() {
        return AVAILABLE ? nativeEngineVersion() : null;
    }

    public static String getNativeCapabilitiesJson() {
        return AVAILABLE ? nativeCapabilitiesJson() : null;
    }

    /** Initializes the native lifecycle controller and returns a JSON result. */
    public static String initializeEngine() {
        return AVAILABLE ? nativeInitialize() : BRIDGE_UNAVAILABLE_JSON;
    }

    /** Returns the current native lifecycle state as JSON. */
    public static String getStatusJson() {
        return AVAILABLE ? nativeStatus() : BRIDGE_UNAVAILABLE_JSON;
    }

    /**
     * Validates a launch request. This does not start Minecraft until a native
     * runtime backend has been implemented.
     */
    public static String launchGame(String requestJson) {
        if (requestJson == null || requestJson.trim().isEmpty()) {
            return INVALID_REQUEST_JSON;
        }
        return AVAILABLE ? nativeLaunch(requestJson) : BRIDGE_UNAVAILABLE_JSON;
    }

    /** Stops an active native session if one exists; currently no runtime is present. */
    public static String stopGame() {
        return AVAILABLE ? nativeStop() : BRIDGE_UNAVAILABLE_JSON;
    }

    private static native String nativeEngineVersion();

    private static native String nativeCapabilitiesJson();

    private static native String nativeInitialize();

    private static native String nativeStatus();

    private static native String nativeLaunch(String requestJson);

    private static native String nativeStop();
}
