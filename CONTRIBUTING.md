# Contributing to flac-ffm

Thank you for your interest in contributing! This document explains how to get
started, the coding conventions we follow, and the process for submitting changes.

---

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 25+ |
| Maven | 3.9+ |
| libFLAC | 1.4+ (for integration tests) |
| Git | 2.x |

Install libFLAC on your platform:

```bash
# Debian/Ubuntu
sudo apt install libflac-dev

# Fedora/RHEL
sudo dnf install flac-devel

# macOS (Homebrew)
brew install flac
```

---

## Building

```bash
# Compile + unit tests (no libFLAC needed)
mvn test

# Full build with integration tests
mvn verify -Pintegration

# Skip tests entirely
mvn package -DskipTests
```

---

## Code Conventions

### General

- **Java 25** — use records, sealed classes, pattern matching, and text blocks where they improve clarity.
- **No checked exceptions in the public API** — throw sub-types of `FlacException` (all `RuntimeException`).
- **AutoCloseable** — any class that wraps a native resource must implement `AutoCloseable`.
- **Double-close safety** — `close()` must be idempotent (second call silently ignored).
- **Null handling** — validate all public method arguments; throw `NullPointerException` or `IllegalArgumentException` with a descriptive message.

### Naming

| Element | Convention | Example |
|---|---|---|
| Raw FFM layer | `*H` suffix | `StreamDecoderH`, `MetadataH` |
| High-level wrapper | No suffix | `FlacStreamDecoder` |
| Method handle fields | `MH_<snake_name>` | `MH_init_file` |
| Callback FDs | `FD_<UPPER_NAME>_CALLBACK` | `FD_WRITE_CALLBACK` |
| Native C constants | All-caps with `FLAC__` prefix | `FLAC__MAX_CHANNELS` |
| Java enum mirrors | All-caps inner class fields | `MetadataType.STREAMINFO` |

### FFM Specifics

- **Every `MethodHandle` invocation** must be wrapped in a `try/catch (Throwable)` that rethrows as `AssertionError`.  Checked exceptions from `invokeExact` are impossible given our `FunctionDescriptor`s, but the compiler still requires the catch.
- **`FLAC__bool`** maps to `JAVA_INT` (`ValueLayout.JAVA_INT`), not `JAVA_BYTE`.
- **Upcall stubs** must be created in a confined `Arena` owned by the wrapper object, and freed when the wrapper is closed.
- **Keep strong references** to `MemorySegment` upcall stubs; the GC will otherwise collect them while the native code still holds a pointer.

### Javadoc

- Every public class and method must have a Javadoc comment.
- Use `@param`, `@return`, `@throws`, and `@since` tags.
- Reference libFLAC C symbols with inline code: `` {@code FLAC__stream_decoder_init_file} ``.
- Link to related Java types with `{@link}`.

---

## Testing

### Unit tests (no libFLAC needed)

Put pure-Java tests (testing validation logic, exception hierarchy, format constants, etc.)
in `src/test/java/org/xiph/flac/` without extending `IntegrationTestBase`.

```bash
mvn test
```

### Integration tests (libFLAC required)

Extend `IntegrationTestBase` and call `assumeIntegration()` at the start of each
test method. The `@BeforeAll` in `IntegrationTestBase` skips the class automatically
when `flac.test.integration` is not `true`.

```bash
mvn test -Pintegration
# or
mvn test -Dflac.test.integration=true
```

### Writing a good test

1. **Name** — `<what>_<condition>_<expectedOutcome>`, e.g. `initFile_nonExistentPath_throwsInitException`.
2. **Arrange** — set up data and objects.
3. **Act** — call the method under test.
4. **Assert** — use JUnit 5 `assertThrows`, `assertEquals`, `assertTrue`, `assertFalse`, `assertDoesNotThrow`.
5. **Independence** — each test must be self-contained; never depend on another test's side effects.

---

## Adding a New Binding

When a new libFLAC symbol needs to be exposed:

1. **`*H` class** — add `private static final MethodHandle MH_xxx` with the correct `FunctionDescriptor`, then a `public static` method that calls it.
2. **High-level wrapper** (if applicable) — add a method that delegates to `*H`, including null checks, `checkOpen()`, and appropriate exception translation.
3. **Test** — add at least one integration test exercising the new binding.
4. **Javadoc** — document all new methods in both the `*H` class and the wrapper.
5. **CHANGELOG** — add an entry under the upcoming version.

---

## Pull Request Process

1. Fork the repository and create a feature branch: `git checkout -b feat/my-feature`.
2. Make your changes following the conventions above.
3. Run `mvn verify -Pintegration` and ensure all tests pass.
4. Squash trivial fixup commits: `git rebase -i main`.
5. Open a pull request against `main` with:
   - A clear title summarising the change.
   - A description explaining *why* the change is needed.
   - Reference to any related issues.

---

## Reporting Bugs

Open a GitHub issue with:
- The libFLAC version (`flac --version`).
- The JDK version (`java -version`).
- The OS and architecture.
- A minimal reproducer (ideally a failing unit test).
