# Repository Guidance

## Project overview

Rider Service Launcher is a Java IntelliJ Platform plug-in for Rider 2025.2 and later. It is a frontend-only plug-in;
there is no ReSharper backend component.

The launcher is an organized view over Rider run configurations. A launcher configuration is its own entity and may
exist without being linked to a Rider run configuration. Rider remains responsible for execution, debugging, consoles,
environment variables, and before-launch tasks.

## Important configuration boundaries

- `ServiceLauncherSettings` contains project-specific launcher configurations and groups. These are the values handled
  by launcher import and export.
- `ServiceLauncherPreferences` contains application-level plug-in preferences. Never include these preferences in
  launcher import or export.
- Persist preferences in a forward-compatible form. Added, removed, renamed, unknown, or malformed options must not
  prevent the remaining preferences from loading. New options must have safe defaults.
- Do not make launcher configurations depend on Rider run configurations. Linking and relinking must remain optional.

## Code style

Follow `.editorconfig` and the existing Java sources:

- Use Java 21, UTF-8, CRLF line endings, two-space indentation, and a maximum line length of 130 characters.
- Use Allman braces for types, methods, and control-flow blocks.
- Put `else`, `catch`, `finally`, and `while` on a new line.
- Add the Soloplan copyright header to new source and build-script files.
- Add meaningful Javadoc to every type, constructor, and method, including private declarations. Use
  `{@inheritDoc}` for straightforward overrides.
- Prefer small, focused classes and methods. Keep IntelliJ UI updates on the appropriate Swing/application thread.
- Reuse IntelliJ Platform APIs and existing project helpers instead of duplicating platform behavior.

Do not add formatting or code-generation scripts merely to enforce style. Contributors are expected to configure their
IDE appropriately and review their changes.

## User-interface conventions

- The configured primary color communicates running services and startup activity. Selection borders remain neutral
  grey.
- Starting, running, selected, and stopped states must remain visually distinguishable.
- Per-card actions and selection toggles must respect the application preferences while aggregate toolbar actions
  continue to work.
- Prefer live application of preference changes when an existing tool window can be updated safely.

When changing animation or painting code, repaint only the affected cards and stop timers when no service is starting.

## Versioning and releases

Version changes are manual. Do not automatically increment the plug-in version or introduce an automatic build number.
Only change the version in `build.gradle.kts` when explicitly requested.

A distributable release is produced as a ZIP in `build/distributions`. GitHub releases use a matching `v<version>` tag
and attach that ZIP.

## Building and validation

The Gradle wrapper requires Java. Rider's bundled runtime can be used from PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\JetBrains\JetBrains Rider 2025.2.2.1\jbr'
.\gradlew.bat test buildPlugin verifyPluginStructure verifyPluginProjectConfiguration
```

PowerShell does not execute programs from the current directory implicitly, so always use `.\gradlew.bat`.

Before handing off a change:

1. Run the focused tests for the changed behavior.
2. Run the complete command above.
3. Verify Javadoc coverage:

   ```powershell
   java scripts\GenerateDocumentation.java --check
   ```

4. Run `git diff --check`.
5. Confirm the expected ZIP exists in `build/distributions`.

Do not use `clean` unless it is necessary; Rider or another process may hold generated report files open.

## Tests

Tests use JUnit 4 and live under `src/test/java/local/soloplan/tools/servicelauncher`.

- Add focused regression tests for persistence, import/export, reconciliation, default values, and compatibility
  behavior.
- Include serialization round trips when testing persisted state rather than testing only an in-memory object.
- Keep tests deterministic and independent from a locally running Rider instance where possible.
- Preserve unknown preference entries in compatibility tests so future settings do not break older installations.

## Change discipline

- Preserve unrelated user changes in the working tree.
- Do not commit generated build output.
- Keep README content focused on the plug-in's value, philosophy, installation, and normal use rather than cataloguing
  every implementation detail.
- Register new IntelliJ extensions in `src/main/resources/META-INF/plugin.xml` and validate the plug-in structure after
  changes.
