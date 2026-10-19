package me.kvd.configra;

import java.time.Duration;
import java.util.List;
import me.kvd.configra.exceptions.PropertiesException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyCodecTest {

  @Nested
  @DisplayName("STRING")
  class StringCodec {

    @Test
    void identityRoundTrip() {
      var expected = "hello world";

      assertEquals(expected, PropertyCodec.STRING.decode(expected));
      assertEquals(expected, PropertyCodec.STRING.encode(expected));
    }

    @Test
    void valueTypeIsString() {
      assertEquals(String.class, PropertyCodec.STRING.valueType());
    }
  }

  @Nested
  @DisplayName("INTEGER")
  class IntegerCodec {
    @Test
    void parsesValid() {
      assertEquals(42, PropertyCodec.INTEGER.decode("42"));
      assertEquals(-7, PropertyCodec.INTEGER.decode("-7"));
      assertEquals(100, PropertyCodec.INTEGER.decode("  100  "));
    }

    @Test
    void throwsOnInvalid() {
      assertThrows(PropertiesException.class,
        () -> PropertyCodec.INTEGER.decode("abc"));
      assertThrows(PropertiesException.class,
        () -> PropertyCodec.INTEGER.decode(""));
    }

    @Test
    void encodesRoundTrip() {
      assertEquals("42", PropertyCodec.INTEGER.encode(42));
      assertEquals(42, PropertyCodec.INTEGER.decode(
        PropertyCodec.INTEGER.encode(42)));
    }
  }

  @Nested
  @DisplayName("BOOLEAN")
  class BooleanCodec {
    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "yes", "YES", "on", "ON", "1"})
    void truthy(final String input) {
      assertTrue(PropertyCodec.BOOLEAN.decode(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", "no", "NO", "off", "OFF", "0"})
    void falsy(final String input) {
      assertFalse(PropertyCodec.BOOLEAN.decode(input));
    }

    @Test
    void throwsOnGarbage() {
      assertThrows(PropertiesException.class,
        () -> PropertyCodec.BOOLEAN.decode("maybe"));
    }

    @Test
    void encodesAsLowercase() {
      assertEquals("true", PropertyCodec.BOOLEAN.encode(true));
      assertEquals("false", PropertyCodec.BOOLEAN.encode(false));
    }
  }

  @Nested
  @DisplayName("DURATION")
  class DurationCodec {
    @Test
    void iso8601() {
      assertEquals(Duration.ofSeconds(30),
        PropertyCodec.DURATION.decode("PT30S"));
      assertEquals(Duration.ofMinutes(5),
        PropertyCodec.DURATION.decode("PT5M"));
    }

    @Test
    void friendly() {
      assertEquals(Duration.ofSeconds(30),
        PropertyCodec.DURATION.decode("30s"));
      assertEquals(Duration.ofMinutes(5),
        PropertyCodec.DURATION.decode("5m"));
      assertEquals(Duration.ofMinutes(90),
        PropertyCodec.DURATION.decode("1h30m"));
      assertEquals(Duration.ofMillis(500),
        PropertyCodec.DURATION.decode("500ms"));
    }

    @Test
    void encodesAsIso() {
      assertEquals("PT30S", PropertyCodec.DURATION.encode(
        Duration.ofSeconds(30)));
    }

    @Test
    void roundTrip() {
      final Duration original = Duration.ofMinutes(90).plusSeconds(15);
      final Duration parsed = PropertyCodec.DURATION.decode(
        PropertyCodec.DURATION.encode(original));
      assertEquals(original, parsed);
    }

    @Test
    void throwsOnEmpty() {
      assertThrows(PropertiesException.class,
        () -> PropertyCodec.DURATION.decode(""));
    }
  }

  @Nested
  @DisplayName("STRING_LIST")
  class StringListCodec {
    @Test
    void parsesCommaSeparated() {
      assertEquals(List.of("a", "b", "c"),
        PropertyCodec.STRING_LIST.decode("a, b, c"));
    }

    @Test
    void emptyInput() {
      assertEquals(List.of(), PropertyCodec.STRING_LIST.decode(""));
      assertEquals(List.of(), PropertyCodec.STRING_LIST.decode("   "));
    }

    @Test
    void escapedComma() {
      assertEquals(List.of("a,b", "c"),
        PropertyCodec.STRING_LIST.decode("a\\,b, c"));
    }

    @Test
    void escapedBackslash() {
      assertEquals(List.of("a\\b"),
        PropertyCodec.STRING_LIST.decode("a\\\\b"));
    }

    @Test
    void encodesWithSeparator() {
      assertEquals("a, b, c",
        PropertyCodec.STRING_LIST.encode(List.of("a", "b", "c")));
    }

    @Test
    void encodesEmpty() {
      assertEquals("", PropertyCodec.STRING_LIST.encode(List.of()));
    }

    @Test
    void roundTrip() {
      final List<String> original = List.of("alpha", "beta gamma", "x,y");
      final List<String> parsed = PropertyCodec.STRING_LIST.decode(
        PropertyCodec.STRING_LIST.encode(original));
      assertEquals(original, parsed);
    }
  }

  @Nested
  @DisplayName("enumeration")
  class Enumeration {
    enum Color {RED, GREEN, BLUE}

    @Test
    void decodeCaseInsensitive() {
      assertEquals(Color.RED,
        PropertyCodec.enumeration(Color.class).decode("red"));
      assertEquals(Color.GREEN,
        PropertyCodec.enumeration(Color.class).decode("GREEN"));
    }

    @Test
    void throwsOnUnknown() {
      assertThrows(PropertiesException.class,
        () -> PropertyCodec.enumeration(Color.class).decode("PURPLE"));
    }

    @Test
    void returnsCachedInstance() {
      assertSame(PropertyCodec.enumeration(Color.class),
        PropertyCodec.enumeration(Color.class));
    }

    @Test
    void valueType() {
      assertEquals(Color.class,
        PropertyCodec.enumeration(Color.class).valueType());
    }
  }

  @Nested
  @DisplayName("of(Class, ...)")
  class CustomCodec {
    @Test
    void valueTypeIsProvidedClass() {
      final PropertyCodec<Float> codec = PropertyCodec.of(
        Float.class,
        s -> Float.parseFloat(s),
        Object::toString);
      assertEquals(Float.class, codec.valueType());
    }

    @Test
    void twoArgFactoryHasObjectType() {
      final PropertyCodec<Object> codec = PropertyCodec.of(
        s -> s, Object::toString);
      assertEquals(Object.class, codec.valueType());
    }
  }
}