package com.ashkanrafiee.regextractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests for {@link RegexBuilder.Options#broadMatch} (the "Similar" toggle).
 *
 * <p>Broad mode generalizes a single selected example into a category-level
 * pattern so the app captures every occurrence of the same kind in the text
 * (all emails, all dates, all IDs, all prices) instead of only the exact
 * selected strings.</p>
 */
public class BroadMatchTest {

    private static final String EMAILS =
            "Contact john@example.com for details\n"
                    + "or write to jane.doe@example.org instead.\n"
                    + "asomasdm@teasd.com\n"
                    + "manbsmfnbamsdf.sadbasjahsdf@test.com";

    private static RegexBuilder.Options broad() {
        RegexBuilder.Options o = new RegexBuilder.Options();
        o.broadMatch = true;
        return o;
    }

    private static String build(String... examples) {
        return RegexBuilder.build(Arrays.asList(examples), broad());
    }

    private static int count(String pattern, String text) {
        Pattern compiled = Pattern.compile(pattern);
        Matcher matcher = compiled.matcher(text);
        int total = 0;
        while (matcher.find()) {
            total++;
            if (matcher.start() == matcher.end()) break;
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

    // ------------------------------------------------------------------
    // Emails — the primary motivation for broad mode
    // ------------------------------------------------------------------

    @org.junit.Test
    public void singleEmailFindsEveryEmailInText() {
        String pattern = build("john@example.com");
        assertCompiles(pattern);
        assertEquals(4, count(pattern, EMAILS));
    }

    @org.junit.Test
    public void dottedLocalPartEmailAlsoFindsEverything() {
        String pattern = build("jane.doe@example.org");
        assertCompiles(pattern);
        assertEquals(4, count(pattern, EMAILS));
    }

    @org.junit.Test
    public void emailPatternStillCaptureGroupMatches() {
        String pattern = build("john@example.com");
        Matcher matcher = Pattern.compile(pattern).matcher(EMAILS);
        assertTrue(matcher.find());
        assertNotNull(matcher.group(1));
    }

    @org.junit.Test
    public void everySelectedEmailIsStillMatched() {
        for (String email : new String[]{"john@example.com", "jane.doe@example.org",
                "asomasdm@teasd.com", "manbsmfnbamsdf.sadbasjahsdf@test.com"}) {
            String pattern = build(email);
            Pattern compiled = Pattern.compile(pattern);
            assertTrue("broad pattern '" + pattern + "' must match its own example '" + email + "'",
                    compiled.matcher(email).find());
        }
    }

    @org.junit.Test
    public void singleEmailFindsHyphenatedAndUnderscoredLocalParts() {
        // Hyphen, underscore or plus in the local part are standard email
        // characters, so "all emails" must capture them fully — not truncate
        // at the joiner and steal the tail, and not require the user to select
        // a joined example first.
        String text = "Contact john@example.com for details\n"
                + "or write to jane.doe@example.org instead.\n"
                + "asomasdm@teasd.com\n"
                + "manbsmfnbamsdf.sadbasjahsdf@test.com\n"
                + "kjhasdkfjhaksjdf-ajhsdgf@test.com\n"
                + "kjhasdkfjhaksjdf_ajhsdgf@test.com\n"
                + "kjhasdkfjhaksjdf+ajhsdgf@test.com";
        String pattern = build("john@example.com");
        assertCompiles(pattern);
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        java.util.List<String> hits = new java.util.ArrayList<>();
        while (matcher.find()) hits.add(matcher.group(1));
        assertEquals(7, hits.size());
        assertTrue("full hyphenated local part must be captured, was '" + hits.get(4) + "'",
                hits.get(4).equals("kjhasdkfjhaksjdf-ajhsdgf@test.com"));
        assertTrue("full underscored local part must be captured, was '" + hits.get(5) + "'",
                hits.get(5).equals("kjhasdkfjhaksjdf_ajhsdgf@test.com"));
        assertTrue("full plus-addressed local part must be captured, was '" + hits.get(6) + "'",
                hits.get(6).equals("kjhasdkfjhaksjdf+ajhsdgf@test.com"));
    }

    @org.junit.Test
    public void doubleAtResolvesToTheWellFormedTail() {
        // "a@b@c" is malformed (an email has exactly one '@'). Like a trailing
        // slash or paren, the well-formed tail must be reported — not the
        // leftmost fragment sharing the separator, and never the domain shrunk
        // char-by-char to dodge the guard.
        String pattern = build("john@example.com");
        assertCompiles(pattern);
        Matcher m = Pattern.compile(pattern).matcher("kjhasdkfjhaksjdf@ajhsdgf@test.com");
        assertTrue(m.find());
        assertEquals("ajhsdgf@test.com", m.group(1));

        m = Pattern.compile(build("a@b")).matcher("a@b@c");
        assertTrue(m.find());
        assertEquals("b@c", m.group(1));

        m = Pattern.compile(build("a@b")).matcher("x@y@z");
        assertTrue(m.find());
        assertEquals("y@z", m.group(1));
    }

    @org.junit.Test
    public void hardDelimitersStayOutsideTheEmail() {
        // '/' and '(' are not email characters: a slash path after an email
        // is excluded, and a slash or paren jammed into a local part means the
        // real email is the tail after it — never an over-extended merge.
        String text = "kjhasdkfjhaksjdf-ajhsdgf@test.com/asdfasdf\n"
                + "kjhasdkfjhaksjdf/ajhsdgf@test.com\n"
                + "kjhasdkfjhaksjdf(ajhsdgf@test.com";
        String pattern = build("john@example.com");
        assertCompiles(pattern);
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        java.util.List<String> hits = new java.util.ArrayList<>();
        while (matcher.find()) hits.add(matcher.group(1));
        assertEquals(3, hits.size());
        assertTrue("path suffix must be excluded, was '" + hits.get(0) + "'",
                hits.get(0).equals("kjhasdkfjhaksjdf-ajhsdgf@test.com"));
        assertTrue("slash prefix must not merge, was '" + hits.get(1) + "'",
                hits.get(1).equals("ajhsdgf@test.com"));
        assertTrue("paren prefix must not merge, was '" + hits.get(2) + "'",
                hits.get(2).equals("ajhsdgf@test.com"));
    }

    // ------------------------------------------------------------------
    // Other categories (generic broad behavior)
    // ------------------------------------------------------------------

    @org.junit.Test
    public void singlePriceFindsAllPrices() {
        String text = "Coffee - $3.50\nSandwich - $12.00\nJuice - $4.25";
        String pattern = build("$3.50");
        assertCompiles(pattern);
        assertEquals(3, count(pattern, text));
    }

    @org.junit.Test
    public void singleDateFindsAllDates() {
        String text = "Invoice issued on 2024-05-01.\n"
                + "Payment due by 2024-12-31.\n"
                + "Reminder sent on 2023-08-08.";
        String pattern = build("2024-01-15");
        assertCompiles(pattern);
        assertEquals(3, count(pattern, text));
    }

    @org.junit.Test
    public void singleIdFindsOnlyIdsNotPlainWords() {
        String text = "user-101 signed up\nteam-42 merged\nrepo-7 archived";
        String pattern = build("user-101");
        assertCompiles(pattern);
        // Must find the three IDs and nothing else: no "signed", "up",
        // "merged" or "archived".
        assertEquals(3, count(pattern, text));
    }

    @org.junit.Test
    public void timePatternKeepsColonStructure() {
        String text = "at 16:45:02 then 9:01:33 and 23:59:59";
        String pattern = build("16:45:02");
        assertCompiles(pattern);
        assertEquals(3, count(pattern, text));
    }

    @org.junit.Test
    public void urlPatternKeepsSlashAndColonStructure() {
        String text = "go to https://x.co/a or https://long.example.org/path now";
        String pattern = build("https://x.co/a");
        assertCompiles(pattern);
        assertEquals(2, count(pattern, text));
    }

    @org.junit.Test
    public void phonePatternKeepsParentheses() {
        String text = "(555) 123-4567 and (212) 987-6543";
        String pattern = build("(555) 123-4567");
        assertCompiles(pattern);
        assertEquals(2, count(pattern, text));
    }

    @org.junit.Test
    public void pureWordBroadModeMatchesEveryWord() {
        String text = "Alice joined\nbob logged out";
        assertEquals(5, count(build("alice"), text));
    }

    // ------------------------------------------------------------------
    // Options interplay
    // ------------------------------------------------------------------

    @org.junit.Test
    public void broadMatchWithCaseInsensitiveFlag() {
        RegexBuilder.Options o = broad();
        o.caseInsensitive = true;
        String pattern = RegexBuilder.build(Collections.singletonList("Status: OK"), o);
        assertTrue(pattern.startsWith("(?i)"));
        assertCompiles(pattern);
        assertTrue(Pattern.compile(pattern).matcher("status: ok").find());
    }

    @org.junit.Test
    public void broadMatchWithNamedGroups() {
        RegexBuilder.Options o = broad();
        o.namedGroups = true;
        String pattern = RegexBuilder.build(Collections.singletonList("john@example.com"), o);
        assertCompiles(pattern);
        Matcher matcher = Pattern.compile(pattern).matcher(EMAILS);
        assertTrue(matcher.find());
        assertEquals("john@example.com", matcher.group("value1"));
    }

    @org.junit.Test
    public void matchedGroupNameReflectsContent() {
        String pattern = build("john@example.com");
        assertTrue(pattern.contains("@"));
        // digit-only content gets a number name in named mode
        RegexBuilder.Options o = broad();
        o.namedGroups = true;
        String digits = RegexBuilder.build(Collections.singletonList("20240115"), o);
        assertTrue(digits, digits.contains("number1"));
    }

    // ------------------------------------------------------------------
    // Broad alternation across structurally-different selections
    // ------------------------------------------------------------------

    @org.junit.Test
    public void structurallyDifferentExamplesCombineViaAlternation() {
        // One is a plain lowercase word, the other digits:
        String pattern = build("hello", "123");
        assertCompiles(pattern);
        assertTrue(pattern.contains("|"));
        assertTrue(Pattern.compile(pattern).matcher("say hello then 123").find());
    }

    @org.junit.Test
    public void exactModeStillExistsAndIsNarrow() {
        // Without broadMatch the same single example must stay narrow.
        RegexBuilder.Options plain = new RegexBuilder.Options();
        String narrow = RegexBuilder.build(Collections.singletonList("john@example.com"), plain);
        assertEquals(1, count(narrow, EMAILS));
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @org.junit.Test
    public void veryLongBroadExampleCompiles() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("x").append(i % 10);
        String pattern = build(sb.toString());
        assertCompiles(pattern);
        assertTrue(Pattern.compile(pattern).matcher(sb.toString()).find());
    }

    @org.junit.Test
    public void emptyInputStillRejected() {
        try {
            RegexBuilder.build(Collections.emptyList(), broad());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @org.junit.Test
    public void duplicatesAreIgnoredInBroadMode() {
        String pattern = build("abc@x.com", "abc@x.com", "abc@x.com");
        assertCompiles(pattern);
    }

    @org.junit.Test
    public void broadPatternsDoNotMatchUnrelatedText() {
        String pattern = build("john@example.com");
        String unrelated = "there are no emails here, just words and 123 numbers.";
        assertFalse(Pattern.compile(pattern).matcher(unrelated).find());
    }

    @org.junit.Test
    public void broadAllFlagsCombined() {
        RegexBuilder.Options o = broad();
        o.caseInsensitive = true;
        o.multiline = true;
        o.dotAll = true;
        String pattern = RegexBuilder.build(Collections.singletonList("john@example.com"), o);
        assertCompiles(pattern);
        assertEquals(4, count(pattern, EMAILS));
    }

    // ------------------------------------------------------------------
    // Special characters other than the soft joiners are hard anchors
    // ------------------------------------------------------------------

    @org.junit.Test
    public void hashStaysAnAnchorAndKeepsTheDigitGuard() {
        // "#tag42" must find "#tag7" but not a digit-free "#tag".
        String pattern = build("#tag42");
        assertCompiles(pattern);
        assertEquals(2, count(pattern, "#tag42 and #tag7 but #tag alone"));
    }

    @org.junit.Test
    public void starCurlsAreHardAnchors() {
        // Selecting "*bold*" generalizes the inner word only, and the
        // literal '*' must be regex-escaped.
        String pattern = build("*bold*");
        assertCompiles(pattern);
        assertTrue(pattern.contains("\\*"));
        assertEquals(2, count(pattern, "*bold* and *italic* but not bold"));
    }

    @org.junit.Test
    public void questionMarkIsEscapedAndAnchored() {
        // "what?" is the shape word+'?', so it finds "never?" too — this is
        // broad generalization at work; the literal '?' is regex-escaped.
        String pattern = build("what?");
        assertCompiles(pattern);
        assertTrue(pattern.contains("\\?"));
        assertEquals(2, count(pattern, "what? never?"));
    }

    @org.junit.Test
    public void apostrophePreservesWordShape() {
        // "don't" keeps the apostrophe as a structural anchor and flexes the
        // letters on both sides, so it matches other contractions.
        assertEquals(2, count(build("don't"), "don't won't cant"));
    }

    @org.junit.Test
    public void spacedOperatorsDoNotMergeAcrossWhitespace() {
        // Whitespace is the hardest boundary: "a != b" (spaced) must not
        // match the pattern built from "a!=b" (unspaced), and '&' must not
        // swallow "rock & roll" into one run.
        assertEquals(0, count(build("x|y"), "x | y"));
        assertEquals(0, count(build("a!=b"), "a != b"));
        assertEquals(0, count(build("A&B"), "rock & roll"));
    }
}