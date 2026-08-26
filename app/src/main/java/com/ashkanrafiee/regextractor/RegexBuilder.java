package com.ashkanrafiee.regextractor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Infers a regular expression from example substrings of a sample text.
 *
 * <p>Each example is tokenized into maximal runs of one character class:
 * ASCII digits become {@code \d}, ASCII letters a case-aware {@code [a-z]},
 * {@code [A-Z]} or {@code [A-Za-z]} run, whitespace {@code \s}, and every
 * other character matches literally (escaped when it is a regex
 * metacharacter). Runs are then merged across examples: shapes with the
 * same run layout collapse into one pattern whose occurrence counts widen
 * into ranges ({@code {min,max}}), while incompatible shapes fall back to
 * an alternation of the individual patterns.</p>
 *
 * <p>The result is always ready to use for extraction: the matched content
 * lands in group 1 (each alternation branch in its own group), or in a
 * named group when {@link Options#namedGroups} is set. Helpers convert the
 * pattern into JavaScript literal or quoted string-literal form.</p>
 *
 * <p>This class is pure Java with no Android dependencies so it can be unit
 * tested on the JVM.</p>
 */
public final class RegexBuilder {

    /** Toggles that shape the generated expression. */
    public static final class Options {
        public boolean caseInsensitive;
        public boolean multiline;
        public boolean dotAll;
        public boolean namedGroups;

        /** Inline flag prefix such as {@code (?im)}, or "" when none apply. */
        public String flagPrefix() {
            StringBuilder flags = new StringBuilder();
            if (caseInsensitive) flags.append('i');
            if (multiline) flags.append('m');
            if (dotAll) flags.append('s');
            return flags.length() == 0 ? "" : "(?" + flags + ")";
        }
    }

    private RegexBuilder() {
    }

    /**
     * Builds a compilable pattern (including any inline flag prefix) that
     * matches every given example.
     *
     * @throws IllegalArgumentException when no non-empty example is supplied
     */
    public static String build(List<String> examples, Options options) {
        List<String> unique = dedupe(examples);
        if (unique.isEmpty()) throw new IllegalArgumentException("No examples to build from");

        List<List<Token>> shapes = new ArrayList<>();
        for (String example : unique) shapes.add(tokenize(example));

        List<Token> merged = tryMerge(shapes);
        String body;
        if (merged != null) {
            String rendered = render(merged, options);
            // Capture by default so matcher.group(1) yields the extracted
            // content straight away; named mode supplies its own group.
            body = options.namedGroups
                    ? "(?<" + groupNameFor(unique.get(0), 1) + ">" + rendered + ")"
                    : "(" + rendered + ")";
        } else {
            StringBuilder alternation = new StringBuilder();
            for (int i = 0; i < shapes.size(); i++) {
                if (i > 0) alternation.append('|');
                String part = render(shapes.get(i), options);
                alternation.append(options.namedGroups
                        ? "(?<" + groupNameFor(unique.get(i), i + 1) + ">" + part + ")"
                        : "(" + part + ")");
            }
            body = alternation.toString();
        }
        return options.flagPrefix() + body;
    }

    /**
     * Converts a pattern into a JavaScript regular expression literal such
     * as {@code /\d{4}/gi}: inline flags move to the suffix, slashes from
     * matched text are escaped, and {@code g} is appended when requested.
     */
    public static String toJavascriptLiteral(String pattern, boolean global) {
        String flags = "";
        String body = pattern;
        if (pattern.startsWith("(?")) {
            int i = 2;
            while (i < pattern.length() && isInlineFlag(pattern.charAt(i))) i++;
            if (i > 2 && i < pattern.length() && pattern.charAt(i) == ')') {
                flags = pattern.substring(2, i);
                body = pattern.substring(i + 1);
            }
        }
        if (global) flags = flags + 'g';
        return "/" + body.replace("/", "\\/") + "/" + flags;
    }

    /**
     * Quotes a pattern as a Java/Kotlin string literal, doubling backslashes
     * and escaping quotes so it can be pasted into source unchanged.
     */
    public static String toJavaStringLiteral(String pattern) {
        return '"' + pattern.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static boolean isInlineFlag(char c) {
        return c == 'i' || c == 'm' || c == 's';
    }

    // ------------------------------------------------------------------
    // Tokenization

    private static final int LITERAL = 0;
    private static final int DIGIT = 1;
    private static final int LETTER = 2;
    private static final int WHITESPACE = 3;

    private static final int CASE_MIXED = 0;
    private static final int CASE_LOWER = 1;
    private static final int CASE_UPPER = 2;

    /** One maximal run of a single character class with occurrence bounds. */
    private static final class Token {
        final int kind;
        final char literal; // meaningful only when kind == LITERAL
        int letterCase = CASE_MIXED; // meaningful only when kind == LETTER
        int min;
        int max;

        Token(int kind, char literal, int count) {
            this.kind = kind;
            this.literal = literal;
            this.min = count;
            this.max = count;
        }

        boolean sameClass(Token other) {
            return kind == other.kind && (kind != LITERAL || literal == other.literal);
        }

        void widen(Token other) {
            if (letterCase != other.letterCase) letterCase = CASE_MIXED;
            min = Math.min(min, other.min);
            max = Math.max(max, other.max);
        }

        /** Independent copy so widening a merge attempt never mutates the source tokenization. */
        Token copy() {
            Token clone = new Token(kind, literal, min);
            clone.max = max;
            clone.letterCase = letterCase;
            return clone;
        }
    }

    private static int classify(char c) {
        if (c >= '0' && c <= '9') return DIGIT;
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return LETTER;
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == 0x0B) return WHITESPACE;
        return LITERAL;
    }

    private static List<Token> tokenize(String s) {
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            int kind = classify(c);
            int end = i + 1;
            while (end < s.length()
                    && (kind == LITERAL ? s.charAt(end) == c : classify(s.charAt(end)) == kind)) end++;
            Token token = new Token(kind, c, end - i);
            if (kind == LETTER) {
                boolean hasLower = hasCase(s, i, end, false);
                boolean hasUpper = hasCase(s, i, end, true);
                token.letterCase = hasLower && hasUpper ? CASE_MIXED
                        : hasUpper ? CASE_UPPER : CASE_LOWER;
            }
            tokens.add(token);
            i = end;
        }
        return tokens;
    }

    private static boolean hasCase(String s, int start, int end, boolean upper) {
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            if (upper ? (c >= 'A' && c <= 'Z') : (c >= 'a' && c <= 'z')) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Merging

    /**
     * Merges shapes that share one run layout into tokens with widened
     * count ranges; returns null when layouts differ (caller falls back to
     * an alternation).
     *
     * <p>Compatibility is checked for every position before anything is
     * widened, and widening always operates on a {@link Token#copy()} of the
     * first shape's token. That keeps the original {@code shapes} lists
     * immutable, so a failed merge attempt can never leak partially-widened
     * state (e.g. a case flattened to mixed) into the per-example alternation
     * that the caller falls back to.</p>
     */
    private static List<Token> tryMerge(List<List<Token>> shapes) {
        int size = shapes.get(0).size();
        for (List<Token> shape : shapes) if (shape.size() != size) return null;

        for (int i = 0; i < size; i++) {
            Token first = shapes.get(0).get(i);
            for (List<Token> shape : shapes)
                if (!shape.get(i).sameClass(first)) return null;
        }

        List<Token> merged = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Token widened = shapes.get(0).get(i).copy();
            for (List<Token> shape : shapes) widened.widen(shape.get(i));
            merged.add(widened);
        }
        return merged;
    }

    // ------------------------------------------------------------------
    // Rendering

    private static String render(List<Token> tokens, Options options) {
        StringBuilder out = new StringBuilder();
        for (Token t : tokens) {
            out.append(atomOf(t, options)).append(quantifierOf(t));
        }
        return out.toString();
    }

    private static String atomOf(Token t, Options options) {
        switch (t.kind) {
            case DIGIT: return "\\d";
            case WHITESPACE: return "\\s";
            case LETTER:
                if (!options.caseInsensitive && t.letterCase == CASE_LOWER) return "[a-z]";
                if (!options.caseInsensitive && t.letterCase == CASE_UPPER) return "[A-Z]";
                return "[A-Za-z]";
            default: return escape(t.literal);
        }
    }

    private static String quantifierOf(Token t) {
        if (t.min == 1 && t.max == 1) return "";
        if (t.min == t.max) return "{" + t.min + "}";
        return "{" + t.min + "," + t.max + "}";
    }

    /** Escapes one character so it always matches itself. */
    private static String escape(char c) {
        switch (c) {
            case '\\': case '^': case '$': case '.': case '|':
            case '?': case '*': case '+': case '(': case ')':
            case '[': case ']': case '{': case '}':
                return "\\" + c;
            default: return String.valueOf(c);
        }
    }

    // ------------------------------------------------------------------
    // Named capture groups

    /**
     * Returns a group name reflecting the content of an example:
     * {@code number} for digit-only, {@code word} for letter-only text and
     * {@code value} otherwise, suffixed with the example's one-based index
     * so names stay unique within a pattern.
     */
    private static String groupNameFor(String example, int index) {
        boolean allDigits = true;
        boolean allLetters = true;
        for (int i = 0; i < example.length(); i++) {
            int kind = classify(example.charAt(i));
            if (kind != DIGIT) allDigits = false;
            if (kind != LETTER) allLetters = false;
        }
        String base = allDigits ? "number" : allLetters ? "word" : "value";
        return base + index;
    }

    // ------------------------------------------------------------------
    // Helpers

    private static List<String> dedupe(List<String> examples) {
        Set<String> seen = new HashSet<>();
        List<String> unique = new ArrayList<>();
        if (examples == null) return unique;
        for (String example : examples) {
            if (example != null && !example.isEmpty() && seen.add(example)) unique.add(example);
        }
        return unique;
    }
}
