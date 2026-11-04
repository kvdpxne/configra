# Configra

## Introduction

**Configra** is a modular, type-safe configuration library for JVM applications. It provides a format-agnostic node
graph (`ConfigFile`), immutable configuration instances, strongly typed `PropertyKey<T>` / `PropertyCodec<T>`
abstractions, and declarative schema support (`PropertySchema`, `EnumSchema`). Supported syntaxes are **INI** and
**properties**, implemented as pluggable `ConfigFormat` service providers via the `spi` module; additional formats can
be added by implementing the same interface. Optional extras add structural **diffing**, file **watching**, **overlay**
loading, and async I/O.

Both formats are supported because they are structurally near-identical key–value syntaxes; the essential difference is
that INI is partitioned into sections, whereas properties files are flat. This shared model lets a single core serve
both without duplicating the parser, writer, or node graph.

## The Problem

Configuration handling in JVM projects tends to accumulate as a patchwork of ad-hoc solutions rather than a deliberate
design. Values are read as raw strings and parsed at the call site, so casting, defaults, and error handling end up
scattered across the codebase, with each consumer re-implementing its own conventions. Libraries are typically bound to
a single syntax, which makes switching formats — or supporting several at once — expensive. Configuration objects are
frequently mutable and shared, giving rise to race conditions, silent overwrites, and unpredictable reloads. Validation
lives in imperative code instead of a single declaration, and once a file is loaded the application has no structural
awareness of it: detecting what changed, or reacting to an edit on disk, requires custom diffing and polling. INI
compounds this further, since case sensitivity, duplicate keys, nesting, and comment handling differ between parsers, so
the same file parsed by two libraries can yield different results.

- Stringly-typed access scattered across call sites.
- Format lock-in to a single syntax.
- Mutable, shared configuration state.
- Ad-hoc validation and defaults.
- No structural awareness after load.
- Inconsistent INI dialects.

## The Solution

Configra replaces this patchwork with explicit, composable abstractions. Typed access is declared once as a
`PropertyKey<T>` bound to a `PropertyCodec<T>`, making retrieval compile-time checked and surfacing parse failures as
typed exceptions. The core node graph is independent of any syntax, so format providers can be swapped behind the same
API — and because INI and properties share the same key–value model, differing essentially only in INI's section
partitioning, both are supported without duplicating core abstractions. Schemas express defaults and validation
declaratively, immutability makes instances safe to share, and the runtime concerns — diffing, watching, overlays, async
loading — are opt-in extras rather than core baggage. Dialect behaviour becomes explicit configuration instead of an
assumption.

- Typed `PropertyKey<T>` access instead of string parsing.
- Pluggable format providers behind a stable core API.
- Immutable `ConfigFile` instances instead of shared state.
- Declarative schemas instead of imperative validation.
- Built-in diffing and watching instead of custom loops.
- Explicit dialect configuration instead of guesswork.

## Quick Example

```java
import java.nio.file.Path;
import me.kvd.configra.PropertyKey;
import me.kvd.configra.core.ConfigFile;
import me.kvd.configra.format.ini.IniFormat;

Path path = Path.of("server.ini");
ConfigFile config = ConfigFile.load(path, IniFormat.INSTANCE);
PropertyKey<Integer> port = PropertyKey.integer("server.port", 8080);

int value = config.getOrElse(port); // 8080 — falls back to the key's default
```

## Features

The sections above describe the design; the list below summarises the concrete capabilities available to applications.

- **Typed property access** — `PropertyKey<T>` with an associated `PropertyCodec<T>`; retrieval is compile-time checked
  and decoded lazily.

- **Pluggable formats** — INI and properties ship as `ConfigFormat` providers; new formats implement the same interface.

- **INI dialect control** — Sections, `=` / `:` assignments, `;` / `#` comments, case-insensitive keys and sections,
  quoted values, inline comments, relative nesting (`[.subsection]`), indented continuation, duplicate policies, and
  key-style transformations.

- **Properties compatibility** — `java.util.Properties`-compatible parsing with escape handling, logical-line
  continuation, and disabled-property comments.

- **Fluent building** — `ConfigBuilder` with typed shortcuts (`setString`, `setInt`, `setEnum`, …), automatic comment
  prefixing, and optional blank-line separation.

- **Declarative schemas** — `PropertySchema` and `EnumSchema` for defaults and validation.

- **Diffing** — `ConfigDiff` reports added, removed, changed, and disabled keys between two configuration states.

- **Watching** — `PropertiesWatcher` in polling or event-driven mode with a change callback.

- **Flexible I/O** — `load`, `loadOrCreate`, `loadOverlay`, `loadAsync`, `peek`, `peekAll`, and classpath resource
  loading.

- **Interpolation** — `withInterpolation()` resolves `${…}` references within the node graph.

## Documentation

Usage instructions, dependency coordinates, module-by-module API notes, option semantics (`ParseOptions`,
`WriteOptions`), dialect behaviour, and worked examples are maintained in
the [project wiki](https://github.com/kvdpxne/configra/wiki). Start there if you want to use Configra or need more
detail on configuring it. This README is intentionally limited to an overview of the problem, the design, and the
feature set.

Configra targets JDK 25; the build uses the bundled Gradle wrapper, so no separate Gradle installation is required.

## License

This project is licensed under the **MIT License**. See
the [LICENSE](https://github.com/kvdpxne/configra/blob/main/LICENSE) file for details.
