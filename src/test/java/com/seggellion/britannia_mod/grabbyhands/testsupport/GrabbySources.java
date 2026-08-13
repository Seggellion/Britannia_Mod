package com.seggellion.britannia_mod.grabbyhands.testsupport;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Helpers for the source-scan contract tests.
 *
 * <p>Several load-bearing properties of Grabby Hands are <em>absences</em> — it never calls
 * {@code setBlock}, never touches {@code BreakEvent}, never reads {@code placerUuid} for a use
 * decision, never declares a use predicate. An absence cannot be unit-tested behaviourally, so it is
 * enforced by scanning the sources.
 *
 * <p>Both helpers exist because a naive scan gives false answers in opposite directions:
 *
 * <ul>
 *   <li>{@link #stripComments} — documentation that <em>names</em> a forbidden symbol in order to
 *       explain why the code avoids it is not a violation. Four separate guards have tripped on their
 *       own doc comments.</li>
 *   <li>{@link #methodBody} — asserting that a file contains a call passes trivially when an
 *       unrelated declaration of the same name sits a few lines away. That exact mistake let a
 *       container-duplication bug through a green test until mutation testing exposed it.</li>
 * </ul>
 */
public final class GrabbySources {
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private GrabbySources() {
    }

    /**
     * Removes comments so a scan sees code only.
     *
     * <p>Crude but sufficient here: no Grabby source holds a string literal containing a comment
     * opener.
     */
    public static String stripComments(String source) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll(" ")).replaceAll(" ");
    }

    /**
     * The body of one method, so an assertion cannot be satisfied by an unrelated line elsewhere.
     *
     * @param signature enough of the declaration to identify it uniquely
     */
    public static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "method not found: " + signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, index + 1);
                }
            }
        }
        return "";
    }
}
