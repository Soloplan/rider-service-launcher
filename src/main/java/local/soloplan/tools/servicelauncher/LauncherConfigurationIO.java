package local.soloplan.tools.servicelauncher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

final class LauncherConfigurationIO {
    static final String FILE_EXTENSION = "service-launcher.xml";
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_SERVICES = 10_000;

    private LauncherConfigurationIO() {
    }

    static void exportTo(File file, List<ServiceLauncherSettings.ServiceAppearance> services) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("formatVersion", String.valueOf(FORMAT_VERSION));
        properties.setProperty("serviceCount", String.valueOf(services.size()));
        for (int i = 0; i < services.size(); i++) {
            ServiceLauncherSettings.ServiceAppearance item = services.get(i);
            String prefix = "service." + i + ".";
            properties.setProperty(prefix + "itemId", safe(item.itemId));
            properties.setProperty(prefix + "configurationName", safe(item.configurationName));
            properties.setProperty(prefix + "expectedConfigurationName", safe(item.expectedConfigurationName));
            properties.setProperty(prefix + "displayName", safe(item.displayName));
            properties.setProperty(prefix + "group", safe(item.group));
            properties.setProperty(prefix + "icon", safe(item.icon));
            properties.setProperty(prefix + "customIconName", safe(item.customIconName));
            properties.setProperty(prefix + "customIconData", safe(item.customIconData));
            properties.setProperty(prefix + "visible", String.valueOf(item.visible));
            properties.setProperty(prefix + "order", String.valueOf(item.order));
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.storeToXML(output, "Rider Service Launcher configuration (custom icons are embedded)", "UTF-8");
        }
    }

    static List<ServiceLauncherSettings.ServiceAppearance> importFrom(File file) throws IOException {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            properties.loadFromXML(input);
        }
        int version = parseInt(properties.getProperty("formatVersion"), 0);
        if (version < 1 || version > FORMAT_VERSION) {
            throw new IOException("Unsupported Service Launcher export version: " + version);
        }
        int count = parseInt(properties.getProperty("serviceCount"), -1);
        if (count < 0 || count > MAX_SERVICES) {
            throw new IOException("Invalid service count in export file.");
        }

        List<ServiceLauncherSettings.ServiceAppearance> result = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String prefix = "service." + i + ".";
            String itemId = properties.getProperty(prefix + "itemId", "").trim();
            String configurationName = properties.getProperty(prefix + "configurationName", "").trim();
            String identity = itemId.isEmpty()
                ? (configurationName.isEmpty() ? "legacy-index:" + i : "legacy-link:" + configurationName)
                : "id:" + itemId;
            if (!identities.add(identity)) {
                continue;
            }
            ServiceLauncherSettings.ServiceAppearance item = new ServiceLauncherSettings.ServiceAppearance();
            item.itemId = itemId;
            item.configurationName = configurationName;
            item.expectedConfigurationName = properties.getProperty(prefix + "expectedConfigurationName", "");
            item.displayName = properties.getProperty(prefix + "displayName", configurationName);
            item.group = properties.getProperty(prefix + "group", ServiceLauncherSettings.DEFAULT_GROUP);
            item.icon = properties.getProperty(prefix + "icon", "service");
            item.customIconName = properties.getProperty(prefix + "customIconName", "");
            item.customIconData = properties.getProperty(prefix + "customIconData", "");
            validateBase64(item.customIconData);
            item.visible = Boolean.parseBoolean(properties.getProperty(prefix + "visible", "true"));
            item.order = parseInt(properties.getProperty(prefix + "order"), i);
            result.add(item);
        }
        ServiceLauncherSettings.ensureItemIds(result);
        ServiceLauncherSettings.normalizeOrders(result);
        return result;
    }

    static List<ServiceLauncherSettings.ServiceAppearance> merge(
        List<ServiceLauncherSettings.ServiceAppearance> current,
        List<ServiceLauncherSettings.ServiceAppearance> imported
    ) {
        List<ServiceLauncherSettings.ServiceAppearance> merged = new ArrayList<>();
        Set<String> importedIds = new HashSet<>();
        Set<String> claimedConfigurations = new HashSet<>();
        for (ServiceLauncherSettings.ServiceAppearance item : imported) {
            merged.add(item.copy());
            importedIds.add(item.itemId);
            if (item.configurationName != null && !item.configurationName.isBlank()) {
                claimedConfigurations.add(item.configurationName);
            }
        }
        for (ServiceLauncherSettings.ServiceAppearance item : current) {
            boolean duplicateId = importedIds.contains(item.itemId);
            boolean duplicateLink = item.configurationName != null
                && !item.configurationName.isBlank()
                && claimedConfigurations.contains(item.configurationName);
            if (!duplicateId && !duplicateLink) {
                merged.add(item.copy());
            }
        }
        ServiceLauncherSettings.ensureItemIds(merged);
        ServiceLauncherSettings.normalizeOrders(merged);
        return merged;
    }

    private static void validateBase64(String value) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("The export contains invalid embedded icon data.", exception);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
