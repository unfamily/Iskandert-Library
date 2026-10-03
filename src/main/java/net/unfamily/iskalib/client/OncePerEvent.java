package net.unfamily.iskalib.client;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards client registrations against NeoForge fan-out of {@code IModBusEvent}s:
 * the same event instance is posted to every mod bus, so a given handler must run
 * at most once per event instance.
 */
public final class OncePerEvent {

    private static final ConcurrentHashMap<Object, Object> LAST_BY_OWNER = new ConcurrentHashMap<>();

    private OncePerEvent() {}

    /**
     * @param owner stable identity for the handler (typically the handler {@link Class})
     * @return {@code true} if this is the first claim of {@code event} for {@code owner}
     */
    public static boolean claim(Object owner, Object event) {
        if (owner == null || event == null) {
            return false;
        }
        Object previous = LAST_BY_OWNER.put(owner, event);
        return previous != event;
    }
}
