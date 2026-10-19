package me.kvd.configra.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.kvd.configra.PropertyKey;
import me.kvd.configra.diff.ConfigDiff;
import me.kvd.configra.exceptions.PropertyMissingException;
import me.kvd.configra.format.properties.PropertiesFormat;
import me.kvd.configra.options.WriteOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileTest {

  @TempDir
  Path tmp;

  private Path write(final String name, final String content)
    throws IOException {
    final Path file = tmp.resolve(name);
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  @Test
  void loadAndRequire() throws IOException {
    final Path file = write("test.properties", "name=hello\nport=8080\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE);
    assertEquals("hello", config.require(PropertyKey.string("name")));
    assertEquals(8080, config.require(PropertyKey.integer("port")));
  }

  @Test
  void missingRequiredThrows() throws IOException {
    final Path file = write("test.properties", "name=hello\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE);
    assertThrows(PropertyMissingException.class,
      () -> config.require(PropertyKey.string("absent")));
  }

  @Test
  void findWithDefault() throws IOException {
    final Path file = write("test.properties", "name=hello\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE);
    assertEquals("fallback",
      config.find(PropertyKey.string("absent", "fallback")).orElseThrow());
  }

  @Test
  void findWithoutDefault() throws IOException {
    final Path file = write("test.properties", "name=hello\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE);
    assertEquals(Optional.empty(),
      config.find(PropertyKey.string("absent")));
  }

  @Test
  void withReplacesExisting() throws IOException {
    final Path file = write("test.properties", "name=old\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE)
      .with(PropertyKey.string("name"), "new");
    assertEquals("new", config.require(PropertyKey.string("name")));
  }

  @Test
  void withAppendsNew() throws IOException {
    final Path file = write("test.properties", "a=1\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE)
      .with(PropertyKey.string("b"), "2");
    assertEquals("2", config.require(PropertyKey.string("b")));
  }

  @Test
  void withoutRemoves() throws IOException {
    final Path file = write("test.properties", "a=1\nb=2\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE)
      .without(PropertyKey.string("a"));
    assertFalse(config.contains("a"));
    assertTrue(config.contains("b"));
  }

  @Test
  void withAllBatch() throws IOException {
    final Path file = write("test.properties", "a=1\nb=2\n");
    final Map<PropertyKey<?>, Object> updates = new LinkedHashMap<>();
    updates.put(PropertyKey.string("a"), "9");
    updates.put(PropertyKey.string("c"), "3");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE)
      .withAll(updates);
    assertEquals("9", config.require(PropertyKey.string("a")));
    assertEquals("3", config.require(PropertyKey.string("c")));
    assertEquals("2", config.require(PropertyKey.string("b")));
  }

  @Test
  void disableEnableRoundTrip() throws IOException {
    final Path file = write("test.properties", "debug=true\n");
    ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE);

    config = config.disable(PropertyKey.bool("debug"));
    assertTrue(config.isDisabled(PropertyKey.bool("debug")));
    assertEquals(Set.of("debug"), config.disabledKeys());
    assertFalse(config.contains("debug"));

    config = config.enable(PropertyKey.bool("debug"));
    assertFalse(config.isDisabled(PropertyKey.bool("debug")));
    assertTrue(config.require(PropertyKey.bool("debug")));
  }

  @Test
  void withCommentInsertsAbove() throws IOException {
    final Path file = write("test.properties", "key=value\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE)
      .withComment(PropertyKey.string("key"), "Note about key");
    final String text = PropertiesFormat.INSTANCE.write(config.nodes(),
      WriteOptions.utf8());
    assertTrue(text.contains("# Note about key"));
  }

  @Test
  void equalityByValues() throws IOException {
    final Path a = write("a.properties", "x=1\ny=2\n");
    final Path b = write("b.properties", "# comment\nx=1\ny=2\n");
    final ConfigFile configA = ConfigFile.load(a, PropertiesFormat.INSTANCE);
    final ConfigFile configB = ConfigFile.load(b, PropertiesFormat.INSTANCE);
    assertEquals(configA, configB);
    assertEquals(configA.hashCode(), configB.hashCode());
  }

  @Test
  void contentEqualsByStructure() throws IOException {
    final Path a = write("a.properties", "x=1\ny=2\n");
    final Path b = write("b.properties", "# comment\nx=1\ny=2\n");
    final ConfigFile configA = ConfigFile.load(a, PropertiesFormat.INSTANCE);
    final ConfigFile configB = ConfigFile.load(b, PropertiesFormat.INSTANCE);
    assertNotEquals(configA.contentHashCode(), configB.contentHashCode());
    assertFalse(configA.contentEquals(configB));
  }

  @Test
  void saveRoundTrip() throws IOException {
    final Path file = write("source.properties", "a=1\nb=2\n");
    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE);
    final Path out = tmp.resolve("saved.properties");
    config.save(out);

    final ConfigFile reparsed = ConfigFile.load(out, PropertiesFormat.INSTANCE);
    assertEquals(config, reparsed);
  }

  @Test
  void bomStripped() throws IOException {
    final byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    final byte[] content = "key=value\n".getBytes(StandardCharsets.UTF_8);
    final byte[] combined = new byte[bom.length + content.length];
    System.arraycopy(bom, 0, combined, 0, bom.length);
    System.arraycopy(content, 0, combined, bom.length, content.length);
    final Path file = tmp.resolve("bom.properties");
    Files.write(file, combined);

    final ConfigFile config = ConfigFile.load(file, PropertiesFormat.INSTANCE);
    assertTrue(config.contains("key"));
    assertEquals("value", config.require(PropertyKey.string("key")));
  }

  @Test
  void emptyAndOf() {
    final ConfigFile empty = ConfigFile.empty(PropertiesFormat.INSTANCE);
    assertTrue(empty.keys().isEmpty());

    final Map<String, String> values = new LinkedHashMap<>();
    values.put("a", "1");
    values.put("b", "2");
    final ConfigFile constructed = ConfigFile.of(values, PropertiesFormat.INSTANCE);
    assertEquals("1", constructed.require(PropertyKey.string("a")));
    assertEquals("2", constructed.require(PropertyKey.string("b")));
  }

  @Test
  void peekAndPeekAll() throws IOException {
    final Path file = write("test.properties", "a=1\nb=2\nc=3\n");
    assertEquals(Optional.of("2"),
      ConfigFile.peek(file, PropertiesFormat.INSTANCE, "b"));
    final Map<String, String> peeked =
      ConfigFile.peekAll(file, PropertiesFormat.INSTANCE,
        List.of("a", "c", "missing"));
    assertEquals("1", peeked.get("a"));
    assertEquals("3", peeked.get("c"));
    assertFalse(peeked.containsKey("missing"));
  }

  @Test
  void diffDetectsChanges() throws IOException {
    final Path a = write("a.properties", "x=1\ny=2\n");
    final ConfigFile configA = ConfigFile.load(a, PropertiesFormat.INSTANCE);
    final ConfigFile configB = configA.with(PropertyKey.string("x"), "9");

    final ConfigDiff diff = configA.diff(configB);
    assertEquals(Set.of("x"), diff.changed().keySet());
    assertTrue(diff.added().isEmpty());
    assertTrue(diff.removed().isEmpty());
  }
}