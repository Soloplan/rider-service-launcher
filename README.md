# Rider Service Launcher

A local, frontend-only Rider plugin for arranging existing run configurations as visual service cards and launching any selected combination through Rider's normal Run/Debug pipeline.

## Features

- Compact responsive card layout with crisp HiDPI-aware 24px service icons that adds columns as the tool window gets wider.
- Project-specific aliases, group names, group-aware ordering, visibility, and icons.
- Stable launcher-item IDs independent of Rider configuration names.
- Newly discovered Rider configurations are added as inactive candidates.
- Items whose run configuration is missing remain visible with a warning and can be relinked.
- Arbitrary SVG, PNG, JPEG, or GIF icons, stored directly with the launcher settings.
- Portable XML export/import; custom icon data is embedded in the export.
- Run or debug only the selected services that are not running yet.
- Start missing and restart running selected services with one action; running services keep their current Run/Debug mode.
- Stop all services started through the launcher with one action.
- Running cards expose compact Stop and Restart controls in their upper-right corner.
- Toolbar and card controls have Rider-style hover and pressed states.
- Select-all and clear-selection actions in the launcher background context menu, plus show-all/hide-all in the configuration list.
- Selected services use a thin bright CarLo-magenta border; running services use a distinctly heavier deep-magenta border. Cards in both states show both brand shades.
- Right-click a card to edit its display name, group, position, and icon, or to move it within/between groups.
- Existing Rider run configurations remain the source of truth, so debugger support, environment variables, consoles, and before-launch tasks are preserved.

## Build and install

The project is compiled against the locally installed Rider 2025.2 (`252`) SDK and declares compatibility with Rider 2025.2 and later, including Rider 2026.1 (`261`).

1. Run `gradlew.bat buildPlugin`.
2. In Rider, open **Settings | Plugins**, choose the gear menu, then **Install Plugin from Disk**.
3. Select the ZIP produced in `build/distributions`.
4. Open **View | Tool Windows | Service Launcher**.

Use the pencil button to configure all cards at once. The configuration dialog's **Edit selected** action can relink any item to an available Rider run configuration. Right-click a visible card for focused editing, relinking, and ordering, or right-click the launcher background to select or deselect everything. **Import** and **Export** are available in the configuration dialog and create a self-contained `*.service-launcher.xml` file suitable for version control or sharing with the team. Configuration is project-specific and stored in Rider's workspace settings. No existing run configuration is modified.

Only permanent run configurations are shown because temporary configurations can disappear at any time. For `launchSettings.json` profiles that are not listed yet, right-click the file in Rider and choose **Generate Configurations** first.

## Development

Run `gradlew.bat runIde` to start an isolated Rider development instance. The plugin is deliberately implemented using only IntelliJ Platform frontend APIs; it has no ReSharper backend component.
