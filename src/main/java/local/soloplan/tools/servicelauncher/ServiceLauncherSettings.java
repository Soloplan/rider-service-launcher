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
        public List<String> groups = new ArrayList<>();
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
        ensureGroups();
    }

    public List<ServiceAppearance> reconciledWith(List<String> configurationNames) {
        ensureItemIds(state.services);
        ensureGroups();
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
            String expectedName = safe(item.expectedConfigurationName);
            if (!expectedName.isEmpty() && available.contains(expectedName) && linked.add(expectedName)) {
                item.configurationName = expectedName;
                item.expectedConfigurationName = "";
            }
        }
        normalizeOrders(state.services);
        ensureGroups();
        return state.services;
    }

    public void replaceWith(List<ServiceAppearance> imported) {
        List<ServiceAppearance> snapshot = imported.stream().map(ServiceAppearance::copy).toList();
        ensureItemIds(snapshot);
        state.services.clear();
        state.services.addAll(snapshot);
        normalizeOrders(state.services);
        ensureGroups();
    }

    public List<String> groupNames() {
        ensureGroups();
        return List.copyOf(state.groups);
    }

    public boolean addGroup(String requestedName) {
        ensureGroups();
        String name = normalizeGroup(requestedName);
        if (containsGroup(name)) {
            return false;
        }
        state.groups.add(name);
        return true;
    }

    public boolean renameGroup(String sourceName, String requestedName) {
        ensureGroups();
        String targetName = normalizeGroup(requestedName);
        int sourceIndex = indexOfGroup(sourceName);
        if (sourceIndex < 0 || (containsGroup(targetName) && !sourceName.equalsIgnoreCase(targetName))) {
            return false;
        }
        String existingName = state.groups.get(sourceIndex);
        state.groups.set(sourceIndex, targetName);
        state.services.stream()
            .filter(item -> existingName.equalsIgnoreCase(normalizeGroup(item.group)))
            .forEach(item -> item.group = targetName);
        return true;
    }

    public boolean moveGroup(String groupName, int delta) {
        ensureGroups();
        int sourceIndex = indexOfGroup(groupName);
        int targetIndex = sourceIndex + delta;
        if (sourceIndex < 0 || targetIndex < 0 || targetIndex >= state.groups.size()) {
            return false;
        }
        String moved = state.groups.remove(sourceIndex);
        state.groups.add(targetIndex, moved);
        return true;
    }

    public boolean removeGroup(String groupName) {
        ensureGroups();
        boolean hasServices = state.services.stream()
            .anyMatch(item -> normalizeGroup(item.group).equalsIgnoreCase(groupName));
        if (hasServices) {
            return false;
        }
        int index = indexOfGroup(groupName);
        if (index < 0) {
            return false;
        }
        state.groups.remove(index);
        return true;
    }

    public boolean removeService(String itemId) {
        return state.services.removeIf(item -> item.itemId.equals(itemId));
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

    private void ensureGroups() {
        if (state.groups == null) {
            state.groups = new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String group : state.groups) {
            addUniqueGroup(normalized, normalizeGroup(group));
        }
        for (ServiceAppearance item : state.services) {
            addUniqueGroup(normalized, normalizeGroup(item.group));
        }
        state.groups.clear();
        state.groups.addAll(normalized);
    }

    private boolean containsGroup(String groupName) {
        return indexOfGroup(groupName) >= 0;
    }

    private int indexOfGroup(String groupName) {
        for (int i = 0; i < state.groups.size(); i++) {
            if (state.groups.get(i).equalsIgnoreCase(groupName)) {
                return i;
            }
        }
        return -1;
    }

    private static void addUniqueGroup(List<String> groups, String groupName) {
        if (groups.stream().noneMatch(existing -> existing.equalsIgnoreCase(groupName))) {
            groups.add(groupName);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
