package com.ashkanrafiee.regextractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regression test suite for {@link RegexBuilder}.
 *
 * <p>Focuses on integration / end-to-end pipelines, edge-case inputs, and
 * specific scenarios that have historically been fragile. Each scenario is
 * exercised with at least three {@link RegexBuilder.Options} variants.</p>
 */
public class RegressionTest {

    // ====================================================================
    // Option variants reused across every regression scenario
    // ====================================================================

    private static RegexBuilder.Options plain() {
        return new RegexBuilder.Options();
    }

    private static RegexBuilder.Options caseInsensitive() {
        RegexBuilder.Options o = new RegexBuilder.Options();
        o.caseInsensitive = true;
        return o;
    }

    private static RegexBuilder.Options namedAll() {
        RegexBuilder.Options o = new RegexBuilder.Options();
        o.namedGroups = true;
        o.caseInsensitive = true;
        o.multiline = true;
        o.dotAll = true;
        return o;
    }

    private static List<RegexBuilder.Options> allVariants() {
        return Arrays.asList(plain(), caseInsensitive(), namedAll());
    }

    /** Build + compile + verify all examples match. */
    private static void assertPipeline(List<String> examples, List<RegexBuilder.Options> variants) {
        for (RegexBuilder.Options opts : variants) {
            String pattern = RegexBuilder.build(examples, opts);
            assertNotNull("pattern must not be null", pattern);
            assertCompiles(pattern);
            Pattern compiled = Pattern.compile(pattern);
            for (String ex : examples) {
                assertTrue("pattern '" + pattern + "' must match '" + ex + "'",
                        compiled.matcher(ex).find());
            }
        }
    }

    private static void assertCompiles(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (RuntimeException e) {
            fail("pattern does not compile: '" + pattern + "' (" + e.getMessage() + ")");
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) count++;
        return count;
    }

    // ====================================================================
    // 1. Integration-style full-pipeline tests
    // ====================================================================

    @org.junit.Test
    public void pipeline_dateExtraction() {
        List<String> examples = Arrays.asList("2024-01-15", "2023-12-08", "2025-07-04");
        assertPipeline(examples, allVariants());
        Pattern p = Pattern.compile(RegexBuilder.build(examples, plain()));
        Matcher m = p.matcher("invoice date 2025-07-04 paid");
        assertTrue(m.find());
        assertEquals("2025-07-04", m.group(1));
    }

