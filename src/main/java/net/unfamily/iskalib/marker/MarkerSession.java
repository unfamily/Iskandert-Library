package net.unfamily.iskalib.marker;

import java.util.UUID;

/**
 * Runtime-only session data for marker systems.
 *
 * <p>Session values are not persisted and should be reset on server start/stop.
 */
public final class MarkerSession {
    private MarkerSession() {}

    private static volatile UUID scannerSessionId = UUID.randomUUID();

    public static UUID getScannerSessionId() {
        return scannerSessionId;
    }

    public static void resetScannerSessionId() {
        scannerSessionId = UUID.randomUUID();
    }
}

