# Agent rules — Shared Expenses App

Rules for any AI agent (Claude Code, Copilot agent mode, Cursor, Gemini CLI)
and for humans working in this repository. Required by the course AI policy in
`README.md`; CI fails if this file is missing.

## Project

Native Android app (Kotlin, Gradle) for tracking shared expenses in groups.
See `README.md` for the functional requirements.

The graded quality attribute is **Reliability**, which means three things must
hold in the code and be covered by tests:

1. **Offline availability** — previously retrieved data stays readable with no
   network.
2. **Background retry** — a request that fails on a transient network error is
   retried in the background, and never blocks the user from continuing.
3. **Request status feedback** — the UI shows pending / succeeded / failed, and
   the user is notified when a previously failed request later succeeds.

Treat these as load-bearing. Do not weaken them to make a test or a build pass.

## Conventions

- Kotlin only. Formatting is enforced by ktlint 1.5.0 against `.editorconfig`;
  `ktlint_code_style = android_studio`, 4-space indent, LF endings.
- `@Composable` functions are PascalCase (already exempted in `.editorconfig`).
- The app package is `com.example.splendit`. Package names must not contain
  underscores — ktlint's `standard:package-name` rejects them and cannot
  auto-fix it. Android Studio derives the package from the project name
  ("splend-it"), so new modules need the name corrected by hand.
- No wildcard imports (`standard:no-wildcard-imports`); ktlint cannot auto-fix
  these either, so the generated test templates need them expanded.
- Keep the agent rules file current. When an agent gets something wrong in a
  way that will recur, add the rule here rather than fixing it case by case.

## CI / Definition of done

CI is `.github/workflows/ci.yml`. It runs on every pull request and on pushes
to `main`. Two cheap gates run first — agent-rules and the gitleaks secret scan
— and the four expensive jobs (static checks, unit tests, instrumented tests,
debug build) then run in parallel.

**Build JDK is 21**, pinned in three places that must stay in agreement:
`gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`), the `JAVA_VERSION`
env in `ci.yml`, and your local `JAVA_HOME`. If they diverge, Gradle silently
downloads its own JDK on every CI run. Android Studio's bundled JBR is already
21; a newer system JDK is not a substitute.

**Before opening a pull request, run locally from the folder containing
`gradlew`:**

```bash
ktlint "**/*.kt" "**/*.kts" "!**/build/**"   # add -F to auto-fix
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest          # needs an emulator or device
```

**A pull request is done only when all three of these are true:**

1. **CI is green.** Every job passes. Fix the cause, not the check — do not add
   lint baselines, `@Suppress`, or `-PskipTests`-style escapes, and do not
   delete or disable a failing test to get a green tick.
2. **It includes tests.** New behaviour ships with tests. Anything touching
   offline caching, retry, or request-status feedback needs a test that
   exercises the failure path, not just the happy path.
3. **A teammate who did not generate the code has reviewed it.** If an AI wrote
   it, the reviewer must be a human other than the person who prompted it.
   "The model wrote it and it ran" is not verification.

Nothing is pushed directly to `main`; `main` is reached only through a reviewed
pull request.

Every significant AI interaction goes in the AI dossier (Appendix A of the
design document): tool, mode, task, prompt or link, verification, decision.
Wrong or off-spec AI output goes in the failure log with how it was caught.