    @org.junit.Test
    public void pipeline_emailPatterns() {
        List<String> examples = Arrays.asList("john@example.com", "jane.doe@example.org");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_errorLogLines() {
        List<String> examples = Arrays.asList("ERROR: disk full", "WARN: low memory");
        assertPipeline(examples, allVariants());
        Pattern p = Pattern.compile(RegexBuilder.build(examples, plain()));
        assertTrue(p.matcher("ERROR: disk full").find());
        assertTrue(p.matcher("WARN: low memory").find());
    }

    @org.junit.Test
    public void pipeline_currencyAmounts() {
        List<String> examples = Arrays.asList("$19.99", "$1,234.56");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_phoneNumbers() {
        List<String> examples = Arrays.asList("(555) 123-4567", "(212) 987-6543");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_urls() {
        List<String> examples = Arrays.asList("https://x.co/a", "https://very-long.host.io/path");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_timeFormats() {
        List<String> examples = Arrays.asList("16:45:02", "9:01:33");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_hexColors() {
        List<String> examples = Arrays.asList("#FF5733", "#000000", "#aBcDeF");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_percentages() {
        List<String> examples = Arrays.asList("100%", "87.5%", "12%");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_userAssignments() {
        List<String> examples = Arrays.asList("user=alice;", "user=bob;", "user=catherine;");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_windowsPaths() {
        List<String> examples = Arrays.asList(
                "C:\\Users\\ashkan\\file.txt",
                "D:\\backup\\old file.txt");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void pipeline_alternatingShapesFallBack() {
        // "hello" is all letters, "123" is all digits => incompatible shapes
        List<String> examples = Arrays.asList("hello", "123");
        assertPipeline(examples, allVariants());
        Pattern p = Pattern.compile(RegexBuilder.build(examples, plain()));
        Matcher m = p.matcher("say hello then 123");
        assertTrue(m.find());
        assertEquals("hello", m.group(1));
        assertTrue(m.find());
        assertEquals("123", m.group(2));
    }

    @org.junit.Test
    public void pipeline_namedGroupsOnAlternation() {
        List<String> examples = Arrays.asList("hello", "123");
        String pattern = RegexBuilder.build(examples, namedAll());
        assertCompiles(pattern);
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher("got hello and 42 and 123");
        assertTrue(m.find());
        assertEquals("hello", m.group("word1"));
        assertTrue(m.find());
        assertEquals("123", m.group("number2"));
    }

    @org.junit.Test
    public void pipeline_caseInsensitiveFullMatch() {
        RegexBuilder.Options opts = caseInsensitive();
        String pattern = RegexBuilder.build(Collections.singletonList("Status: OK"), opts);
        assertCompiles(pattern);
        Pattern p = Pattern.compile(pattern);
        assertTrue(p.matcher("Status: OK").find());
        assertTrue(p.matcher("status: ok").find());
        assertTrue(p.matcher("STATUS: OK").find());
    }

    @org.junit.Test
    public void pipeline_dotAllMultilineFlags() {
        RegexBuilder.Options opts = namedAll();
        String pattern = RegexBuilder.build(Collections.singletonList("abc def"), opts);
        assertTrue(pattern.startsWith("(?ims)"));
        assertCompiles(pattern);
        assertTrue(Pattern.compile(pattern).matcher("abc def").find());
    }

    // ====================================================================
    // 2. Regression: duplicate / identical examples
    // ====================================================================

    @org.junit.Test
    public void regression_manyIdenticalExamples() {
        List<String> examples = Collections.nCopies(50, "abc123");
        assertPipeline(examples, allVariants());
        String pattern = RegexBuilder.build(examples, plain());
        assertEquals("([a-z]{3}\\d{3})", pattern);
    }

    @org.junit.Test
    public void regression_twoIdenticalExamples() {
        List<String> examples = Arrays.asList("foo", "foo");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_identicalMixedExamples() {
        List<String> examples = Arrays.asList("AbC", "AbC", "AbC", "AbC");
        assertPipeline(examples, allVariants());
    }

    // ====================================================================
    // 3. Regression: very long strings
    // ====================================================================

    @org.junit.Test
    public void regression_veryLongDigits() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1500; i++) sb.append((char) ('0' + (i % 10)));
        String longDigits = sb.toString();
        String pattern = RegexBuilder.build(Collections.singletonList(longDigits), plain());
        assertCompiles(pattern);
        assertTrue(Pattern.compile(pattern).matcher(longDigits).find());
    }

    @org.junit.Test
    public void regression_veryLongLetters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1200; i++) sb.append((char) ('a' + (i % 26)));
        String longLetters = sb.toString();
        assertPipeline(Collections.singletonList(longLetters), allVariants());
    }

    @org.junit.Test
    public void regression_veryLongMixed() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            sb.append("abc");
            sb.append("123");
            sb.append(" ");
        }
        String longMixed = sb.toString();
        assertPipeline(Collections.singletonList(longMixed), allVariants());
    }

    @org.junit.Test
    public void regression_twoLongExamplesMerge() {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 500; i++) a.append('x');
        for (int i = 0; i < 800; i++) b.append('x');
        List<String> examples = Arrays.asList(a.toString(), b.toString());
        String pattern = RegexBuilder.build(examples, plain());
        assertCompiles(pattern);
        assertTrue(pattern.contains("{500,800}"));
        for (String ex : examples) {
            assertTrue(Pattern.compile(pattern).matcher(ex).find());
        }
    }

    // ====================================================================
    // 4. Regression: full alphabet and digit coverage
    // ====================================================================

    @org.junit.Test
    public void regression_all26Letters() {
        String all = "abcdefghijklmnopqrstuvwxyz";
        assertPipeline(Collections.singletonList(all), allVariants());
    }

    @org.junit.Test
    public void regression_all26LettersUppercase() {
        String all = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        assertPipeline(Collections.singletonList(all), allVariants());
    }

    @org.junit.Test
    public void regression_all26LettersBothCases() {
        String all = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz";
        assertPipeline(Collections.singletonList(all), allVariants());
    }

    @org.junit.Test
    public void regression_all10Digits() {
        String all = "0123456789";
        assertPipeline(Collections.singletonList(all), allVariants());
        String pattern = RegexBuilder.build(Collections.singletonList(all), plain());
        assertEquals("(\\d{10})", pattern);
    }

    // ====================================================================
    // 5. Regression: regex metacharacters
    // ====================================================================

    @org.junit.Test
    public void regression_allMetacharactersInSequence() {
        String specials = "\\.^$|?*+()[]{}";
        assertPipeline(Collections.singletonList(specials), allVariants());
        String pattern = RegexBuilder.build(Collections.singletonList(specials), plain());
        // Every metachar should be escaped in the pattern
        for (char c : specials.toCharArray()) {
            assertTrue("escaped char for '" + c + "' missing in pattern: " + pattern,
                    pattern.contains("\\" + c));
        }
    }

    @org.junit.Test
    public void regression_metacharactersEscapedInPattern() {
        // Backslash itself
        String backslash = "\\";
        String pattern = RegexBuilder.build(Collections.singletonList(backslash), plain());
        assertTrue(pattern.contains("\\\\"));
        assertCompiles(pattern);
        assertTrue(Pattern.compile(pattern).matcher(backslash).find());
    }

    @org.junit.Test
    public void regression_metacharactersWithFlags() {
        String specials = "^[\\]$";
        for (RegexBuilder.Options opts : allVariants()) {
            String pattern = RegexBuilder.build(Collections.singletonList(specials), opts);
            assertCompiles(pattern);
            assertTrue(Pattern.compile(pattern).matcher(specials).find());
        }
    }

    @org.junit.Test
    public void regression_leadingTrailingDollar() {
        List<String> examples = Arrays.asList("$hello$", "$world$");
        assertPipeline(examples, allVariants());
    }

    // ====================================================================
    // 6. Regression: Unicode
    // ====================================================================

    @org.junit.Test
    public void regression_arabicText() {
        String arabic = "\u0633\u0644\u0627\u0645"; // "salaam"
        assertPipeline(Collections.singletonList(arabic), allVariants());
        Pattern p = Pattern.compile(RegexBuilder.build(Collections.singletonList(arabic), plain()));
        assertTrue(p.matcher("prefix\u0633\u0644\u0627\u0645suffix").find());
    }

    @org.junit.Test
    public void regression_chineseText() {
        String chinese = "\u4f60\u597d\u4e16\u754c"; // "hello world"
        assertPipeline(Collections.singletonList(chinese), allVariants());
    }

    @org.junit.Test
    public void regression_cyrillicText() {
        String cyrillic = "\u041f\u0440\u0438\u0432\u0435\u0442"; // "Privet"
        assertPipeline(Collections.singletonList(cyrillic), allVariants());
    }

    @org.junit.Test
    public void regression_emojiText() {
        String emoji = "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02"; // several emoji
        assertPipeline(Collections.singletonList(emoji), allVariants());
    }

    @org.junit.Test
    public void regression_mixedUnicodeAndAscii() {
        String mixed = "hello\u00e9\u00e8\u00ea123"; // "hello" + accented e's + digits
        assertPipeline(Collections.singletonList(mixed), allVariants());
    }

    @org.junit.Test
    public void regression_persianDigits() {
        List<String> examples = Arrays.asList("\u06f1\u06f2:\u06f3\u06f0", "\u06f7:\u06f0\u06f5");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_euroSymbol() {
        String euro = "\u20AC1234";
        assertPipeline(Collections.singletonList(euro), allVariants());
        Pattern p = Pattern.compile(RegexBuilder.build(Collections.singletonList(euro), plain()));
        assertTrue(p.matcher("price \u20AC1234").find());
    }

    @org.junit.Test
    public void regression_cjkWithAscii() {
        String mixed = "\u65e5\u672c\u8a9e_test123";
        assertPipeline(Collections.singletonList(mixed), allVariants());
    }

    // ====================================================================
    // 7. Regression: single characters of each type
    // ====================================================================

    @org.junit.Test
    public void regression_singleLetter() {
        assertPipeline(Collections.singletonList("a"), allVariants());
        assertPipeline(Collections.singletonList("Z"), allVariants());
    }

    @org.junit.Test
    public void regression_singleDigit() {
        assertPipeline(Collections.singletonList("0"), allVariants());
        assertPipeline(Collections.singletonList("9"), allVariants());
    }

    @org.junit.Test
    public void regression_singleWhitespace() {
        assertPipeline(Collections.singletonList(" "), allVariants());
        assertPipeline(Collections.singletonList("\t"), allVariants());
        assertPipeline(Collections.singletonList("\n"), allVariants());
    }

    @org.junit.Test
    public void regression_singleSpecial() {
        assertPipeline(Collections.singletonList("."), allVariants());
        assertPipeline(Collections.singletonList("*"), allVariants());
        assertPipeline(Collections.singletonList("?"), allVariants());
        assertPipeline(Collections.singletonList("("), allVariants());
        assertPipeline(Collections.singletonList("["), allVariants());
        assertPipeline(Collections.singletonList("{"), allVariants());
    }

    // ====================================================================
    // 8. Regression: substring examples
    // ====================================================================

    @org.junit.Test
    public void regression_examplesThatAreSubstrings() {
        List<String> examples = Arrays.asList("a", "ab", "abc", "abcd");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_examplesThatAreSuffixes() {
        List<String> examples = Arrays.asList("xyz", "yz", "z");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_examplesSharedPrefix() {
        List<String> examples = Arrays.asList("abc111", "abc2222", "abc3");
        assertPipeline(examples, allVariants());
    }

    // ====================================================================
    // 9. Regression: whitespace edge cases
    // ====================================================================

    @org.junit.Test
    public void regression_leadingWhitespace() {
        List<String> examples = Arrays.asList("  hello", "  world");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_trailingWhitespace() {
        List<String> examples = Arrays.asList("hello  ", "world  ");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_leadingAndTrailingWhitespace() {
        List<String> examples = Arrays.asList("  hello  ", "  world  ");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_consecutiveWhitespace() {
        List<String> examples = Arrays.asList("a   b", "x     y");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_mixedWhitespaceTypes() {
        List<String> examples = Arrays.asList("a\tb", "c\nd", "e\rf");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void regression_onlyWhitespace() {
        assertPipeline(Collections.singletonList("   "), allVariants());
    }

    // ====================================================================
    // 10. Pattern validity regression: 20+ example sets
    // ====================================================================

    @org.junit.Test
    public void patternValidity_20Sets() {
        List<List<String>> sets = new ArrayList<>();
        sets.add(Arrays.asList("abc", "def"));
        sets.add(Arrays.asList("123", "456", "789"));
        sets.add(Arrays.asList("hello world", "foo bar"));
        sets.add(Arrays.asList("a1", "b2", "c3"));
        sets.add(Arrays.asList("AB-12", "CD-34", "EF-56"));
        sets.add(Arrays.asList("http://", "https://"));
        sets.add(Arrays.asList("one two", "three four"));
        sets.add(Arrays.asList("00:00", "23:59"));
        sets.add(Arrays.asList("yes", "no"));
        sets.add(Arrays.asList("aaa", "bb", "c"));
        sets.add(Arrays.asList("foo.bar", "baz.qux"));
        sets.add(Arrays.asList("+1", "+44", "+86"));
        sets.add(Arrays.asList("cat", "dog", "bird"));
        sets.add(Arrays.asList("1st", "2nd", "3rd"));
        sets.add(Arrays.asList("on", "off"));
        sets.add(Arrays.asList("red", "green", "blue"));
        sets.add(Arrays.asList("Asia/Tokyo", "Europe/London"));
        sets.add(Arrays.asList("N/A", "n/a"));
        sets.add(Arrays.asList("1,000", "2,000,000"));
        sets.add(Arrays.asList("a]b", "c]d"));
        sets.add(Arrays.asList("x}y", "z}w"));
        sets.add(Arrays.asList("(test)", "(data)"));
        sets.add(Arrays.asList(">=1.0", ">=2.0"));
        sets.add(Arrays.asList("a^b", "c^d"));
        sets.add(Arrays.asList("2024-01-15", "2023-12-08"));

        for (List<String> group : sets) {
            for (RegexBuilder.Options opts : allVariants()) {
                String pattern = RegexBuilder.build(group, opts);
                assertNotNull("null pattern for " + group, pattern);
                assertCompiles(pattern);
                Pattern compiled = Pattern.compile(pattern);
                for (String ex : group) {
                    assertTrue("pattern '" + pattern + "' must match '" + ex + "'",
                            compiled.matcher(ex).find());
                }
            }
        }
    }

    // ====================================================================
    // 11. Alternation regression
    // ====================================================================

    @org.junit.Test
    public void alternation_twoBranchesCaptureIndependently() {
        String pattern = RegexBuilder.build(Arrays.asList("hello", "123"), plain());
        Pattern p = Pattern.compile(pattern);
        // Use surrounding text that won't itself be matched by either branch
        Matcher m = p.matcher(">> hello << 123 >>");
        assertTrue(m.find());
        assertEquals("hello", m.group(1));
        assertEquals(null, m.group(2));
        assertTrue(m.find());
        assertEquals(null, m.group(1));
        assertEquals("123", m.group(2));
    }

    @org.junit.Test
    public void alternation_threeBranches() {
        List<String> examples = Arrays.asList("abc", "123", "   ");
        String pattern = RegexBuilder.build(examples, plain());
        assertCompiles(pattern);
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher("abc 123    end");
        assertTrue(m.find());
        assertEquals("abc", m.group(1));
        assertTrue(m.find());
        assertEquals("123", m.group(2));
        assertTrue(m.find());
        assertEquals("   ", m.group(3));
    }

    @org.junit.Test
    public void alternation_mixedShapesWithFlags() {
        for (RegexBuilder.Options opts : allVariants()) {
            String pattern = RegexBuilder.build(Arrays.asList("abc", "123"), opts);
            assertCompiles(pattern);
            assertTrue(Pattern.compile(pattern).matcher("abc").find());
            assertTrue(Pattern.compile(pattern).matcher("123").find());
            // "ab" is too short (2 letters, pattern needs 3) and "12" is too short (2 digits)
            assertFalse(Pattern.compile(pattern).matcher("ab").find());
            assertFalse(Pattern.compile(pattern).matcher("12").find());
        }
    }

    @org.junit.Test
    public void alternation_fourBranchesNamedGroups() {
        List<String> examples = Arrays.asList("aaa", "111", "   ", "...");
        String pattern = RegexBuilder.build(examples, namedAll());
        assertCompiles(pattern);
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher("aaa111   ...");
        assertTrue(m.find());
        assertEquals("aaa", m.group("word1"));
        assertTrue(m.find());
        assertEquals("111", m.group("number2"));
        assertTrue(m.find());
        assertEquals("   ", m.group("value3"));
        assertTrue(m.find());
        assertEquals("...", m.group("value4"));
    }

    @org.junit.Test
    public void alternation_eachBranchIndependentlyValid() {
        // Three branches with different shapes: letters, digits, whitespace+literal
        List<String> examples = Arrays.asList("hello", "12345", "a b c");
        for (RegexBuilder.Options opts : allVariants()) {
            String pattern = RegexBuilder.build(examples, opts);
            assertCompiles(pattern);
            for (String ex : examples) {
                assertTrue("'" + pattern + "' must match '" + ex + "'",
                        Pattern.compile(pattern).matcher(ex).find());
            }
        }
    }

    // ====================================================================
    // 12. Format conversion regression
    // ====================================================================

    @org.junit.Test
    public void toJavascriptLiteral_simplePattern() {
        assertEquals("/abc/", RegexBuilder.toJavascriptLiteral("abc", false));
        assertEquals("/abc/g", RegexBuilder.toJavascriptLiteral("abc", true));
    }

    @org.junit.Test
    public void toJavascriptLiteral_withInlineFlags() {
        assertEquals("/abc/i", RegexBuilder.toJavascriptLiteral("(?i)abc", false));
        assertEquals("/abc/im", RegexBuilder.toJavascriptLiteral("(?im)abc", false));
        assertEquals("/abc/ims", RegexBuilder.toJavascriptLiteral("(?ims)abc", false));
        assertEquals("/abc/ims", RegexBuilder.toJavascriptLiteral("(?ims)abc", false));
    }

    @org.junit.Test
    public void toJavascriptLiteral_flagsMovedAndGlobalAdded() {
        assertEquals("/abc/ig", RegexBuilder.toJavascriptLiteral("(?i)abc", true));
        assertEquals("/abc/img", RegexBuilder.toJavascriptLiteral("(?im)abc", true));
    }

    @org.junit.Test
    public void toJavascriptLiteral_slashesEscaped() {
        assertEquals("/a\\/b/g", RegexBuilder.toJavascriptLiteral("a/b", true));
        assertEquals("/http:\\/\\/x.co/g",
                RegexBuilder.toJavascriptLiteral("http://x.co", true));
    }

    @org.junit.Test
    public void toJavascriptLiteral_backslashPattern() {
        assertEquals("/\\d{4}/g", RegexBuilder.toJavascriptLiteral("\\d{4}", true));
        assertEquals("/\\d{4}/", RegexBuilder.toJavascriptLiteral("\\d{4}", false));
    }

    @org.junit.Test
    public void toJavascriptLiteral_namedGroups() {
        assertEquals("/(?<word1>[a-z]+)/g",
                RegexBuilder.toJavascriptLiteral("(?<word1>[a-z]+)", true));
    }

    @org.junit.Test
    public void toJavascriptLiteral_specialCharsInBody() {
        // Pattern with dots, pipes, dollar signs
        String pattern = "a\\.b|c\\$d";
        assertEquals("/a\\.b|c\\$d/g", RegexBuilder.toJavascriptLiteral(pattern, true));
    }

    @org.junit.Test
    public void toJavascriptLiteral_metacharacterSequence() {
        String pattern = "\\^\\$\\.\\|\\?\\*\\+";
        assertEquals("/\\^\\$\\.\\|\\?\\*\\+/g",
                RegexBuilder.toJavascriptLiteral(pattern, true));
    }

    @org.junit.Test
    public void toJavascriptLiteral_slashCount() {
        String literal = RegexBuilder.toJavascriptLiteral("a/b(?i)c/d", true);
        // Only two unescaped slashes should exist: the delimiters
        int unescapedSlashes = countOccurrences(literal, "/")
                - countOccurrences(literal, "\\/");
        assertEquals("should have exactly 2 delimiter slashes", 2, unescapedSlashes);
    }

    // --- toJavaStringLiteral ---

    @org.junit.Test
    public void toJavaStringLiteral_simplePattern() {
        assertEquals("\"abc\"", RegexBuilder.toJavaStringLiteral("abc"));
    }

    @org.junit.Test
    public void toJavaStringLiteral_backslashesDoubled() {
        assertEquals("\"\\\\d{4}\"", RegexBuilder.toJavaStringLiteral("\\d{4}"));
        assertEquals("\"\\\\s+\"", RegexBuilder.toJavaStringLiteral("\\s+"));
    }

    @org.junit.Test
    public void toJavaStringLiteral_quotesEscaped() {
        assertEquals("\"he said \\\"hi\\\" \\\\d\"",
                RegexBuilder.toJavaStringLiteral("he said \"hi\" \\d"));
    }

    @org.junit.Test
    public void toJavaStringLiteral_specialCharsAllTypes() {
        // Pattern containing backslash and regex metacharacters
        String pattern = "[\\]]^$|?*+(){}";
        String literal = RegexBuilder.toJavaStringLiteral(pattern);
        assertTrue(literal.startsWith("\""));
        assertTrue(literal.endsWith("\""));
        assertTrue("backslash should be doubled", literal.contains("\\\\"));
        // Verify round-trip: unquote to recover the original pattern
        String inner = literal.substring(1, literal.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        assertEquals(pattern, inner);
    }

    @org.junit.Test
    public void toJavaStringLiteral_emptyString() {
        assertEquals("\"\"", RegexBuilder.toJavaStringLiteral(""));
    }

    @org.junit.Test
    public void toJavaStringLiteral_onlyBackslash() {
        assertEquals("\"\\\\\"", RegexBuilder.toJavaStringLiteral("\\"));
    }

    @org.junit.Test
    public void toJavaStringLiteral_onlyQuote() {
        assertEquals("\"\\\"\"", RegexBuilder.toJavaStringLiteral("\""));
    }

    @org.junit.Test
    public void toJavaStringLiteral_allRegexMetacharacters() {
        String metas = "\\.^$|?*+()[]{}";
        String literal = RegexBuilder.toJavaStringLiteral(metas);
        // Verify round-trip: unquote to recover original pattern
        String inner = literal.substring(1, literal.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        assertEquals(metas, inner);
        // Verify the literal is a syntactically valid Java string literal
        assertTrue(literal.startsWith("\""));
        assertTrue(literal.endsWith("\""));
    }

    @org.junit.Test
    public void toJavaStringLiteral_roundtripWithPattern() {
        // Build a pattern and ensure its string literal is syntactically valid
        String pattern = RegexBuilder.build(
                Arrays.asList("foo", "bar"), plain());
        String literal = RegexBuilder.toJavaStringLiteral(pattern);
        assertTrue("literal starts with quote", literal.startsWith("\""));
        assertTrue("literal ends with quote", literal.endsWith("\""));
        // Remove quotes and verify it matches the original pattern
        String inner = literal.substring(1, literal.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        assertEquals(pattern, inner);
    }

    @org.junit.Test
    public void toJavascriptLiteral_roundtripWithPattern() {
        String pattern = RegexBuilder.build(
                Arrays.asList("abc", "def"), plain());
        String literal = RegexBuilder.toJavascriptLiteral(pattern, true);
        assertTrue(literal.startsWith("/"));
        int lastSlash = literal.lastIndexOf('/');
        assertTrue(lastSlash > 0);
        assertEquals("g", literal.substring(lastSlash + 1));
    }

    // ====================================================================
    // 13. Cross-option regression on edge-case inputs
    // ====================================================================

    @org.junit.Test
    public void crossOption_singleCharPerType() {
        List<String> examples = Arrays.asList("a", "0", " ", ".");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void crossOption_veryLongIdentical() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) sb.append('x');
        assertPipeline(Collections.singletonList(sb.toString()), allVariants());
    }

    @org.junit.Test
    public void crossOption_unicodeArabicDigits() {
        List<String> examples = Arrays.asList("\u06f1\u06f2:\u06f3\u06f0", "\u06f7:\u06f0\u06f5");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void crossOption_allMetacharacters() {
        String specials = "\\.^$|?*+()[]{}";
        assertPipeline(Collections.singletonList(specials), allVariants());
    }

    @org.junit.Test
    public void crossOption_substringExamples() {
        List<String> examples = Arrays.asList("a", "ab", "abc", "abcd", "abcde");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void crossOption_whitespaceEdges() {
        List<String> examples = Arrays.asList("  hi  ", "  there  ");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void crossOption_alternatingIncompatible() {
        List<String> examples = Arrays.asList("aaa", "111", "...", "   ");
        assertPipeline(examples, allVariants());
    }

    @org.junit.Test
    public void crossOption_emptyAndNullFiltered() {
        List<String> input = Arrays.asList(null, "", "abc", null, "", "abc");
        for (RegexBuilder.Options opts : allVariants()) {
            String pattern = RegexBuilder.build(input, opts);
            assertCompiles(pattern);
            assertTrue(Pattern.compile(pattern).matcher("abc").find());
        }
    }

    // ====================================================================
    // 14. Regression: edge cases that previously caused failures
    // ====================================================================

    @org.junit.Test
    public void regression_mergeFailsThenPerBranchIntegrity() {
        // First attempt: "ab-1" starts with [a-z]{2} but the second shape
        // "AB_2" also starts with [A-Z]{2}. They have the same token COUNT
        // (letter, literal, letter, literal, digit) but different literal
        // characters at positions 2 and 4, so merge fails.
        // Each branch must be rendered independently and not leak state.
        String pattern = RegexBuilder.build(Arrays.asList("ab-1", "AB_2"), plain());
        assertCompiles(pattern);
        Pattern p = Pattern.compile(pattern);
        assertTrue(p.matcher("ab-1").find());
        assertTrue(p.matcher("AB_2").find());
        assertFalse(p.matcher("ab_2").find());
        assertFalse(p.matcher("AB-1").find());
    }

    @org.junit.Test
    public void regression_mergeFailsThenPerBranchWithNamedGroups() {
        String pattern = RegexBuilder.build(
                Arrays.asList("ab-1", "AB_2"), namedAll());
        assertCompiles(pattern);
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher("ab-1 and AB_2");
        assertTrue(m.find());
        assertEquals("ab-1", m.group("value1"));
        assertTrue(m.find());
        assertEquals("AB_2", m.group("value2"));
    }

    @org.junit.Test
    public void regression_tripleAlternationAllCaptures() {
        List<String> examples = Arrays.asList("aaa", "111", "...");
        String pattern = RegexBuilder.build(examples, plain());
        assertCompiles(pattern);
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher("aaa111...");
        assertTrue(m.find());
        assertEquals("aaa", m.group(1));
        assertTrue(m.find());
        assertEquals("111", m.group(2));
        assertTrue(m.find());
        assertEquals("...", m.group(3));
    }

    @org.junit.Test
    public void regression_longStringAlternation() {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 500; i++) { a.append('a'); b.append('1'); }
        String pattern = RegexBuilder.build(Arrays.asList(a.toString(), b.toString()), plain());
        assertCompiles(pattern);
        assertTrue(Pattern.compile(pattern).matcher(a.toString()).find());
        assertTrue(Pattern.compile(pattern).matcher(b.toString()).find());
    }

    @org.junit.Test
    public void regression_identicalExamplesDontAlterOriginalShape() {
        // Running merge on duplicates must not leave shared mutable state
        // that would affect subsequent calls.
        List<String> examples = Arrays.asList("abc", "abc");
        String p1 = RegexBuilder.build(examples, plain());
        String p2 = RegexBuilder.build(examples, plain());
        assertEquals(p1, p2);
        assertEquals("([a-z]{3})", p1);
    }

    @org.junit.Test
    public void regression_multipleCallsProduceConsistentResults() {
        List<String> examples = Arrays.asList("2024-01-15", "2023-12-08");
        for (int i = 0; i < 10; i++) {
            String pattern = RegexBuilder.build(examples, plain());
            assertEquals("(\\d{4}-\\d{2}-\\d{2})", pattern);
        }
    }

    // ====================================================================
    // 15. Regression: format conversion with every special character type
    // ====================================================================

    @org.junit.Test
    public void formatConversion_toJS_everyCharType() {
        // toJavascriptLiteral only escapes forward slashes, not other metacharacters
        // Backslash: body \ → /\g
        assertEquals("/\\/g", RegexBuilder.toJavascriptLiteral("\\", true));
        // Pipe (not escaped by toJavascriptLiteral)
        assertEquals("/a|b/g", RegexBuilder.toJavascriptLiteral("a|b", true));
        // Dot (not escaped by toJavascriptLiteral)
        assertEquals("/a.b/g", RegexBuilder.toJavascriptLiteral("a.b", true));
        // Caret (not escaped by toJavascriptLiteral)
        assertEquals("/^a/g", RegexBuilder.toJavascriptLiteral("^a", true));
        // Dollar (not escaped by toJavascriptLiteral)
        assertEquals("/a$/g", RegexBuilder.toJavascriptLiteral("a$", true));
        // Asterisk (not escaped by toJavascriptLiteral)
        assertEquals("/a*b/g", RegexBuilder.toJavascriptLiteral("a*b", true));
        // Plus (not escaped by toJavascriptLiteral)
        assertEquals("/a+b/g", RegexBuilder.toJavascriptLiteral("a+b", true));
        // Question mark (not escaped by toJavascriptLiteral)
        assertEquals("/a?b/g", RegexBuilder.toJavascriptLiteral("a?b", true));
        // Parentheses (not escaped by toJavascriptLiteral)
        assertEquals("/(a)/g", RegexBuilder.toJavascriptLiteral("(a)", true));
        // Brackets (not escaped by toJavascriptLiteral)
        assertEquals("/[a]/g", RegexBuilder.toJavascriptLiteral("[a]", true));
        // Braces (not escaped by toJavascriptLiteral)
        assertEquals("/{a}/g", RegexBuilder.toJavascriptLiteral("{a}", true));
        // Slash IS escaped in JS literal
        assertEquals("/a\\/b/g", RegexBuilder.toJavascriptLiteral("a/b", true));
    }

    @org.junit.Test
    public void formatConversion_toJavaString_everyCharType() {
        // Backslash
        assertEquals("\"\\\\\"", RegexBuilder.toJavaStringLiteral("\\"));
        // Double quote
        assertEquals("\"\\\"\"", RegexBuilder.toJavaStringLiteral("\""));
        // Pipe (no special Java escaping needed)
        assertEquals("\"a|b\"", RegexBuilder.toJavaStringLiteral("a|b"));
        // Dot
        assertEquals("\"a.b\"", RegexBuilder.toJavaStringLiteral("a.b"));
        // Backslash + quote combined
        assertEquals("\"\\\\\\\"\"", RegexBuilder.toJavaStringLiteral("\\\""));
        // Parentheses
        assertEquals("\"(a)\"", RegexBuilder.toJavaStringLiteral("(a)"));
        // Brackets
        assertEquals("\"[a]\"", RegexBuilder.toJavaStringLiteral("[a]"));
        // Braces
        assertEquals("\"{a}\"", RegexBuilder.toJavaStringLiteral("{a}"));
    }

    @org.junit.Test
    public void formatConversion_toJS_patternFromBuild() {
        for (List<String> examples : Arrays.asList(
                Arrays.asList("abc", "def"),
                Arrays.asList("123", "456"),
                Arrays.asList("a.b", "c.d"))) {
            String pattern = RegexBuilder.build(examples, plain());
            String jsLiteral = RegexBuilder.toJavascriptLiteral(pattern, true);
            assertTrue(jsLiteral.startsWith("/"));
            assertTrue(jsLiteral.endsWith("g"));
            assertCompiles(pattern);
        }
    }

    @org.junit.Test
    public void formatConversion_toJavaString_patternFromBuild() {
        for (List<String> examples : Arrays.asList(
                Arrays.asList("abc", "def"),
                Arrays.asList("123", "456"),
                Arrays.asList("a\"b", "c\"d"))) {
            String pattern = RegexBuilder.build(examples, plain());
            String javaLiteral = RegexBuilder.toJavaStringLiteral(pattern);
            assertTrue(javaLiteral.startsWith("\""));
            assertTrue(javaLiteral.endsWith("\""));
            assertCompiles(pattern);
        }
    }

    // ====================================================================
    // 16. Regression: empty / null / whitespace-only edge input
    // ====================================================================

    @org.junit.Test(expected = IllegalArgumentException.class)
    public void regression_emptyListThrows() {
        RegexBuilder.build(Collections.emptyList(), plain());
    }

    @org.junit.Test(expected = IllegalArgumentException.class)
    public void regression_allNullsThrows() {
        RegexBuilder.build(Arrays.asList((String) null, null), plain());
    }

    @org.junit.Test(expected = IllegalArgumentException.class)
    public void regression_allEmptyThrows() {
        RegexBuilder.build(Arrays.asList("", "", ""), plain());
    }

    @org.junit.Test
    public void regression_nullsAndEmptiesWithValidEntry() {
        List<String> input = Arrays.asList(null, "", null, "hello", "", null);
        String pattern = RegexBuilder.build(input, plain());
        assertCompiles(pattern);
        assertTrue(Pattern.compile(pattern).matcher("hello").find());
    }

    // ====================================================================
    // 17. Regression: large number of examples
    // ====================================================================

    @org.junit.Test
    public void regression_manyDifferentExamplesSameShape() {
        List<String> examples = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            examples.add(String.format("%04d-%02d-%02d",
                    2000 + i % 20, 1 + i % 12, 1 + i % 28));
        }
        assertPipeline(examples, allVariants());
        // All 100 share the same shape, so they should merge
        String pattern = RegexBuilder.build(examples, plain());
        assertCompiles(pattern);
        Pattern p = Pattern.compile(pattern);
        for (String ex : examples) {
            assertTrue("pattern must match '" + ex + "'", p.matcher(ex).find());
        }
    }

    @org.junit.Test
    public void regression_manyAlternatingShapes() {
        List<String> examples = Arrays.asList(
                "aaa", "111", "...", "   ", "AAA", "000", "***", "\t\t\t");
        assertPipeline(examples, allVariants());
    }
}
