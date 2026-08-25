//-----------------------------------------------------------------------
// <copyright file="LauncherConfigurationIO.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

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

/**
 * Represents a launcher configuration io.
 */
final class LauncherConfigurationIO
{
  static final String FILE_EXTENSION = "service-launcher.xml";
  private static final int FORMAT_VERSION = 2;
  private static final int MAX_SERVICES = 10_000;

  /**
   * Creates a new {@code LauncherConfigurationIO} instance.
   */
  private LauncherConfigurationIO()
  {
  }

  /**
   * Exports the to.
   *
   * @param file the file
   * @param configurations the configurations
   * @throws IOException if the operation cannot be completed
   */
  static void exportTo(
    File file,
    List<ServiceLauncherSettings.LauncherConfiguration> configurations
  ) throws IOException
  {
    Properties properties = serialize(configurations);
    try (FileOutputStream output = new FileOutputStream(file))
    {
      properties.storeToXML(output, "Rider Service Launcher configuration (custom icons are embedded)", "UTF-8");
    }
  }

  /**
   * Returns the result of serialize.
   *
   * @param configurations the configurations
   * @return the serialize result
   */
  private static Properties serialize(
    List<ServiceLauncherSettings.LauncherConfiguration> configurations
  )
  {
    Properties properties = new Properties();
    properties.setProperty("formatVersion", String.valueOf(FORMAT_VERSION));
    properties.setProperty("serviceCount", String.valueOf(configurations.size()));
    for (int index = 0; index < configurations.size(); index++)
    {
      writeConfiguration(properties, configurations.get(index), index);
    }
    return properties;
  }

  /**
   * Writes the configuration.
   *
   * @param properties the properties
   * @param configuration the configuration
   * @param index the index
   */
  private static void writeConfiguration(
    Properties properties,
    ServiceLauncherSettings.LauncherConfiguration configuration,
    int index
  )
  {
    String prefix = propertyPrefix(index);
    properties.setProperty(prefix + "itemId", safe(configuration.itemId));
    properties.setProperty(prefix + "configurationName", safe(configuration.configurationName));
    properties.setProperty(prefix + "expectedConfigurationName", safe(configuration.expectedConfigurationName));
    properties.setProperty(prefix + "displayName", safe(configuration.displayName));
    properties.setProperty(prefix + "group", safe(configuration.group));
    properties.setProperty(prefix + "icon", safe(configuration.icon));
    properties.setProperty(prefix + "customIconName", safe(configuration.customIconName));
    properties.setProperty(prefix + "customIconData", safe(configuration.customIconData));
    properties.setProperty(prefix + "visible", String.valueOf(configuration.visible));
    properties.setProperty(prefix + "order", String.valueOf(configuration.order));
  }

  /**
   * Imports the from.
   *
   * @param file the file
   * @return the import from result
   * @throws IOException if the operation cannot be completed
   */
  static List<ServiceLauncherSettings.LauncherConfiguration> importFrom(File file) throws IOException
  {
    Properties properties = load(file);
    int count = validateHeader(properties);
    List<ServiceLauncherSettings.LauncherConfiguration> configurations = readConfigurations(properties, count);
    ServiceLauncherSettings.ensureItemIds(configurations);
    ServiceLauncherSettings.normalizeOrders(configurations);
    return configurations;
  }

  /**
   * Loads the operation.
   *
   * @param file the file
   * @return the load result
   * @throws IOException if the operation cannot be completed
   */
  private static Properties load(File file) throws IOException
  {
    Properties properties = new Properties();
    try (FileInputStream input = new FileInputStream(file))
    {
      properties.loadFromXML(input);
    }
    return properties;
  }

  /**
   * Returns the result of validate header.
   *
   * @param properties the properties
   * @return the validate header result
   * @throws IOException if the operation cannot be completed
   */
  private static int validateHeader(Properties properties) throws IOException
  {
    int version = parseInt(properties.getProperty("formatVersion"), 0);
    if (version < 1 || version > FORMAT_VERSION)
    {
      throw new IOException("Unsupported Service Launcher export version: " + version);
    }
    int count = parseInt(properties.getProperty("serviceCount"), -1);
    if (count < 0 || count > MAX_SERVICES)
    {
      throw new IOException("Invalid service count in export file.");
    }
    return count;
  }

  /**
   * Reads the configurations.
   *
   * @param properties the properties
   * @param count the count
   * @return the read configurations result
   * @throws IOException if the operation cannot be completed
   */
  private static List<ServiceLauncherSettings.LauncherConfiguration> readConfigurations(
    Properties properties,
    int count
  ) throws IOException
  {
    List<ServiceLauncherSettings.LauncherConfiguration> configurations = new ArrayList<>();
    Set<String> identities = new HashSet<>();
    for (int index = 0; index < count; index++)
    {
      ServiceLauncherSettings.LauncherConfiguration configuration =
        readConfiguration(properties, index, identities);
      if (configuration != null)
      {
        configurations.add(configuration);
      }
    }
    return configurations;
  }

