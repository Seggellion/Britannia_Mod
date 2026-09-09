package com.seggellion.britannia_mod.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The deployable artifact has to be the obvious one.
 *
 * <p>Two jars land in {@code build/libs/}, and only one of them can be deployed. The other bundles
 * no dependencies, and because {@code neoforge.mods.toml} never declares GeckoLib, nothing at load
 * time objects: a server given the wrong file starts clean, runs, and then throws
 * {@link NoClassDefFoundError} at the first animated render, minutes later, with nothing in the
 * stack trace about packaging. Before this test the two files differed only by a classifier that
 * the dependency-free one did not have -- so the unclassified name, the one an operator reads as
 * "the release", belonged to the file that cannot be released.
 *
 * <p>{@code verifyDeployableJar} in {@code build.gradle} is the gate that inspects the real bytes,
 * and {@code check} depends on it. This test guards the arrangement itself: that the Jar-in-Jar
 * declarations are still there, that the gate is still wired into {@code check}, that no jar is
 * produced without a classifier, and that the documentation still names the file operators are
 * meant to copy. Those are the things that can rot silently between releases, because none of them
 * breaks a build when they go wrong.
 */
class DeployableArtifactPackagingTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final String JARJAR_PREFIX = "META-INF/jarjar/";

    private static String buildGradle;
    private static String readme;
    private static String modsTomlTemplate;
    private static Properties gradleProperties;

    @BeforeAll
    static void readProjectFiles() throws IOException {
        buildGradle = read(PROJECT.resolve("build.gradle"));
        readme = read(PROJECT.resolve("README.md"));
        modsTomlTemplate = read(PROJECT.resolve("src/main/templates/META-INF/neoforge.mods.toml"));
        gradleProperties = new Properties();
        try (InputStream in = Files.newInputStream(PROJECT.resolve("gradle.properties"))) {
            gradleProperties.load(in);
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String modVersion() {
        return gradleProperties.getProperty("mod_version");
    }

    // ---------------------------------------------------------------- the declarations

    @Test
    void jarInJarStillEmbedsGeckoLibAndNanohttpd() {
        assertTrue(buildGradle.contains("jarJar \"software.bernie.geckolib:geckolib-neoforge-"),
                "build.gradle no longer embeds GeckoLib via Jar-in-Jar");
        assertTrue(buildGradle.contains("jarJar \"org.nanohttpd:nanohttpd:2.2.0\""),
                "build.gradle no longer embeds nanohttpd via Jar-in-Jar");
    }

    @Test
    void packagingGateIsRegisteredAndWiredIntoCheck() {
        assertTrue(buildGradle.contains("tasks.register('verifyDeployableJar')"),
                "the verifyDeployableJar packaging gate is gone");
        int gate = buildGradle.indexOf("tasks.named('check')");
        assertTrue(gate >= 0, "nothing configures the check task");
        assertTrue(buildGradle.substring(gate).contains("verifyDeployableJar"),
                "verifyDeployableJar is no longer reached by `check`, so nothing invokes it");
    }

    @Test
    void theIdentityReportCannotVouchForAnUnverifiedJar() {
        // artifactIdentity prints "deploy this one" beside a SHA-256. That reads as proof, so it
        // must not be printable for a jar the gate has not passed.
        assertTrue(buildGradle.contains(
                        "tasks.named('jar'), tasks.named('jarJar'), tasks.named('verifyDeployableJar')"),
                "artifactIdentity no longer depends on verifyDeployableJar, so it can vouch for an "
                        + "artifact nothing checked");
    }

    @Test
    void theDependencyFreeJarDoesNotHoldTheUnclassifiedName() {
        assertTrue(buildGradle.contains("archiveClassifier = 'thin'"),
                "the thin jar has taken back the unclassified filename, which is the one an "
                        + "operator reads as the release even though it cannot be deployed");
    }

    @Test
    void modMetadataInterpolatesTheVersionRatherThanHardCodingIt() {
        assertTrue(modsTomlTemplate.contains("version=\"${mod_version}\""),
                "neoforge.mods.toml hard-codes a version, so the jar can disagree with gradle.properties");
        assertFalse(modsTomlTemplate.contains("version=\"0."),
                "neoforge.mods.toml carries a literal version number");
    }

    // ---------------------------------------------------------------- the documentation

    @Test
    void readmeNamesTheVersionThatGradleActuallyBuilds() {
        assertTrue(readme.contains(modVersion()),
                "README does not mention mod version " + modVersion()
                        + "; the release note and gradle.properties have drifted apart");
    }

    @Test
    void readmeNamesTheDeployableArtifactWithItsRealFilename() {
        // The base name is lower case: `britannia_mod-<version>-all.jar`. The README named
        // `Britannia_Mod-<version>-all.jar` for a long time, which matches no file Gradle produces.
        assertTrue(readme.contains("britannia_mod-<version>-all.jar"),
                "README no longer names the deployable artifact by its real filename");
        assertFalse(readme.contains("Britannia_Mod-<version>-all.jar"),
                "README names a capitalised jar that Gradle never produces");
    }

    // ---------------------------------------------------------------- the produced bytes

    /**
     * The bytes check duplicated from {@code verifyDeployableJar}, so that a plain {@code gradlew
     * test} against an already-built tree still catches a hollow artifact. The Gradle task is the
     * gate that cannot be skipped; this runs whenever a build has left jars behind.
     */
    @Test
    void anyBuiltDeployableJarBundlesItsRuntimeDependencies() throws IOException {
        for (Path jar : builtJars("-all.jar")) {
            Set<String> embedded = jarJarEntries(jar);
            assertTrue(embedded.stream().anyMatch(name -> name.startsWith("geckolib-neoforge-")),
                    jar.getFileName() + " bundles no GeckoLib: " + embedded);
            assertTrue(embedded.contains("nanohttpd-2.2.0.jar"),
                    jar.getFileName() + " bundles no nanohttpd: " + embedded);
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                assertTrue(zip.getEntry(JARJAR_PREFIX + "metadata.json") != null,
                        jar.getFileName() + " has no Jar-in-Jar metadata, so NeoForge would ignore "
                                + "the embedded jars");
                Properties info = new Properties();
                ZipEntry entry = zip.getEntry("britannia_mod_build.properties");
                assertTrue(entry != null, jar.getFileName() + " carries no build provenance");
                try (InputStream in = zip.getInputStream(entry)) {
                    info.load(in);
                }
                for (String key : List.of("git.head", "git.branch", "git.dirty", "build.timestamp")) {
                    String value = info.getProperty(key, "");
                    assertFalse(value.isBlank() || value.equals("unknown"),
                            jar.getFileName() + " provenance " + key + " is '" + value + "'");
                }
            }
        }
    }

    @Test
    void noBuiltJarCarriesTheUnclassifiedName() throws IOException {
        List<Path> unclassified = new ArrayList<>();
        for (Path jar : builtJars(".jar")) {
            String name = jar.getFileName().toString();
            if (name.equals("britannia_mod-" + modVersion() + ".jar")) {
                unclassified.add(jar);
            }
        }
        assertEquals(List.of(), unclassified,
                "an unclassified jar is back in build/libs; it is the name an operator reaches for "
                        + "and it is not the deployable artifact");
    }

    private static Set<String> jarJarEntries(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith(JARJAR_PREFIX) && name.endsWith(".jar"))
                    .map(name -> name.substring(JARJAR_PREFIX.length()))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static List<Path> builtJars(String suffix) throws IOException {
        Path libs = PROJECT.resolve("build/libs");
        if (!Files.isDirectory(libs)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(libs)) {
            return files.filter(path -> path.getFileName().toString().endsWith(suffix)).toList();
        }
    }
}
