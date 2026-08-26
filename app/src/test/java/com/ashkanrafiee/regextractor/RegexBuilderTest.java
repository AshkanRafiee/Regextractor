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
import java.util.regex.Pattern;

/** Tests for the regex inference engine. */
public class RegexBuilderTest {

    private static String build(String... examples) {
        return RegexBuilder.build(Arrays.asList(examples), new RegexBuilder.Options());
    }

    // ------------------------------------------------------------------
    // Single-example tokenization

    @org.junit.Test
    public void lettersBecomeClassRuns() {
        assertEquals("[a-z]", build("a"));
        assertEquals("[a-z]{5}", build("hello"));
        assertEquals("[A-Z]{3}", build("ABC"));
        assertEquals("[A-Za-z]{10}", build("HelloWorld")); // mixed case in one run
    }

    @org.junit.Test
    public void digitsBecomeDigitRuns() {
        assertEquals("\\d", build("7"));
        assertEquals("\\d{5}", build("12345"));
    }

    @org.junit.Test
    public void whitespaceBecomesSpaceClass() {
        assertEquals("\\s", build(" "));
        assertEquals("\\s{4}", build("    "));
        assertEquals("\\s{3}", build("\t\n "));
    }

    @org.junit.Test
    public void mixedClassesConcatenate() {
        assertEquals("[a-z]{3}\\d{3}", build("abc123"));
        assertEquals("\\d{4}-\\d{2}-\\d{2}", build("2024-01-15"));
        assertEquals("[a-z]{3}\\s[a-z]{3}", build("abc def"));
    }

    @org.junit.Test
    public void caseInsensitiveFlattensLetterClasses() {
        RegexBuilder.Options options = new RegexBuilder.Options();
        options.caseInsensitive = true;
        assertEquals("(?i)[A-Za-z]{6}:\\s[A-Za-z]{2}",
                RegexBuilder.build(Collections.singletonList("Status: OK"), options));
        assertEquals("(?i)[A-Za-z]{5}",
                RegexBuilder.build(Collections.singletonList("hello"), options));
    }

    @org.junit.Test
    public void mixedCaseRunsMergeToCommonCase() {
        assertEquals("[A-Za-z]{3,5};", build("Alice;", "bob;"));
    }

    @org.junit.Test
    public void metacharactersAreEscaped() {
        String specials = "\\.^$|?*+()[]{}";
        StringBuilder expected = new StringBuilder();
        for (char c : specials.toCharArray()) expected.append('\\').append(c);
        assertEquals(expected.toString(), build(specials));
        assertEquals("<>", build("<>"));
        assertEquals("&=", build("&="));
    }

    @org.junit.Test
    public void controlCharactersMatchAsWhitespace() {
        assertEquals("\\s", build("\n"));
        assertEquals("\\s", build("\t"));
        assertEquals("\\s{2}", build("\r\n"));
        assertEquals("[a-z]\\s[a-z]", build("a\nb"));
    }

    @org.junit.Test
    public void nonAsciiCharactersMatchLiterally() {
        assertEquals("\u0633\u0644\u0627\u0645", build("\u0633\u0644\u0627\u0645")); // Persian "salaam"
        assertEquals("\u20AC\\d", build("\u20AC9")); // € followed by a digit
    }

    // ------------------------------------------------------------------
    // Multi-example merging

    @org.junit.Test
    public void identicalShapesWidenCounts() {
        assertEquals("\\d{4}-\\d{2}-\\d{2}",
                build("2024-01-15", "1999-12-31"));
    }

    @org.junit.Test
    public void differingRunLengthsProduceRanges() {
        assertEquals("[a-z]{3,5};", build("alice;", "bob;"));
        assertEquals("\\d{1,3}", build("1", "22", "333"));
    }

    @org.junit.Test
    public void threeExamplesMergeTogether() {
        assertEquals("[a-z]{2,5}:\\s\\d{1,4}",
                build("id: 42", "order: 7", "user: 2024"));
    }

    @org.junit.Test
    public void incompatibleShapesFallBackToAlternation() {
        assertEquals("(?:[a-z]{5})|(?:\\d{3})", build("hello", "123"));
    }

    @org.junit.Test
    public void duplicateExamplesAreIgnored() {
        assertEquals("[a-z]{3}", build("abc", "abc", "abc"));
    }

    @org.junit.Test
    public void nullAndEmptyExamplesAreDropped() {
        List<String> messy = Arrays.asList(null, "", "ok", null);
        assertEquals("[a-z]{2}", RegexBuilder.build(messy, new RegexBuilder.Options()));
    }

    // ------------------------------------------------------------------
    // Named groups

    @org.junit.Test
    public void namedGroupsReflectContent() {
        assertEquals("(?<number1>\\d{2})", build(true, "42"));
        assertEquals("(?<word1>[a-z]{5})", build(true, "hello"));
        assertEquals("(?<value1>[a-z]\\d)", build(true, "a1"));
    }

    @org.junit.Test
    public void namedGroupsNumberAcrossAlternatives() {
        assertEquals("(?<word1>[a-z]{2})|(?<number2>\\d{2})", build(true, "hi", "42"));
    }