  /**
   * Reads the configuration.
   *
   * @param properties the properties
   * @param index the index
   * @param identities the identities
   * @return the read configuration result
   * @throws IOException if the operation cannot be completed
   */
  private static ServiceLauncherSettings.LauncherConfiguration readConfiguration(
    Properties properties,
    int index,
    Set<String> identities
  ) throws IOException
  {
      String prefix = propertyPrefix(index);
      String itemId = properties.getProperty(prefix + "itemId", "").trim();
      String configurationName = properties.getProperty(prefix + "configurationName", "").trim();
      String identity = itemId.isEmpty()
        ? (configurationName.isEmpty() ? "legacy-index:" + index : "legacy-link:" + configurationName)
        : "id:" + itemId;
      if (!identities.add(identity))
      {
        return null;
      }
      ServiceLauncherSettings.LauncherConfiguration configuration =
        new ServiceLauncherSettings.LauncherConfiguration();
      configuration.itemId = itemId;
      configuration.configurationName = configurationName;
      configuration.expectedConfigurationName =
        properties.getProperty(prefix + "expectedConfigurationName", "");
      configuration.displayName = properties.getProperty(prefix + "displayName", configurationName);
      configuration.group = properties.getProperty(prefix + "group", ServiceLauncherSettings.DEFAULT_GROUP);
      configuration.icon = properties.getProperty(prefix + "icon", "service");
      configuration.customIconName = properties.getProperty(prefix + "customIconName", "");
      configuration.customIconData = properties.getProperty(prefix + "customIconData", "");
      validateBase64(configuration.customIconData);
      configuration.visible = Boolean.parseBoolean(properties.getProperty(prefix + "visible", "true"));
      configuration.order = parseInt(properties.getProperty(prefix + "order"), index);
      return configuration;
  }

  /**
   * Returns the result of property prefix.
   *
   * @param index the index
   * @return the property prefix result
   */
  private static String propertyPrefix(int index)
  {
    return "service." + index + ".";
  }

  /**
   * Merges the operation.
   *
   * @param current the current
   * @param imported the imported
   * @return the merge result
   */
  static List<ServiceLauncherSettings.LauncherConfiguration> merge(
    List<ServiceLauncherSettings.LauncherConfiguration> current,
    List<ServiceLauncherSettings.LauncherConfiguration> imported
  )
  {
    List<ServiceLauncherSettings.LauncherConfiguration> merged = new ArrayList<>();
    Set<String> importedIds = new HashSet<>();
    Set<String> claimedConfigurations = new HashSet<>();
    for (ServiceLauncherSettings.LauncherConfiguration configuration : imported)
    {
      merged.add(configuration.copy());
      importedIds.add(configuration.itemId);
      if (hasRunConfigurationLink(configuration))
      {
        claimedConfigurations.add(configuration.configurationName);
      }
    }
    for (ServiceLauncherSettings.LauncherConfiguration configuration : current)
    {
      boolean duplicateId = importedIds.contains(configuration.itemId);
      boolean duplicateLink = hasRunConfigurationLink(configuration)
        && claimedConfigurations.contains(configuration.configurationName);
      if (!duplicateId && !duplicateLink)
      {
        merged.add(configuration.copy());
      }
    }
    ServiceLauncherSettings.ensureItemIds(merged);
    ServiceLauncherSettings.normalizeOrders(merged);
    return merged;
  }

  /**
   * Determines whether run configuration link.
   *
   * @param configuration the configuration
   * @return whether run configuration link
   */
  private static boolean hasRunConfigurationLink(
    ServiceLauncherSettings.LauncherConfiguration configuration
  )
  {
    return configuration.configurationName != null && !configuration.configurationName.isBlank();
  }

  /**
   * Performs the validate base64 operation.
   *
   * @param value the value
   * @throws IOException if the operation cannot be completed
   */
  private static void validateBase64(String value) throws IOException
  {
    if (value == null || value.isEmpty())
    {
      return;
    }
    try
    {
      Base64.getDecoder().decode(value);
    }
    catch (IllegalArgumentException exception)
    {
      throw new IOException("The export contains invalid embedded icon data.", exception);
    }
  }

  /**
   * Returns the result of parse int.
   *
   * @param value the value
   * @param fallback the fallback
   * @return the parse int result
   */
  private static int parseInt(String value, int fallback)
  {
    try
    {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException ignored)
    {
      return fallback;
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
    return value == null ? "" : value;
  }
}
