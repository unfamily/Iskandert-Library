package net.unfamily.iskalib.load;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry for startup datapack/filesystem loaders used by Library and dependent mods.
 * <p>
 * All loaders should read external packs through {@link LoadFilesystemBootstrap} (shared
 * {@code bootstrap.datapack_paths} list). Register during mod construction; late registrations
 * run immediately after {@link #flush()} has already been called.
 */
public final class BootstrapDatapackLoads {
    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapDatapackLoads.class);
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static volatile boolean flushed;

    private BootstrapDatapackLoads() {}

    /**
     * Registers a named bootstrap loader. Safe to call from any mod constructor that depends on
     * {@code iska_lib}.
     */
    public static void register(String id, Runnable loader) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loader, "loader");
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("bootstrap loader id must not be blank");
        }
        ENTRIES.add(new Entry(trimmed, loader));
        if (flushed) {
            runSafe(trimmed, loader);
        }
    }

    /**
     * Runs every registered loader once. Called from Library after its own bootstrap loaders.
     * Subsequent {@link #register} calls execute immediately.
     */
    public static void flush() {
        if (flushed) {
            return;
        }
        flushed = true;
        for (Entry entry : ENTRIES) {
            runSafe(entry.id(), entry.loader());
        }
    }

    private static void runSafe(String id, Runnable loader) {
        try {
            loader.run();
        } catch (Throwable t) {
            LOGGER.error("Bootstrap datapack loader '{}' failed", id, t);
        }
    }

    private record Entry(String id, Runnable loader) {}
}
