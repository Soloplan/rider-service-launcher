package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service(Service.Level.PROJECT)
@State(name = "ServiceLauncherSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ServiceLauncherSettings implements PersistentStateComponent<ServiceLauncherSettings.StateData> {
    public static final String DEFAULT_GROUP = "Services";

    public static final class StateData {
        public List<ServiceAppearance> services = new ArrayList<>();
    }

    public static final class ServiceAppearance {
        public String itemId = "";
        public String configurationName = "";
        public String expectedConfigurationName = "";
        public String displayName = "";
        public String group = DEFAULT_GROUP;
        public String icon = "service";
        public String customIconName = "";
        public String customIconData = "";
        public boolean visible = true;
        public int order = 0;

        public ServiceAppearance() {
        }

        public ServiceAppearance(String configurationName, int order) {
            this.itemId = UUID.randomUUID().toString();
            this.configurationName = configurationName;
            this.displayName = configurationName;
            this.visible = false;
            this.order = order;
        }

        public ServiceAppearance copy() {
            ServiceAppearance copy = new ServiceAppearance();
            XmlSerializerUtil.copyBean(this, copy);
            return copy;
        }
    }

    private StateData state = new StateData();

    public static ServiceLauncherSettings getInstance(Project project) {
        return project.getService(ServiceLauncherSettings.class);
    }

    @Override
    public @Nullable StateData getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state;
    }

    public List<ServiceAppearance> synchronizedWith(List<String> configurationNames) {
        ensureItemIds(state.services);
        Set<String> available = new HashSet<>(configurationNames);
        Set<String> linked = new HashSet<>();
        for (ServiceAppearance item : state.services) {
            String configuredName = safe(item.configurationName);
            if (!configuredName.isEmpty() && available.contains(configuredName) && linked.add(configuredName)) {
                item.expectedConfigurationName = "";
                continue;
            }
            if (!configuredName.isEmpty()) {
                item.expectedConfigurationName = configuredName;
                item.configurationName = "";
            }
        }

        int nextOrder = state.services.stream()
            .filter(s -> DEFAULT_GROUP.equals(normalizeGroup(s.group)))
            .map(s -> s.order)
            .max(Comparator.naturalOrder()).orElse(-1) + 1;
        for (String name : configurationNames) {
            if (!linked.contains(name)) {
                state.services.add(new ServiceAppearance(name, nextOrder++));
                linked.add(name);
            }
        }
        normalizeOrders(state.services);
        return state.services;
    }

    public void replaceWith(List<ServiceAppearance> imported) {
        List<ServiceAppearance> snapshot = imported.stream().map(ServiceAppearance::copy).toList();
        ensureItemIds(snapshot);
        state.services.clear();
        state.services.addAll(snapshot);
        normalizeOrders(state.services);
    }

    public static void normalizeOrders(List<ServiceAppearance> services) {
        Map<String, List<ServiceAppearance>> groups = new LinkedHashMap<>();
        services.forEach(item -> {
            item.group = normalizeGroup(item.group);
            groups.computeIfAbsent(item.group, ignored -> new ArrayList<>()).add(item);
        });
        List<ServiceAppearance> normalized = new ArrayList<>();
        for (List<ServiceAppearance> groupItems : groups.values()) {
            groupItems.sort(Comparator.comparingInt(item -> item.order));
            for (int i = 0; i < groupItems.size(); i++) {
                groupItems.get(i).order = i;
                normalized.add(groupItems.get(i));
            }
        }
        services.clear();
        services.addAll(normalized);
    }

    public static String normalizeGroup(String group) {
        return group == null || group.isBlank() ? DEFAULT_GROUP : group.trim();
    }

    public static void ensureItemIds(List<ServiceAppearance> services) {
        Set<String> ids = new HashSet<>();
        for (ServiceAppearance item : services) {
            if (item.itemId == null || item.itemId.isBlank() || !ids.add(item.itemId)) {
                item.itemId = UUID.randomUUID().toString();
                ids.add(item.itemId);
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
