package com.seggellion.britannia_mod.dye.colour;

import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import java.util.List;
import java.util.Objects;

/**
 * Common-side colour conversion and distance utilities.
 *
 * <p>The sRGB inverse transfer function follows IEC 61966-2-1 as reproduced by W3C CSS Color 4.
 * The linear-sRGB-to-OKLab matrices are Bjorn Ottosson's 2021-01-25 reference matrices:
 * https://bottosson.github.io/posts/oklab/ . Calculations use {@code double}; values are never
 * silently clamped.
 */
public final class ColourMath {
    public static final double COMPARISON_EPSILON = 1.0e-12;
    public static final double TIE_EPSILON = 1.0e-9;
    public static final double REFERENCE_TEST_TOLERANCE = 5.0e-9;
    public static final double AUTHORED_OKLAB_TOLERANCE = 5.0e-7;

    private ColourMath() {
    }

    public static SrgbColour parseCanonicalSrgb(String canonical) {
        String value = DataCodecs.requireCanonicalSrgb(canonical, "canonicalSrgb");
        return new SrgbColour(
                Integer.parseInt(value.substring(1, 3), 16),
                Integer.parseInt(value.substring(3, 5), 16),
                Integer.parseInt(value.substring(5, 7), 16));
    }

    public static double toLinearChannel(int channel) {
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException("sRGB channel must be between 0 and 255");
        }
        return toLinearChannel(channel / 255.0);
    }

    public static double toLinearChannel(double normalized) {
        if (!Double.isFinite(normalized) || normalized < 0.0 || normalized > 1.0) {
            throw new IllegalArgumentException("Normalized sRGB channel must be finite and between 0 and 1");
        }
        if (normalized <= 0.04045) {
            return normalized / 12.92;
        }
        return Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    public static LinearSrgb toLinearSrgb(SrgbColour colour) {
        Objects.requireNonNull(colour, "colour");
        return new LinearSrgb(toLinearChannel(colour.red()), toLinearChannel(colour.green()),
                toLinearChannel(colour.blue()));
    }

    public static OklabColour toOklab(String canonicalSrgb) {
        return toOklab(toLinearSrgb(parseCanonicalSrgb(canonicalSrgb)));
    }

    public static OklabColour toOklab(LinearSrgb colour) {
        Objects.requireNonNull(colour, "colour");
        double l = 0.4122214708 * colour.red() + 0.5363325363 * colour.green()
                + 0.0514459929 * colour.blue();
        double m = 0.2119034982 * colour.red() + 0.6806995451 * colour.green()
                + 0.1073969566 * colour.blue();
        double s = 0.0883024619 * colour.red() + 0.2817188376 * colour.green()
                + 0.6299787005 * colour.blue();

        double lRoot = signedCubeRoot(l);
        double mRoot = signedCubeRoot(m);
        double sRoot = signedCubeRoot(s);
        return new OklabColour(
                0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot,
                1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot,
                0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot);
    }

    public static OklabColour fromAuthored(List<Double> components) {
        List<Double> value = DataCodecs.requireOklab(components, "oklab");
        return new OklabColour(value.get(0), value.get(1), value.get(2));
    }

    public static double distance(OklabColour first, OklabColour second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        double deltaL = first.lightness() - second.lightness();
        double deltaA = first.a() - second.a();
        double deltaB = first.b() - second.b();
        double result = Math.sqrt(deltaL * deltaL + deltaA * deltaA + deltaB * deltaB);
        if (!Double.isFinite(result) || result < 0.0) {
            throw new IllegalArgumentException("OKLab distance must be finite and non-negative");
        }
        return result <= COMPARISON_EPSILON ? 0.0 : result;
    }

    public static double authoredDifference(String canonicalSrgb, List<Double> authored) {
        return distance(toOklab(canonicalSrgb), fromAuthored(authored));
    }

    public static boolean authoredMatches(String canonicalSrgb, List<Double> authored) {
        return authoredDifference(canonicalSrgb, authored) <= AUTHORED_OKLAB_TOLERANCE;
    }

    public static boolean distancesTie(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second) || first < 0.0 || second < 0.0) {
            throw new IllegalArgumentException("Compared distances must be finite and non-negative");
        }
        return Math.abs(first - second) <= TIE_EPSILON;
    }

    private static double signedCubeRoot(double value) {
        return Math.abs(value) <= COMPARISON_EPSILON ? 0.0 : Math.cbrt(value);
    }
}