    private static String build(boolean named, String... examples) {
        RegexBuilder.Options options = new RegexBuilder.Options();
        options.namedGroups = named;
        return RegexBuilder.build(Arrays.asList(examples), options);
    }

    // ------------------------------------------------------------------
    // Flags

    @org.junit.Test
    public void flagPrefixCombinesToggles() {
        RegexBuilder.Options none = new RegexBuilder.Options();
        assertEquals("", none.flagPrefix());

        RegexBuilder.Options ci = new RegexBuilder.Options();
        ci.caseInsensitive = true;
        assertEquals("(?i)", ci.flagPrefix());

        RegexBuilder.Options cim = new RegexBuilder.Options();
        cim.caseInsensitive = true;
        cim.multiline = true;
        assertEquals("(?im)", cim.flagPrefix());

        RegexBuilder.Options all = new RegexBuilder.Options();
        all.caseInsensitive = true;
        all.multiline = true;
        all.dotAll = true;
        assertEquals("(?ims)", all.flagPrefix());
    }

    @org.junit.Test
    public void flagsPrependToPattern() {
        RegexBuilder.Options options = new RegexBuilder.Options();
        options.caseInsensitive = true;
        assertEquals("(?i)[A-Za-z]{3}", RegexBuilder.build(Collections.singletonList("abc"), options));
    }

    // ------------------------------------------------------------------
    // Round-trip property: every example must match its own pattern

    @org.junit.Test
    public void everyExampleMatchesGeneratedPattern() {
        List<List<String>> cases = new ArrayList<>();
        cases.add(Arrays.asList("2024-01-15", "2023-12-08", "2025-07-04"));
        cases.add(Arrays.asList("john@example.com", "jane.doe@example.org"));
        cases.add(Arrays.asList("ERROR: disk full", "WARN: low memory"));
        cases.add(Arrays.asList("$19.99", "$1,234.56"));
        cases.add(Arrays.asList("user=alice;", "user=bob;", "user=catherine;"));
        cases.add(Arrays.asList("(555) 123-4567", "(212) 987-6543"));
        cases.add(Arrays.asList("https://x.co/a", "https://very-long.host.io/path"));
        cases.add(Arrays.asList("16:45:02", "9:01:33"));
        cases.add(Arrays.asList("\u06f1\u06f2:\u06f3\u06f0", "\u06f7:\u06f0\u06f5")); // Persian digits
        cases.add(Arrays.asList("C:\\Users\\ashkan\\file.txt", "D:\\backup\\old file.txt"));
        cases.add(Arrays.asList("#FF5733", "#000000", "#aBcDeF"));
        cases.add(Arrays.asList("100%", "87.5%", "12%"));

        for (List<String> group : cases) {
            for (RegexBuilder.Options options : optionVariants()) {
                String pattern = RegexBuilder.build(group, options);
                assertCompiles(pattern);
                Pattern compiled = Pattern.compile(pattern);
                for (String example : group) {
                    assertTrue("pattern '" + pattern + "' should match '" + example + "'",
                            compiled.matcher(example).find());
                }
            }
        }
    }

    @org.junit.Test
    public void generatedPatternsRejectNonExamples() {
        String dates = build("2024-01-15", "2023-12-08");
        assertFalse(Pattern.compile(dates).matcher("hello world").find());

        // letter runs generalize, but the digit count still has to fit {2,3}
        String ids = build("user-101", "user-27");
        assertFalse(Pattern.compile(ids).matcher("admin-1").find());
    }

    @org.junit.Test
    public void caseInsensitiveFlagIsEmittedAndStillMatches() {
        RegexBuilder.Options loose = new RegexBuilder.Options();
        loose.caseInsensitive = true;
        String relaxed = RegexBuilder.build(Collections.singletonList("Status: OK"), loose);
        assertTrue(relaxed.startsWith("(?i)"));
        assertCompiles(relaxed);
        assertTrue(Pattern.compile(relaxed).matcher("Status: OK").find());
    }

    // ------------------------------------------------------------------
    // Error handling

    @org.junit.Test
    public void emptyInputIsRejected() {
        assertRejected(Collections.emptyList());
        assertRejected(null);
        assertRejected(Arrays.asList("", ""));
        assertRejected(Arrays.asList((String) null));
    }

    private static void assertRejected(List<String> input) {
        try {
            RegexBuilder.build(input, new RegexBuilder.Options());
            fail("expected IllegalArgumentException for " + input);
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers

    private static void assertCompiles(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (RuntimeException e) {
            fail("generated pattern does not compile: '" + pattern + "' (" + e.getMessage() + ")");
        }
    }

    private static List<RegexBuilder.Options> optionVariants() {
        List<RegexBuilder.Options> variants = new ArrayList<>();
        RegexBuilder.Options plain = new RegexBuilder.Options();
        RegexBuilder.Options named = new RegexBuilder.Options();
        named.namedGroups = true;
        RegexBuilder.Options everything = new RegexBuilder.Options();
        everything.namedGroups = true;
        everything.caseInsensitive = true;
        everything.multiline = true;
        everything.dotAll = true;
        variants.add(plain);
        variants.add(named);
        variants.add(everything);
        return variants;
    }
}
