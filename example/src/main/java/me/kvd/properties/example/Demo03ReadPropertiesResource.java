package me.kvd.properties.example;

import java.io.IOException;
import java.util.Optional;
import me.kvd.properties.PropertyKey;
import me.kvd.properties.core.ConfigFile;
import me.kvd.properties.format.properties.PropertiesFormat;

/**
 * Reads a {@code .properties} file bundled with the module. Demonstrates
 * required lookups, optional lookups and access to the raw value map.
 */
public final class Demo03ReadPropertiesResource {

    private static final PropertyKey<String> APP_NAME =
            PropertyKey.string("app.name", "unknown");
    private static final PropertyKey<Boolean> APP_DEBUG =
            PropertyKey.bool("app.debug", false);
    private static final PropertyKey<String> SERVER_HOST =
            PropertyKey.string("server.host", "localhost");
    private static final PropertyKey<Integer> SERVER_PORT =
            PropertyKey.integer("server.port", 8080);
    private static final PropertyKey<String> DB_URL =
            PropertyKey.string("database.url");
    private static final PropertyKey<String> DB_USER =
            PropertyKey.string("database.user", "admin");

    private Demo03ReadPropertiesResource() {
        throw new AssertionError("No instances.");
    }

    public static void run() throws IOException {
        final ConfigFile config = ConfigFile.loadFromClasspath(
                "application.properties", PropertiesFormat.INSTANCE);

        System.out.println("format      = " + config.format().name());
        System.out.println("source      = " + config.source());
        System.out.println("keys        = " + config.keys());
        System.out.println();

        // Required reads — throw PropertyMissingException if absent
        // and no default is declared.
        System.out.println("app.name      = " + config.require(APP_NAME));
        System.out.println("server.host   = " + config.require(SERVER_HOST));
        System.out.println("server.port   = " + config.require(SERVER_PORT));
        System.out.println("database.url  = " + config.require(DB_URL));
        System.out.println("database.user = " + config.require(DB_USER));

        // Optional read: falls back to default when the key is absent.
        final boolean debug = config.getOrElse(APP_DEBUG, false);
        System.out.println("app.debug     = " + debug);

        // Probe without default — Optional.empty() when absent.
        final Optional<String> password =
                config.find(PropertyKey.string("database.password"));
        System.out.println("database.password present? " + password.isPresent());

        // Direct access to the raw string map (unmodifiable view).
        System.out.println();
        System.out.println("raw map       = " + config.toRawMap());
    }
}