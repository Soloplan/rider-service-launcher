//-----------------------------------------------------------------------
// <copyright file="ServiceLauncherSettings.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a service launcher settings.
 */
@Service(Service.Level.PROJECT)
@State(name = "ServiceLauncherSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ServiceLauncherSettings implements PersistentStateComponent<ServiceLauncherSettings.StateData>
{
  public static final String DEFAULT_GROUP = "Services";

  /**
   * Represents a state data.
   */
  public static final class StateData
  {
    public List<LauncherConfiguration> services = new ArrayList<>();
    public List<String> groups = new ArrayList<>();
  }

  /**
   * Represents a launcher configuration.
   */
  @Tag("ServiceAppearance")
  public static final class LauncherConfiguration
  {
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

    /**
     * Creates a new {@code LauncherConfiguration} instance.
     */
    public LauncherConfiguration()
    {
    }

    /**
     * Creates a new {@code LauncherConfiguration} instance.
     *
     * @param configurationName the configuration name
     * @param order the order
     */
    public LauncherConfiguration(String configurationName, int order)
    {
      this.itemId = UUID.randomUUID().toString();
      this.configurationName = configurationName;
      this.displayName = configurationName;
      this.visible = false;
      this.order = order;
    }

    /**
     * Copies the operation.
     *
     * @return the copy result
     */
    public LauncherConfiguration copy()
    {
      LauncherConfiguration copy = new LauncherConfiguration();
      XmlSerializerUtil.copyBean(this, copy);
      return copy;
    }
  }

  private StateData state = new StateData();

  /**
   * Returns the instance.
   *
   * @param project the project
   * @return the instance
   */
  public static ServiceLauncherSettings getInstance(Project project)
  {
    return project.getService(ServiceLauncherSettings.class);
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable StateData getState()
  {
    return state;
  }

  /** {@inheritDoc} */
  @Override
  public void loadState(@NotNull StateData state)
  {
    this.state = state;
    sanitizeState();
    ensureGroups();
  }

  /**
   * Returns the result of reconciled with.
   *
   * @param configurationNames the configuration names
   * @return the reconciled with result
   */
  public List<LauncherConfiguration> reconciledWith(List<String> configurationNames)
  {
    ensureItemIds(state.services);
    ensureGroups();
    Set<String> available = new HashSet<>(configurationNames);
    Set<String> linked = new HashSet<>();
    for (LauncherConfiguration item : state.services)
    {
      reconcileLink(item, available, linked);
    }
    normalizeOrders(state.services);
    ensureGroups();
    return state.services;
  }

  /**
   * Performs the reconcile link operation.
   *
   * @param configuration the configuration
   * @param available the available
   * @param linked the linked
   */
  private void reconcileLink(LauncherConfiguration configuration, Set<String> available, Set<String> linked)
  {
    String configuredName = safe(configuration.configurationName);
    if (claimLink(configuredName, available, linked))
    {
      configuration.expectedConfigurationName = "";
      return;
    }
    if (!configuredName.isEmpty())
    {
      configuration.expectedConfigurationName = configuredName;
      configuration.configurationName = "";
    }
    String expectedName = safe(configuration.expectedConfigurationName);
    if (claimLink(expectedName, available, linked))
    {
      configuration.configurationName = expectedName;
      configuration.expectedConfigurationName = "";
    }
  }

  /**
   * Determines whether claim link.
   *
   * @param name the name
   * @param available the available
   * @param linked the linked
   * @return whether claim link
   */
  private boolean claimLink(String name, Set<String> available, Set<String> linked)
  {
    return !name.isEmpty() && available.contains(name) && linked.add(name);
  }

  /**
   * Replaces the with.
   *
   * @param imported the imported
   */
  public void replaceWith(List<LauncherConfiguration> imported)
  {
    List<LauncherConfiguration> snapshot = imported.stream().map(LauncherConfiguration::copy).toList();
    ensureItemIds(snapshot);
    state.services.clear();
    state.services.addAll(snapshot);
    normalizeOrders(state.services);
    ensureGroups();
  }

  /**
   * Returns the result of group names.
   *
   * @return the group names result
   */
  public List<String> groupNames()
  {
    ensureGroups();
    return List.copyOf(state.groups);
  }

  /**
   * Determines whether add group.
   *
   * @param requestedName the requested name
   * @return whether add group
   */
  public boolean addGroup(String requestedName)
  {
    ensureGroups();
    String name = normalizeGroup(requestedName);
    if (containsGroup(name))
    {
      return false;
    }
    state.groups.add(name);
    return true;
  }

  /**
   * Determines whether rename group.
   *
   * @param sourceName the source name
   * @param requestedName the requested name
   * @return whether rename group
   */
  public boolean renameGroup(String sourceName, String requestedName)
  {
    ensureGroups();
    String targetName = normalizeGroup(requestedName);
    int sourceIndex = indexOfGroup(sourceName);
    if (sourceIndex < 0 || (containsGroup(targetName) && !sourceName.equalsIgnoreCase(targetName)))
    {
      return false;
    }
    String existingName = state.groups.get(sourceIndex);
    state.groups.set(sourceIndex, targetName);
    state.services.stream()
      .filter(item -> existingName.equalsIgnoreCase(normalizeGroup(item.group)))
      .forEach(item -> item.group = targetName);
    return true;
  }

  /**
   * Determines whether move group.
   *
   * @param groupName the group name
   * @param delta the delta
   * @return whether move group
   */
  public boolean moveGroup(String groupName, int delta)
  {
    ensureGroups();
    int sourceIndex = indexOfGroup(groupName);
    int targetIndex = sourceIndex + delta;
    if (sourceIndex < 0 || targetIndex < 0 || targetIndex >= state.groups.size())
    {
      return false;
    }
    String moved = state.groups.remove(sourceIndex);
    state.groups.add(targetIndex, moved);
    return true;
  }

  /**
   * Determines whether remove group.
   *
   * @param groupName the group name
   * @return whether remove group
   */
  public boolean removeGroup(String groupName)
  {
    ensureGroups();
    boolean hasServices = state.services.stream()
      .anyMatch(item -> normalizeGroup(item.group).equalsIgnoreCase(groupName));
    if (hasServices)
    {
      return false;
    }
    int index = indexOfGroup(groupName);
    if (index < 0)
    {
      return false;
    }
    state.groups.remove(index);
    return true;
  }

  /**
   * Determines whether remove service.
   *
   * @param itemId the item id
   * @return whether remove service
   */
  public boolean removeService(String itemId)
  {
    return state.services.removeIf(item -> item.itemId.equals(itemId));
  }

  /**
   * Normalizes the orders.
   *
   * @param services the services
   */
  public static void normalizeOrders(List<LauncherConfiguration> services)
  {
    Map<String, List<LauncherConfiguration>> groups = new LinkedHashMap<>();
    services.forEach(item ->
    {
      item.group = normalizeGroup(item.group);
      groups.computeIfAbsent(item.group, ignored -> new ArrayList<>()).add(item);
    });
    List<LauncherConfiguration> normalized = new ArrayList<>();
    for (List<LauncherConfiguration> groupItems : groups.values())
    {
      groupItems.sort(Comparator.comparingInt(item -> item.order));
      for (int index = 0; index < groupItems.size(); index++)
      {
        groupItems.get(index).order = index;
        normalized.add(groupItems.get(index));
      }
    }
    services.clear();
    services.addAll(normalized);
  }

  /**
   * Normalizes the group.
   *
   * @param group the group
   * @return the normalize group result
   */
  public static String normalizeGroup(String group)
  {
    return group == null || group.isBlank() ? DEFAULT_GROUP : group.trim();
  }

  /**
   * Ensures the item ids.
   *
   * @param services the services
   */
  public static void ensureItemIds(List<LauncherConfiguration> services)
  {
    Set<String> ids = new HashSet<>();
    for (LauncherConfiguration item : services)
    {
      if (item.itemId == null || item.itemId.isBlank() || !ids.add(item.itemId))
      {
        item.itemId = UUID.randomUUID().toString();
        ids.add(item.itemId);
      }
    }
  }

  /**
   * Ensures the groups.
   */
  private void ensureGroups()
  {
    sanitizeState();
    if (state.groups == null)
    {
      state.groups = new ArrayList<>();
    }
    List<String> normalized = new ArrayList<>();
    for (String group : state.groups)
    {
      addUniqueGroup(normalized, normalizeGroup(group));
    }
    for (LauncherConfiguration item : state.services)
    {
      addUniqueGroup(normalized, normalizeGroup(item.group));
    }
    state.groups.clear();
    state.groups.addAll(normalized);
  }

  /**
   * Performs the sanitize state operation.
   */
  private void sanitizeState()
  {
    if (state.services == null)
    {
      state.services = new ArrayList<>();
      return;
    }
    state.services.removeIf(Objects::isNull);
  }

  /**
   * Determines whether group.
   *
   * @param groupName the group name
   * @return whether group
   */
  private boolean containsGroup(String groupName)
  {
    return indexOfGroup(groupName) >= 0;
  }

  /**
   * Returns the result of index of group.
   *
   * @param groupName the group name
   * @return the index of group result
   */
  private int indexOfGroup(String groupName)
  {
    for (int index = 0; index < state.groups.size(); index++)
    {
      if (state.groups.get(index).equalsIgnoreCase(groupName))
      {
        return index;
      }
    }
    return -1;
  }

  /**
   * Adds the unique group.
   *
   * @param groups the groups
   * @param groupName the group name
   */
  private static void addUniqueGroup(List<String> groups, String groupName)
  {
    if (groups.stream().noneMatch(existing -> existing.equalsIgnoreCase(groupName)))
    {
      groups.add(groupName);
    }
  }

  /**
   * Returns the result of safe.
   *
   * @param value the value
   * @return the safe result
   */
  private static String safe(String value)
  {
    return value == null ? "" : value.trim();
  }
}
