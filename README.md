# Regextractor

Regextractor is a small, offline-first Android app that builds regular
expressions from examples. Paste any text, highlight the parts you want to
capture, and the app infers a pattern that matches them — with a live preview
of every match.

## Features

- Infers a regex from one or more highlighted examples
- Smart generalization: letters, digits and whitespace become classes
  (`[a-z]`, `\d`, `\s`), occurrence counts widen into ranges (`{2,4}`) across
  examples, and incompatible shapes fall back to an alternation
- Case-aware letter classes by default; an *Aa* toggle flattens them to
  `[A-Za-z]` with an inline `(?i)`
- Live match preview over the sample text — tap any match to copy it
- Ready to use out of the box: every pattern captures its content in
  group 1 (named groups optional), so extraction works right after pasting
- Copy-ready output forms: plain pattern, JavaScript literal (`/…/g`)
  or quoted Java/Kotlin string literal (`"\\d{4}"`)
- Toggles for case-insensitive, multiline, dot-matches-all and find-all
- Optional named capture groups with content-based names
- Sample presets (log lines, dates, emails, prices, IDs)
- Share text into the app from other apps (SEND intent)
- Copy or share the finished pattern

## Privacy

Regextractor works fully offline. It declares no permissions — not even
`INTERNET` — performs no network requests, and keeps all text on the device.
No account, no analytics, no tracking.

## Build

```
bash ./gradlew assembleDebug
bash ./gradlew assembleRelease
```

The release build is unsigned when signing variables are absent, which is
suitable for source-based distribution builds such as F-Droid. For a locally
signed release, provide a keystore through environment variables:

```
REGEXTRACTOR_STORE_FILE=/path/to/regextractor-release.jks \
REGEXTRACTOR_STORE_TYPE=JKS \
REGEXTRACTOR_STORE_PASSWORD='...' \
REGEXTRACTOR_KEY_ALIAS='...' \
REGEXTRACTOR_KEY_PASSWORD='...' \
bash ./gradlew assembleRelease
```

A `signing.properties` file in the repository root takes precedence over the
environment variables if present. Keep keystores and passwords outside
version control.

## Tests

```
bash ./gradlew testDebugUnitTest
```

The inference engine (`RegexBuilder`) is pure Java without Android
dependencies, so its whole test suite runs on the JVM.

## Source

https://github.com/ashkanrafiee/regextractor

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
