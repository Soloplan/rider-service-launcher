# Rider Service Launcher

![Service Launcher icon](src/main/resources/META-INF/pluginIcon.svg)

Service Launcher gives multi-service projects a small, visual control panel inside Rider. It is useful when a normal
development session involves repeatedly starting, debugging, restarting, and stopping different combinations of
services.

The launcher does not replace Rider's run configurations. It provides an organized view over them, while Rider remains
responsible for execution, debugging, consoles, environment variables, and before-launch tasks.

## Philosophy

The plugin is intentionally lightweight:

- Launcher configurations describe how services should appear and be grouped. They exist independently from Rider run
  configurations and can be linked or relinked when needed.
- The launcher only contains services that the user explicitly creates/adds.
- Starting and debugging use Rider's existing execution pipeline.
- Configuration is project-specific and can be exported when a setup should be shared with the team.

## Using the launcher

Open **View | Tool Windows | Service Launcher**.

To set up a project:

1. Right-click the launcher background and create a service or group.
2. Give the service a useful display name and, when it is ready to run, link it to an existing permanent Rider run
   configuration.
3. Select the services that belong to the current development task.
4. Use **Run** or **Debug** to start the selected services.

Right-click a card to edit, move, or remove its launcher configuration. Use the pencil button when it is more convenient
to manage the complete list in one dialog.

Import and export create a self-contained `*.service-launcher.xml` file. This is the simplest way to share a useful
launcher arrangement without modifying anyone's Rider run configurations.

Application-level preferences are available under **Settings | Tools | Service Launcher**. They control card actions,
selection toggles, the primary color, and the startup presentation. These preferences are not included in launcher
imports or exports.

> Only permanent Rider run configurations can be linked. If a `launchSettings.json` profile is missing, generate its
> Rider configuration first.

## Installing

Download the plugin ZIP from the GitHub release. In Rider:

1. Open **Settings | Plugins**.
2. Choose the gear menu and **Install Plugin from Disk**.
3. Select the downloaded ZIP and restart Rider when prompted.
4. Open **View | Tool Windows | Service Launcher**.

The plugin supports Rider 2025.2 and later.

## Building from source

The Gradle wrapper requires Java. Rider's bundled runtime can be used from PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\JetBrains\JetBrains Rider 2025.2.2.1\jbr'
.\gradlew.bat buildPlugin
```

The resulting ZIP is written to `build/distributions`.

For plugin development, run:

```powershell
.\gradlew.bat runIde
```

The implementation uses IntelliJ Platform frontend APIs and has no ReSharper backend component.
