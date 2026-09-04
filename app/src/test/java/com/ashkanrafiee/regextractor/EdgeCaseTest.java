package com.ashkanrafiee.regextractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Edge-case and cross-option coverage for {@link RegexBuilder}: every
 * category is exercised against a matrix of option combinations, and the
 * named-group and broad-mode behaviours are checked on their corner cases.
 *
 * <p>All expectations below were verified against the engine before being
 * written down; nothing here documents an accidental bug.</p>
 */
public class EdgeCaseTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static RegexBuilder.Options options(boolean broad, boolean named,
                                                boolean ci, boolean ml, boolean ds) {
        RegexBuilder.Options o = new RegexBuilder.Options();
        o.broadMatch = broad;
        o.namedGroups = named;
        o.caseInsensitive = ci;
        o.multiline = ml;
        o.dotAll = ds;
        return o;
    }

    private static String build(String example, RegexBuilder.Options o) {
        return RegexBuilder.build(Collections.singletonList(example), o);
    }

    private static int count(String pattern, String text) {
        Matcher m = Pattern.compile(pattern).matcher(text);
        int total = 0;
        while (m.find()) {
            total++;
            if (m.start() == m.end()) break; // safety against engine-injected empty matches
        }
        return total;
    }

    private static void assertCompiles(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (RuntimeException e) {
            fail("pattern does not compile: '" + pattern + "' (" + e.getMessage() + ")");
        }
    }

    /** Replicates {@code RegexBuilder.groupNameFor} so tests can predict the name. */
    private static String expectedName(String example) {
        boolean allDigits = true;
        boolean allLetters = true;
        for (int i = 0; i < example.length(); i++) {
            char c = example.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (!digit) allDigits = false;
            if (!letter) allLetters = false;
        }
        return (allDigits ? "number" : allLetters ? "word" : "value") + "1";
    }

    private static final String NAME = "[A-Za-z][A-Za-z0-9]*";

    private static Set<String> groupNames(String pattern) {
        Set<String> names = new HashSet<>();
        Matcher m = Pattern.compile("\\(\\?<(" + NAME + ")>").matcher(pattern);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    // ------------------------------------------------------------------
    // Category matrix: every category at every option combination
    // ------------------------------------------------------------------

    private static final Object[][] CATEGORIES = {
            {"john@example.com", "Contact john@example.com or jane.doe@example.org\nthen asomasdm@teasd.com and manbsmfnbamsdf.sadbasjahsdf@test.com", 4},
            {"$3.50", "Coffee $3.50, Sandwich $12.00, Juice $4.25", 3},
            {"2024-01-15", "issued 2024-05-01, due 2024-12-31, sent 2023-08-08", 3},
            {"user-101", "user-101 signed, team-42 merged, repo-7 archived", 3},
            {"16:45:02", "at 16:45:02, then 9:01:33 and 23:59:59", 3},
            {"(555) 123-4567", "(555) 123-4567 and (212) 987-6543", 2},
            {"https://x.co/a", "go to https://x.co/a or https://long.example.org/path now", 2},
            {"192.168.1.1", "ping 192.168.1.1 and 10.0.0.1 and 172.16.5.9", 3},
            {"v1.2.3", "v1.2.3 v10.20.30 v2.0", 3},
            {"42%", "42% 7% 100%", 3},
            {"#hello", "#hello #world #x", 3},
            {"report.pdf", "report.pdf house.txt notes.txt", 3},
            {"4111 1111 1111 1111", "4111 1111 1111 1111 and 4000 1234 5678 9010", 2},
            {"1,234", "1,234 99,999 7", 3},
            {"ERROR: test", "ERROR: test WARN: test INFO: test", 3},
    };

    @org.junit.Test
    public void categoryMatrixEveryOptionCombination() {
        for (Object[] cat : CATEGORIES) {
            String example = (String) cat[0];
            String text = (String) cat[1];
            int broadCount = (Integer) cat[2];
            // Two passes into broad mode: non-broad combos must match their own
            // example, broad combos must count every occurrence.
            for (boolean broad : new boolean[]{false, true}) {
                for (boolean named : new boolean[]{false, true}) {
                    for (boolean ci : new boolean[]{false, true}) {
                        RegexBuilder.Options o = options(broad, named, ci, true, true);
                        String pattern = build(example, o);
                        assertCompiles(pattern);
                        Matcher m = Pattern.compile(pattern).matcher(example);
                        assertTrue("'" + pattern + "' must match its own example '" + example + "'",
                                m.find());
                        assertEquals(expectedName(example), 1, m.groupCount());
                        if (named) {
                            assertTrue("named pattern must expose '" + expectedName(example) + "'",
                                    groupNames(pattern).contains(expectedName(example)));
                        }
                        if (broad) {
                            assertEquals("broad '" + example + "' => " + pattern,
                                    broadCount, count(pattern, text));
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Broad-mode edge cases
    // ------------------------------------------------------------------

    @org.junit.Test
    public void broadPureUppercaseIsCaseSensitiveAndAaOpensItUp() {
        RegexBuilder.Options o = options(true, false, false, false, false);
        String pattern = build("ERROR", o);
        assertEquals(1, count(pattern, "ERROR ok error")); // only the exact-case one
        o.caseInsensitive = true;
        assertEquals(3, count(build("ERROR", o), "ERROR ok error"));
    }

    @org.junit.Test
    public void broadIdGuardRejectsPlainWords() {
        String pattern = build("user-101", options(true, false, false, false, false));
        assertEquals(2, count(pattern, "user user-101 team-42"));
    }

    @org.junit.Test
    public void broadDigitlessHexColorIsExcludedByIdentifierGuard() {
        // #A-F without digits are ambiguous with plain words, so the
        // digit-guard deliberately leaves them out; #FF5733/#000000 match.
        String pattern = build("#FF5733", options(true, false, false, false, false));
        assertEquals(2, count(pattern, "#FF5733 and #000000 plus #aBcDeF"));
        RegexBuilder.Options ci = options(true, false, true, false, false);
        assertEquals(2, count(build("#FF5733", ci), "#FF5733 and #000000 plus #aBcDeF"));
    }

    @org.junit.Test
    public void broadUnderscoreFilenameFoldsIntoClass() {
        String pattern = build("report_final.pdf", options(true, false, false, false, false));
        assertEquals(3, count(pattern, "report_final.pdf mid_review.docx holiday_photo.jpg"));
    }

    @org.junit.Test
    public void broadCoordinatesIncludingNegative() {
        String pattern = build("-1.25", options(true, false, false, false, false));
        assertEquals(3, count(pattern, "a -1.25 b -0.5 c 3.14"));
    }

    @org.junit.Test
    public void broadDedupesIdenticalRendersToSingleNamedGroup() {
        RegexBuilder.Options o = options(true, true, false, false, false);
        String pattern = RegexBuilder.build(Arrays.asList("hello", "hi", "hello"), o);
        assertEquals(Collections.singleton("word1"), groupNames(pattern));
        assertEquals(1, Pattern.compile("\\(\\?<").matcher(pattern).results().count());
    }

    @org.junit.Test
    public void broadUnionOfSoftPunctuationSpansBothIdStyles() {
        // Selecting "user-101" and "team_42" together folds '-' and '_' into
        // the shared class of both alternation branches; each branch still
        // requires its own distinguishing char, and together they catch both
        // styles.
        RegexBuilder.Options o = options(true, false, false, false, false);
        String both = RegexBuilder.build(Arrays.asList("user-101", "team_42"), o);
        assertTrue("class must allow the dash", both.contains("-"));
        assertTrue("class must allow the underscore", both.contains("_"));
        assertEquals(2, Pattern.compile("\\|").matcher(both).results().count() + 1); // two branches
        assertEquals(3, count(both, "user-101 team-42 team_42"));
    }

    @org.junit.Test
    public void broadEmailDoesNotJumpAcrossWhitespace() {
        String pattern = build("john@example.com", options(true, false, false, false, false));
        assertEquals(0, count(pattern, "john@ ex ample.com"));
    }

    @org.junit.Test
    public void broadOnlyDigitsStillMatchAnyLengthRun() {
        String pattern = build("123", options(true, false, false, false, false));
        assertEquals(3, count(pattern, "123 456 7890"));
    }

    @org.junit.Test
    public void broadLargeDocumentCountsEveryOccurrence() {
        StringBuilder doc = new StringBuilder();
        for (int i = 0; i < 500; i++) doc.append("janedoe@example.com ");
        String pattern = build("a@example.com", options(true, false, false, false, false));
        assertCompiles(pattern);
        assertEquals(500, count(pattern, doc.toString()));
    }

    @org.junit.Test
    public void broadResultIsStableAcrossRepeatedCalls() {
        RegexBuilder.Options o = options(true, false, false, false, false);
        String p1 = build("user-101", o);
        String p2 = build("user-101", o);
        assertEquals(p1, p2);
        assertEquals(3, count(p1, "user-101 team-42 repo-7"));
    }

    // ------------------------------------------------------------------
    // Named-group edge cases
    // ------------------------------------------------------------------

    @org.junit.Test
    public void namedPreciseAlternationKeepsSameBaseBranchesUnique() {
        RegexBuilder.Options o = options(false, true, false, false, false);
        String pattern = RegexBuilder.build(Arrays.asList("a1", "2b"), o);
        assertEquals(
                new HashSet<>(Arrays.asList("value1", "value2")),
                groupNames(pattern));
        Matcher m = Pattern.compile(pattern).matcher("tap a1 then 2b");
        assertTrue(m.find());
        assertEquals("a1", m.group("value1"));
        assertTrue(m.find());
        assertEquals("2b", m.group("value2"));
    }

    @org.junit.Test
    public void namedPreciseWordNumberValueTrio() {
        RegexBuilder.Options o = options(false, true, false, false, false);
        String pattern = RegexBuilder.build(Arrays.asList("hi", "42", "a1"), o);
        assertEquals(
                new HashSet<>(Arrays.asList("word1", "number2", "value3")),
                groupNames(pattern));
        Matcher m = Pattern.compile(pattern).matcher("hi 42 a1");
        int found = 0;
        while (m.find()) found++;
        assertEquals(3, found);
    }

    @org.junit.Test
    public void namedAllFlagsWithAlternation() {
        RegexBuilder.Options o = options(false, true, true, true, true);
        String pattern = RegexBuilder.build(Arrays.asList("abc", "123"), o);
        assertTrue(pattern + " must carry inline flags", pattern.startsWith("(?ims)"));
        assertCompiles(pattern);
        Matcher m = Pattern.compile(pattern).matcher("ABC and 123");
        assertTrue(m.find());
    }

    @org.junit.Test
    public void namedFiveBranchesAreAllValidAndUnique() {
        RegexBuilder.Options o = options(false, true, false, false, false);
        String pattern = RegexBuilder.build(Arrays.asList("a1", "bb", "2024", "3.3", "v-9"), o);
        Set<String> names = groupNames(pattern);
        assertEquals(5, names.size());
        for (String name : names) assertTrue("invalid group name '" + name + "'", name.matches(NAME));
    }

    @org.junit.Test
    public void namedBroadAlternationUniquePerBranch() {
        RegexBuilder.Options o = options(true, true, false, false, false);
        String pattern = RegexBuilder.build(Arrays.asList("hello", "42"), o);
        assertEquals(
                new HashSet<>(Arrays.asList("word1", "number2")),
                groupNames(pattern));
        Matcher m = Pattern.compile(pattern).matcher("hello 42 7");
        int found = 0;
        while (m.find()) found++;
        assertEquals(3, found);
    }

    @org.junit.Test
    public void namedGroupRetrievesEveryBroadMatch() {
        RegexBuilder.Options o = options(true, true, false, false, false);
        String pattern = build("john@example.com", o);
        Matcher m = Pattern.compile(pattern).matcher(
                "one john@example.com two jane.doe@example.org three x@y.com");
        int found = 0;
        while (m.find()) {
            found++;
            assertNotNull("group value1 must be set", m.group("value1"));
        }
        assertEquals(3, found);
    }

    @org.junit.Test
    public void namedBaseReflectsContentAcrossPreciseAndBroad() {
        assertEquals("(?<number1>\\d{2})", build("42", options(false, true, false, false, false)));
        assertEquals("(?<word1>[a-z]{3})", build("abc", options(false, true, false, false, false)));
        assertEquals("(?<value1>[a-z]\\d)", build("a1", options(false, true, false, false, false)));
        assertEquals("(?<number1>(?=[0-9\\-._]*[A-Za-z0-9])[0-9\\-._]{1,})", build("31415", options(true, true, false, false, false)));
        assertEquals("(?<value1>(?=[0-9\\-._]*[A-Za-z0-9])[0-9\\-._]{1,})", build("3.14", options(true, true, false, false, false)));
        assertEquals("(?<word1>(?=[a-z\\-._]*[A-Za-z0-9])[a-z\\-._]{1,})", build("hello", options(true, true, false, false, false)));
    }

    @org.junit.Test
    public void namedWhitespaceExampleCompiles() {
        String pattern = build("  ", options(false, true, false, false, false));
        assertCompiles(pattern);
    }

    @org.junit.Test
    public void jsLiteralKeepsNamedGroupsAndFlags() {
        RegexBuilder.Options o = options(false, true, true, false, false);
        String pattern = build("a1", o);
        String literal = RegexBuilder.toJavascriptLiteral(pattern, true);
        assertTrue(literal, literal.contains("(?<value1>"));
        assertTrue(literal, literal.startsWith("/") || literal.contains("\\/"));
        assertTrue(literal, literal.endsWith("/ig"));
    }

    @org.junit.Test
    public void jsLiteralRoundtripFromBroadNamedBuild() {
        RegexBuilder.Options o = options(true, true, false, false, false);
        String pattern = build("john@example.com", o);
        String literal = RegexBuilder.toJavascriptLiteral(pattern, true);
        assertTrue(literal.contains("(?<value1>"));
        assertTrue(literal, literal.endsWith("/g"));
    }

    @org.junit.Test
    public void javaStringLiteralEscapesBackslashesInNamedPattern() {
        RegexBuilder.Options o = options(false, true, false, false, false);
        String pattern = build("42", o); // "(?<number1>\d{2})"
        String literal = RegexBuilder.toJavaStringLiteral(pattern);
        assertTrue(literal, literal.contains("(?<number1>\\\\d"));
        // Escaping round-trips through the original.
        String unescaped = literal
                .replace("\\\\", "\\")
                .replace("\\\"", "\"");
        assertEquals(pattern, unescaped.substring(1, unescaped.length() - 1));
    }

    @org.junit.Test
    public void preciseAndBroadPatternsDifferForSameExample() {
        String precise = build("john@example.com", options(false, false, false, false, false));
        String broad = build("john@example.com", options(true, false, false, false, false));
        assertNotEquals(precise, broad);
        assertTrue("broad must be flexible", broad.contains("{1,}"));
        assertTrue("precise must lock counts", precise.contains("{4}"));
    }
}