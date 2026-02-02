package xyz.tcheeric.nostrdb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility class for loading native libraries from JAR resources.
 */
final class NativeLoader {

    private NativeLoader() {}

    /**
     * Load a native library from JAR resources.
     *
     * @param libraryName The library name (without lib prefix or extension)
     * @throws IOException if the library cannot be loaded
     */
    static void loadFromJar(String libraryName) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String libFileName;
        if (os.contains("linux")) {
            libFileName = "lib" + libraryName + ".so";
        } else if (os.contains("mac") || os.contains("darwin")) {
            libFileName = "lib" + libraryName + ".dylib";
        } else if (os.contains("win")) {
            libFileName = libraryName + ".dll";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + os);
        }

        // Try platform-specific path first, then generic
        String[] resourcePaths = {
            "/natives/" + os + "/" + arch + "/" + libFileName,
            "/natives/" + os + "/" + libFileName,
            "/natives/" + libFileName
        };

        for (String path : resourcePaths) {
            try (InputStream is = NativeLoader.class.getResourceAsStream(path)) {
                if (is != null) {
                    loadFromStream(is, libFileName);
                    return;
                }
            }
        }

        throw new IOException("Native library not found in JAR: " + libFileName +
            " (tried: " + String.join(", ", resourcePaths) + ")");
    }

    /**
     * Load a native library from an input stream.
     *
     * @param is The input stream containing the library
     * @param libFileName The library file name
     * @throws IOException if the library cannot be loaded
     */
    private static void loadFromStream(InputStream is, String libFileName) throws IOException {
        Path tempFile = null;
        try {
            // Create a temporary file
            tempFile = Files.createTempFile("nostrdb-", libFileName);
            tempFile.toFile().deleteOnExit();

            // Copy the library to the temp file
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Load the library
            System.load(tempFile.toAbsolutePath().toString());
        } catch (IOException | UnsatisfiedLinkError e) {
            // Clean up temp file on error
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best effort cleanup
                }
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to load native library", e);
        }
    }
}
