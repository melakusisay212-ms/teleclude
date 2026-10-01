# tele-expense-counter
Offline Ethiopian telecom expense tracker. SMS -> classify (COUNT/IGNORE/REFERENCE) -> extract -> Ethiopian date -> group -> dedupe -> expense.

## Status
- [x] `parser/` pure-Kotlin engine: rules, date extraction, Gregorian->Ethiopian, grouping/dedup, tests, dataset
- [x] `app/` Room DB, current-month scan, SMS receiver, first-run + Home (first pass)
- [x] Ethiopian calendar, analytics, details, edit/delete, period filters, dark mode, app icon (first pass, not compiled locally)

## Run tests
`gradle :parser:test` (JDK 17). No network needed at runtime; no logging of SMS bodies.

## Add a new SMS format
Add a `Rule` in `SmsParser.rules`, add a fixture + test. Order matters: specific paid rules before generic free/bonus.

## CI build (GitHub Actions)
`.github/workflows/build.yml` runs parser tests and builds the debug APK on every push/PR (or manually via *Actions → Build debug APK → Run workflow*). Download `tele-expense-debug-apk` from the run's **Artifacts** section. No Gradle wrapper is required (the workflow installs Gradle 8.9).
