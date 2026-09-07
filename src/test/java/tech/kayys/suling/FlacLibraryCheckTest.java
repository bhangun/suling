package tech.kayys.suling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FlacLibraryCheck}.
 *
 * <p>These tests verify the version detection and availability checking
 * functionality without requiring actual audio processing.</p>
 */
@DisplayName("FlacLibraryCheck Tests")
class FlacLibraryCheckTest {

    @Test
    @DisplayName("isAvailable() should return consistent result")
    void testIsAvailableConsistency() {
        // Call multiple times to ensure consistency
        boolean first = FlacLibraryCheck.isAvailable();
        boolean second = FlacLibraryCheck.isAvailable();
        boolean third = FlacLibraryCheck.isAvailable();

        assertEquals(first, second, "isAvailable() should return consistent results");
        assertEquals(second, third, "isAvailable() should return consistent results");
    }

    @Test
    @DisplayName("getVersion() should return valid version string when available")
    void testGetVersion() {
        String version = FlacLibraryCheck.getVersion();

        if (FlacLibraryCheck.isAvailable()) {
            assertNotNull(version, "Version should not be null when library is available");
            assertFalse(version.isEmpty(), "Version should not be empty when library is available");
            assertNotEquals("not loaded", version, "Version should not be 'not loaded' when available");

            // Version should match pattern "major.minor.patch" or similar
            assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*") || "unknown".equals(version),
                    "Version should match 'major.minor.patch' format or be 'unknown': " + version);
        } else {
            assertEquals("not loaded", version, "Version should be 'not loaded' when unavailable");
        }
    }

    @Test
    @DisplayName("getVersionMajor() should return non-negative when available")
    void testGetVersionMajor() {
        int major = FlacLibraryCheck.getVersionMajor();

        if (FlacLibraryCheck.isAvailable()) {
            assertTrue(major >= 0, "Major version should be non-negative");
        } else {
            assertEquals(0, major, "Major version should be 0 when unavailable");
        }
    }

    @Test
    @DisplayName("getVersionMinor() should return non-negative when available")
    void testGetVersionMinor() {
        int minor = FlacLibraryCheck.getVersionMinor();

        if (FlacLibraryCheck.isAvailable()) {
            assertTrue(minor >= 0, "Minor version should be non-negative");
        } else {
            assertEquals(0, minor, "Minor version should be 0 when unavailable");
        }
    }

    @Test
    @DisplayName("getVersionPatch() should return non-negative when available")
    void testGetVersionPatch() {
        int patch = FlacLibraryCheck.getVersionPatch();

        if (FlacLibraryCheck.isAvailable()) {
            assertTrue(patch >= 0, "Patch version should be non-negative");
        } else {
            assertEquals(0, patch, "Patch version should be 0 when unavailable");
        }
    }

    @Test
    @DisplayName("isVersionAtLeast() should handle various version requirements")
    void testIsVersionAtLeast() {
        if (!FlacLibraryCheck.isAvailable()) {
            // When unavailable, all version checks should return false
            assertFalse(FlacLibraryCheck.isVersionAtLeast(0, 0, 1),
                    "Should not have any version if unavailable");
            assertFalse(FlacLibraryCheck.isVersionAtLeast(999, 999, 999),
                    "Should not have version 999.999.999");
            return;
        }

        // When available but version unknown (0.0.0), even low version checks might fail
        // This is acceptable - the library is usable even if version detection fails
        boolean hasVeryOld = FlacLibraryCheck.isVersionAtLeast(0, 0, 1);
        
        // If version is detected, should pass; if not detected (0.0.0), might fail
        // The important thing is that the library is available
        if (FlacLibraryCheck.getVersionMajor() > 0 || FlacLibraryCheck.getVersionMinor() > 0
                || FlacLibraryCheck.getVersionPatch() > 0) {
            assertTrue(hasVeryOld, "Should have at least version 0.0.1 if version is detected");
        }

        // Test with impossibly high version - should fail
        boolean hasImpossible = FlacLibraryCheck.isVersionAtLeast(999, 999, 999);
        assertFalse(hasImpossible, "Should not have version 999.999.999");
    }

    @Test
    @DisplayName("isVersionAtLeast() should be consistent with individual getters")
    void testIsVersionAtLeastConsistency() {
        if (!FlacLibraryCheck.isAvailable()) {
            // All checks should return false when unavailable
            assertFalse(FlacLibraryCheck.isVersionAtLeast(1, 0, 0));
            return;
        }

        int major = FlacLibraryCheck.getVersionMajor();
        int minor = FlacLibraryCheck.getVersionMinor();
        int patch = FlacLibraryCheck.getVersionPatch();

        // Should have at least the detected version
        assertTrue(FlacLibraryCheck.isVersionAtLeast(major, minor, patch),
                "Should have at least the detected version");

        // Should not have version higher than detected
        assertFalse(FlacLibraryCheck.isVersionAtLeast(major + 1, 0, 0),
                "Should not have major version + 1");
    }

    @Test
    @DisplayName("getLoadSource() should return descriptive string")
    void testGetLoadSource() {
        String source = FlacLibraryCheck.getLoadSource();

        assertNotNull(source, "Load source should not be null");
        assertFalse(source.isEmpty(), "Load source should not be empty");

        if (FlacLibraryCheck.isAvailable()) {
            assertNotEquals("not loaded", source,
                    "Load source should not be 'not loaded' when available");
            // Should contain some descriptive text
            assertTrue(source.length() > 5,
                    "Load source should be descriptive: " + source);
        } else {
            assertEquals("not loaded", source,
                    "Load source should be 'not loaded' when unavailable");
        }
    }

    @Test
    @DisplayName("getDiagnostics() should return comprehensive information")
    void testGetDiagnostics() {
        String diagnostics = FlacLibraryCheck.getDiagnostics();

        assertNotNull(diagnostics, "Diagnostics should not be null");
        assertFalse(diagnostics.isEmpty(), "Diagnostics should not be empty");
        assertTrue(diagnostics.length() > 20,
                "Diagnostics should be comprehensive: " + diagnostics);

        // Should contain key sections
        assertTrue(diagnostics.contains("libFLAC"),
                "Diagnostics should mention libFLAC");

        if (FlacLibraryCheck.isAvailable()) {
            assertTrue(diagnostics.contains("Available") || diagnostics.contains("OK"),
                    "Diagnostics should indicate availability");
        } else {
            assertTrue(diagnostics.contains("NOT AVAILABLE") || diagnostics.contains("Troubleshooting"),
                    "Diagnostics should provide troubleshooting when unavailable");
        }
    }

    @Test
    @DisplayName("Version components should be logically consistent")
    void testVersionConsistency() {
        if (!FlacLibraryCheck.isAvailable()) {
            return;
        }

        int major = FlacLibraryCheck.getVersionMajor();
        int minor = FlacLibraryCheck.getVersionMinor();
        int patch = FlacLibraryCheck.getVersionPatch();
        String version = FlacLibraryCheck.getVersion();

        // If we have a version string, it should match the components
        if (!"unknown".equals(version)) {
            assertTrue(version.startsWith(major + "."),
                    "Version string should start with major.minor");
        }

        // Version numbers should be reasonable
        assertTrue(major >= 0 && major < 100,
                "Major version should be reasonable: " + major);
        assertTrue(minor >= 0 && minor < 100,
                "Minor version should be reasonable: " + minor);
        assertTrue(patch >= 0 && patch < 1000,
                "Patch version should be reasonable: " + patch);
    }
}
