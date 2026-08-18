package com.seggellion.britannia_mod.client.render;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

/**
 * Identity and failure-classification contract for NPC portraits.
 *
 * <p>Two separable things are pinned here. The first is that a portrait is addressed by the NPC's
 * <em>personal</em> name and nothing else: the nameplate now renders {@code "Drake the Bank Teller"}
 * for every Service NPC whose type publishes a display name, and the worry during the production
 * incident was that the widened nameplate had leaked into the portrait URL. It has not - the two
 * read different fields - and these tests fail loudly if that ever changes.
 *
 * <p>The second is that a failure can be told apart from another failure. Production gave us a 200
 * followed five seconds later by "Failed to download portrait? Drake", which was consistent with a
 * read timeout, a corrupt image and a texture-registration fault all at once, because the code
 * logged the same line for all three and logged nothing at all for a bad status.
 */
final class PortraitFetchTest {

    // ---------- Canonical identity ----------

    @Test
    void drakeIsAddressedByPersonalNameAlone() {
        assertEquals("https://storage.googleapis.com/ultimacraft/portraits/male/Drake.png",
                PortraitFetch.portraitUrl("Drake", "male"));
    }

    @Test
    void theComposedNameplateNeverReachesThePortraitUrl() {
        // Not an assertion about what the portrait path does with a title - it is an assertion that
        // the title and the personal name produce different URLs, so a regression that started
        // passing the nameplate through cannot go unnoticed.
        String personal = PortraitFetch.portraitUrl("Drake", "male");
        String composed = PortraitFetch.portraitUrl("Drake the Bank Teller", "male");

        assertAll(
                () -> assertFalse(personal.equals(composed),
                        "the composed nameplate must not resolve to the same portrait as the personal name"),
                () -> assertTrue(personal.endsWith("/Drake.png"), "expected /Drake.png, got " + personal),
                () -> assertTrue(composed.endsWith("/Drake_the_Bank_Teller.png"),
                        "a title that did leak through would be visible in the URL, got " + composed));
    }

    @Test
    void spacesBecomeUnderscoresAndGenderPicksTheFolder() {
        assertAll(
                () -> assertEquals("https://storage.googleapis.com/ultimacraft/portraits/female/Lady_Tori.png",
                        PortraitFetch.portraitUrl("Lady Tori", "female")),
                () -> assertEquals("https://storage.googleapis.com/ultimacraft/portraits/male/Drake.png",
                        PortraitFetch.portraitUrl("Drake", "MALE")));
    }

    @Test
    void anAbsentGenderFallsBackToTheUnknownFolderRatherThanAnEmptySegment() {
        assertAll(
                () -> assertEquals("https://storage.googleapis.com/ultimacraft/portraits/unknown/Drake.png",
                        PortraitFetch.portraitUrl("Drake", null)),
                () -> assertEquals("https://storage.googleapis.com/ultimacraft/portraits/unknown/Drake.png",
                        PortraitFetch.portraitUrl("Drake", "")));
    }

    @Test
    void theCacheKeyIsPerGenderSoTwoNpcsSharingANameDoNotCollide() {
        assertAll(
                () -> assertEquals("male_drake", PortraitFetch.cacheKey("Drake", "male")),
                () -> assertEquals("female_drake", PortraitFetch.cacheKey("Drake", "female")),
                () -> assertEquals("male_lady_tori", PortraitFetch.cacheKey("Lady Tori", "male")));
    }

    // ---------- Failure classification ----------

    @Test
    void aMissingPortraitIsReportedWithItsStatusInsteadOfSilently() {
        String line = PortraitFetch.describeFailure("Drake", "male", PortraitFetch.Stage.STATUS,
                404, 143L, null);

        assertAll(
                () -> assertTrue(line.contains("stage=STATUS"), line),
                () -> assertTrue(line.contains("httpStatus=404"), line),
                () -> assertTrue(line.contains("npc=Drake"), line),
                () -> assertTrue(line.contains("/male/Drake.png"), line),
                () -> assertTrue(line.contains("elapsedMs=143"), line));
    }

    @Test
    void aServerErrorIsReportedTheSameWay() {
        String line = PortraitFetch.describeFailure("Drake", "male", PortraitFetch.Stage.STATUS,
                500, 88L, null);

        assertAll(
                () -> assertTrue(line.contains("httpStatus=500"), line),
                () -> assertTrue(line.contains("stage=STATUS"), line));
    }

    @Test
    void aStalledBodyReadIsDistinguishableFromABadStatus() {
        // The production shape: status 200, then the read timeout expires.
        String line = PortraitFetch.describeFailure("Drake", "male", PortraitFetch.Stage.BODY_READ,
                200, 5079L, new SocketTimeoutException("Read timed out"));

        assertAll(
                () -> assertTrue(line.contains("stage=BODY_READ"), line),
                () -> assertTrue(line.contains("httpStatus=200"), line),
                () -> assertTrue(line.contains("elapsedMs=5079"), line),
                () -> assertTrue(line.contains("error=java.net.SocketTimeoutException"), line),
                () -> assertTrue(line.contains("message=Read timed out"), line));
    }

    @Test
    void aMalformedImageIsDistinguishableFromAStalledRead() {
        String line = PortraitFetch.describeFailure("Drake", "male", PortraitFetch.Stage.IMAGE_DECODE,
                200, 210L, new IOException("Invalid PNG signature"));

        assertAll(
                () -> assertTrue(line.contains("stage=IMAGE_DECODE"), line),
                () -> assertTrue(line.contains("error=java.io.IOException"), line),
                () -> assertFalse(line.contains("SocketTimeoutException"), line));
    }

    @Test
    void aTextureRegistrationFaultIsItsOwnStage() {
        String line = PortraitFetch.describeFailure("Drake", "male", PortraitFetch.Stage.TEXTURE_REGISTER,
                200, 260L, new IllegalStateException("Not on render thread"));

        assertTrue(line.contains("stage=TEXTURE_REGISTER"), line);
    }

    @Test
    void aRequestThatNeverGotAStatusSaysSoRatherThanClaimingZero() {
        String line = PortraitFetch.describeFailure("Drake", "male", PortraitFetch.Stage.STATUS,
                -1, 5001L, new SocketTimeoutException("connect timed out"));

        assertAll(
                () -> assertTrue(line.contains("httpStatus=unknown"), line),
                () -> assertFalse(line.contains("httpStatus=-1"), line));
    }

    @Test
    void anExceptionMessageStaysOnOneCappedLine() {
        // Cloud storage answers errors with XML/HTML bodies, and those reached the log verbatim.
        String sprawling = "line one\nline two\r\n   line three\t" + "x".repeat(500);
        String line = PortraitFetch.describeFailure("Drake", "male", PortraitFetch.Stage.BODY_READ,
                200, 12L, new IOException(sprawling));

        assertAll(
                () -> assertFalse(line.contains("\n"), "the log line must stay on one line"),
                () -> assertFalse(line.contains("\r"), "the log line must stay on one line"),
                () -> assertTrue(line.length() < 500, "expected a capped line, got " + line.length()));
    }

    @Test
    void anAbsentExceptionMessageDoesNotRenderAsNull() {
        assertEquals("(none)", PortraitFetch.concise(null));
        assertEquals("(none)", PortraitFetch.concise("   "));
    }
}
