package com.seggellion.britannia_mod.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    void thePackagingGateRefusesADirtyBuildAndSaysHowToBuildOneOnPurpose() {
        // "A dirty build is not a release candidate" was prose in the release handoff and nothing
        // more: the gate required git.dirty to hold *some* value and then accepted `true`, so a
        // build from an uncommitted tree passed `check` and artifactIdentity printed "deploy this
        // one" beside its SHA-256. A jar built that way is already on disk in the canonical
        // checkout, carrying the version string that is in production.
        String gate = packagingGateBody();
        assertTrue(gate.contains("git.dirty") && gate.contains("'true'"),
                "verifyDeployableJar no longer compares git.dirty against 'true', so a build from "
                        + "an uncommitted tree is a release candidate again");
        assertTrue(gate.contains("git.dirty=true"),
                "the dirty-tree failure no longer says which property it is talking about, so an "
                        + "operator cannot tell what to fix");
        assertTrue(gate.contains("git status --porcelain"),
                "the dirty-tree failure no longer explains that git.dirty comes from "
                        + "`git status --porcelain` and therefore counts untracked files; that is "
                        + "the part nobody guesses, and it is why a build in the canonical checkout "
                        + "is dirty from the owner's untracked playbooks alone");
        // The rule must not leave a developer with no way to build. The opt-out is deliberate and
        // has to be typed, which is the whole point: -Pdev is not usable here, because it drops
        // GeckoLib and so fails this same gate on the bundled-dependency check first.
        assertTrue(gate.contains("project.hasProperty('allowDirty')"),
                "verifyDeployableJar has no -PallowDirty opt-out, so an ordinary local build from a "
                        + "dirty tree has no way through the gate at all");
        assertTrue(gate.contains("-PallowDirty"),
                "the dirty-tree failure does not name the -PallowDirty opt-out, so a developer is "
                        + "told to stop without being told how to proceed");
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
     *
     * <p>And when a build has <em>not</em> left jars behind -- a fresh clone, a clean CI runner, a
     * tree where only {@code test} has run -- this test is <strong>skipped</strong> rather than
     * passed. It used to iterate an empty list and report green, which claims more than it did: no
     * bytes were examined at all, on exactly the machines whose green run is quoted as evidence.
     * The guarantee lives in {@code verifyDeployableJar}, which inspects the real jar on every
     * {@code check}; this is a second opinion, and it now says so when it has nothing to look at.
     */
    @Test
    void anyBuiltDeployableJarBundlesItsRuntimeDependencies() throws IOException {
        for (Path jar : deployableJarsToInspect()) {
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
        // Deliberately no assumption about build/libs existing, unlike the test above: this one
        // asserts an absence, and a tree with nothing built genuinely satisfies it. There is no
        // vacuous pass to fix here -- the finding it guards is a file being present.
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

    private static String packagingGateBody() {
        int start = buildGradle.indexOf("tasks.register('verifyDeployableJar')");
        assertTrue(start >= 0, "the verifyDeployableJar packaging gate is gone");
        int end = buildGradle.indexOf("tasks.named('check')", start);
        return end < 0 ? buildGradle.substring(start) : buildGradle.substring(start, end);
    }

    /**
     * The built {@code -all} jars, or a skipped test.
     *
     * <p>An assumption rather than an assertion: a checkout with nothing built is a legitimate
     * state and a plain {@code gradlew test} on a fresh clone must not fail for it. Reporting the
     * caller as skipped is the honest outcome -- it names what was not available instead of
     * quietly claiming to have inspected bytes that were never there.
     */
    private static List<Path> deployableJarsToInspect() throws IOException {
        Path libs = PROJECT.resolve("build/libs").toAbsolutePath();
        assumeTrue(Files.isDirectory(libs),
                "nothing has been built in this tree (" + libs + " does not exist), so there are no "
                        + "artifact bytes to inspect. verifyDeployableJar in build.gradle is the "
                        + "gate that always runs against the real jar; this test is only a second "
                        + "opinion when a build has left one behind.");
        List<Path> jars = builtJars("-all.jar");
        assumeTrue(!jars.isEmpty(),
                libs + " holds no -all jar, so there is no deployable artifact to inspect. "
                        + "verifyDeployableJar in build.gradle is the gate that always runs against "
                        + "the real jar.");
        return jars;
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
