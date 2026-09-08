package com.seggellion.britannia_mod.client.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reading Java sources for the tests that assert things about the code itself.
 *
 * <p>{@link #withoutComments} exists because several of those assertions are about what the mod
 * <i>says to a player</i>, and a javadoc paragraph explaining why a string was removed contains
 * that string. Scanning raw text makes a test that fails on its own explanation, which is a test
 * that will be deleted rather than fixed.
 */
final class JavaSource {

    private JavaSource() {
    }

    static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    static final Path MOD = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");

    static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /** The file with every {@code //} and every block comment blanked out. */
    static String withoutComments(Path path) {
        return withoutComments(read(path));
    }

    /**
     * Blanks comments while leaving string literals intact, tracking which of the three states the
     * scanner is in. Length is not preserved and does not need to be -- every caller searches for
     * substrings rather than offsets.
     */
    static String withoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                } else if (c == '\n') {
                    out.append(c);
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < source.length()) out.append(source.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < source.length()) out.append(source.charAt(++i));
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            }
            out.append(c);
        }
        return out.toString();
    }

    static List<Path> javaFilesUnder(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot list " + root, e);
        }
    }

    static List<Path> modSources() {
        return javaFilesUnder(MOD);
    }

    /** The quest screens and their companions. */
    static List<Path> questScreenSources() {
        List<Path> found = new ArrayList<>();
        for (Path path : javaFilesUnder(MOD.resolve("client/gui"))) {
            if (path.getFileName().toString().startsWith("Quest")) found.add(path);
        }
        return found;
    }
}
