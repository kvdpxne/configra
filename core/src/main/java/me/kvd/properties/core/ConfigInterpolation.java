package me.kvd.properties.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.kvd.properties.exceptions.PropertyInterpolationException;
import me.kvd.properties.spi.node.DisabledPropertyNode;
import me.kvd.properties.spi.node.Node;
import me.kvd.properties.spi.node.PropertyNode;

/**
 * Resolves {@code ${key}} references in config values.
 *
 * <p>Public API: {@link #resolve(Map)} operates on an arbitrary
 * {@code Map<String, String>}. The package-private
 * {@link #apply(ConfigFile)} applies the same resolution to both active and
 * disabled nodes of a {@link ConfigFile}.</p>
 */
public final class ConfigInterpolation {

  private static final Pattern INTERPOLATION =
    Pattern.compile("\\$\\{([^}]+)}");
  private static final String INTERPOLATION_MARKER = "${";

  private ConfigInterpolation() {
    throw new AssertionError("No instances.");
  }

  /**
   * Resolves every {@code ${key}} reference in {@code source} and returns
   * an unmodifiable snapshot. Circular references and missing targets
   * raise {@link PropertyInterpolationException}.
   */
  public static Map<String, String> resolve(final Map<String, String> source) {
    Objects.requireNonNull(source, "source");
    final Map<String, String> resolved =
      new LinkedHashMap<>(source.size());
    final Set<String> visiting = new HashSet<>();
    for (final String key : source.keySet()) {
      resolveOne(key, source, resolved, visiting);
    }
    return Collections.unmodifiableMap(resolved);
  }

  static ConfigFile apply(final ConfigFile file) {
    final Map<String, String> combined = combinedSource(file);
    final Map<String, String> resolved = resolve(combined);
    return rewrite(file, resolved);
  }

  private static Map<String, String> combinedSource(final ConfigFile file) {
    final Map<String, String> combined =
      new LinkedHashMap<>(file.toRawMap());
    for (final Node node : file.nodes()) {
      if (node instanceof DisabledPropertyNode disabled) {
        combined.putIfAbsent(disabled.qualifiedKey(), disabled.value());
      }
    }
    return combined;
  }

  private static ConfigFile rewrite(final ConfigFile file,
                                    final Map<String, String> resolved) {
    final List<Node> updated = new ArrayList<>(file.nodes().size());
    for (final Node node : file.nodes()) {
      if (node instanceof PropertyNode property) {
        updated.add(new PropertyNode(
          property.section(),
          property.key(),
          resolved.get(property.qualifiedKey()),
          property.line())
        );
      } else if (node instanceof DisabledPropertyNode disabled) {
        updated.add(new DisabledPropertyNode(
          disabled.section(),
          disabled.key(),
          resolved.get(disabled.qualifiedKey()),
          disabled.commentPrefix(),
          disabled.spaceAfterPrefix(),
          disabled.line())
        );
      } else {
        updated.add(node);
      }
    }
    return new ConfigFile(updated, file.source(), file.format(),
      file.parseOptions());
  }

  private static String resolveOne(final String key,
                                   final Map<String, String> source,
                                   final Map<String, String> resolved,
                                   final Set<String> visiting) {
    final String cached = resolved.get(key);
    if (null != cached) {
      return cached;
    }
    if (!visiting.add(key)) {
      throw new PropertyInterpolationException(
        key, key,
        "Circular interpolation reference: " + key);
    }
    final String raw = source.get(key);
    if (null == raw) {
      throw new PropertyInterpolationException(
        key, key,
        "Missing property referenced by interpolation: " + key);
    }
    if (-1 == raw.indexOf(INTERPOLATION_MARKER)) {
      visiting.remove(key);
      resolved.put(key, raw);
      return raw;
    }
    final Matcher matcher = INTERPOLATION.matcher(raw);
    final StringBuilder out = new StringBuilder(raw.length());
    int last = 0;
    while (matcher.find()) {
      out.append(raw, last, matcher.start());
      final String refName = matcher.group(1).trim();
      if (!source.containsKey(refName)) {
        throw new PropertyInterpolationException(
          key, refName,
          "Missing property '" + refName
            + "' referenced from '" + key + '\'');
      }
      out.append(resolveOne(refName, source, resolved, visiting));
      last = matcher.end();
    }
    out.append(raw, last, raw.length());
    final String result = out.toString();
    visiting.remove(key);
    resolved.put(key, result);
    return result;
  }
}