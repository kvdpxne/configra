package me.kvd.configra.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import me.kvd.configra.PropertyKey;
import me.kvd.configra.core.ConfigFile;
import me.kvd.configra.diff.ConfigDiff;
import me.kvd.configra.format.ini.IniFormat;

/**
 * Walks through every immutable mutation and comparison operation offered
 * by {@link ConfigFile}: {@code with}, {@code withAll}, {@code disable},
 * {@code enable}, {@code withComment}, {@code diff}, save.
 */
public final class Demo05Operations {

    private static final PropertyKey<String> MODE =
            PropertyKey.string("general.mode", "MINECRAFT");
    private static final PropertyKey<Boolean> DEBUG =
            PropertyKey.bool("general.debug", false);
    private static final PropertyKey<Integer> PORT =
            PropertyKey.integer("network.port", 25565);

    private Demo05Operations() {
        throw new AssertionError("No instances.");
    }

    public static void run(final Path dir, final Path source) throws IOException {
        // Snapshot taken before any mutation, used later for diff.
        final ConfigFile snapshot = ConfigFile.load(source, IniFormat.INSTANCE);

        ConfigFile config = snapshot;
        System.out.println("--- Original ---");
        System.out.println("general.mode  = " + config.require(MODE));
        System.out.println("general.debug = " + config.require(DEBUG));
        System.out.println("network.port  = " + config.require(PORT));

        // 1. with() — single-key replacement.
        config = config.with(MODE, "CUSTOM");
        System.out.println();
        System.out.println("--- After with(MODE, \"CUSTOM\") ---");
        System.out.println("general.mode  = " + config.require(MODE));

        // 2. withAll() — batch mutation, single pass over the node list.
        //    Map is LinkedHashMap so append order for missing keys is stable.
        final Map<PropertyKey<?>, Object> updates = new LinkedHashMap<>();
        updates.put(DEBUG, true);
        updates.put(PORT, 19132);
        config = config.withAll(updates);
        System.out.println();
        System.out.println("--- After withAll({debug=true, port=19132}) ---");
        System.out.println("general.debug = " + config.require(DEBUG));
        System.out.println("network.port  = " + config.require(PORT));

        // 3. disable() — comments out a key, preserving its value in the
        //    node graph so enable() can restore it.
        config = config.disable(DEBUG);
        System.out.println();
        System.out.println("--- After disable(DEBUG) ---");
        System.out.println("isDisabled(DEBUG) = " + config.isDisabled(DEBUG));
        System.out.println("find(DEBUG)       = " + config.find(DEBUG));
        System.out.println("disabledKeys()    = " + config.disabledKeys());

        // 4. enable() — restores the disabled key.
        config = config.enable(DEBUG);
        System.out.println();
        System.out.println("--- After enable(DEBUG) ---");
        System.out.println("general.debug = " + config.require(DEBUG));

        // 5. withComment() — insert a comment above an existing key.
        config = config.withComment(MODE,
                "Mutated by Demo 05 at " + Instant.now() + ".");

        // 6. diff() — compare the snapshot against the current state.
        final ConfigDiff diff = snapshot.diff(config);
        System.out.println();
        System.out.println("--- diff(snapshot, current) ---");
        System.out.println("added    = " + diff.added().keySet());
        System.out.println("changed  = " + diff.changed().keySet());
        System.out.println("removed  = " + diff.removed().keySet());
        System.out.println("disabled = " + diff.disabled().keySet());
        diff.changed().forEach((key, value) ->
                System.out.println("  ~ " + key + ": "
                        + diff.previous(key) + " -> " + value));

        // 7. Save the mutated file.
        final Path out = dir.resolve("demo05-mutated.ini");
        config.save(out);
        System.out.println();
        System.out.println("Saved to " + out.getFileName());

        // 8. Show the final content.
        System.out.println();
        System.out.println("--- " + out.getFileName() + " ---");
        System.out.print(Files.readString(out));
    }
}