//-----------------------------------------------------------------------
// <copyright file="LauncherConfigurationIOTest.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.util.IconLoader;
import org.junit.Test;

import javax.swing.Icon;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Represents a launcher configuration iotest.
 */
public class LauncherConfigurationIOTest
{
  /**
   * Performs the round trip preserves configuration and embedded icon operation.
   *
   * @throws Exception if the operation cannot be completed
   */
  @Test
  public void roundTripPreservesConfigurationAndEmbeddedIcon() throws Exception
  {
    ServiceLauncherSettings.LauncherConfiguration source = new ServiceLauncherSettings.LauncherConfiguration("Orders API", 2);
    source.displayName = "Order Entry\nCarloAPI";
    source.group = "Carlo API";
    source.icon = "dotnet";
    source.customIconName = "orders.svg";
    source.customIconData = Base64.getEncoder().encodeToString("<svg/>".getBytes());
    source.visible = false;

    File export = Files.createTempFile("service-launcher", ".xml").toFile();
    try
    {
      LauncherConfigurationIO.exportTo(export, List.of(source));
      List<ServiceLauncherSettings.LauncherConfiguration> imported = LauncherConfigurationIO.importFrom(export);

      assertEquals(1, imported.size());
      ServiceLauncherSettings.LauncherConfiguration result = imported.get(0);
      assertEquals(source.configurationName, result.configurationName);
      assertEquals(source.displayName, result.displayName);
      assertEquals(source.group, result.group);
      assertEquals(source.customIconName, result.customIconName);
      assertEquals(source.customIconData, result.customIconData);
      assertEquals(false, result.visible);
    }
    finally
    {
      assertTrue(export.delete() || !export.exists());
    }
  }

  /**
   * Normalizes the orders preserves group order and orders within groups.
   */
  @Test
  public void normalizeOrdersPreservesGroupOrderAndOrdersWithinGroups()
  {
    ServiceLauncherSettings.LauncherConfiguration backendSecond = item("Backend 2", "Backend", 8);
    ServiceLauncherSettings.LauncherConfiguration frontend = item("Frontend", "Frontend", 4);
    ServiceLauncherSettings.LauncherConfiguration backendFirst = item("Backend 1", "Backend", 1);
    List<ServiceLauncherSettings.LauncherConfiguration> items = new ArrayList<>(
      List.of(backendSecond, frontend, backendFirst)
    );

    ServiceLauncherSettings.normalizeOrders(items);

    assertEquals(List.of("Backend 1", "Backend 2", "Frontend"),
      items.stream().map(item -> item.configurationName).toList());
    assertEquals(0, backendFirst.order);
    assertEquals(1, backendSecond.order);
    assertEquals(0, frontend.order);
  }

  /**
   * Performs the embedded svg is rendered and scaled as an icon operation.
   */
  @Test
  public void embeddedSvgIsRenderedAndScaledAsAnIcon()
  {
    ServiceLauncherSettings.LauncherConfiguration item = new ServiceLauncherSettings.LauncherConfiguration("API", 0);
    item.customIconName = "api.svg";
    item.customIconData = Base64.getEncoder().encodeToString((
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"64\" height=\"32\">" +
        "<rect width=\"64\" height=\"32\" fill=\"#22a7e0\"/></svg>"
    ).getBytes());

    Icon icon = LauncherIcons.get(item);

    assertTrue(icon.getIconWidth() <= 24);
    assertTrue(icon.getIconHeight() <= 24);
    assertTrue(icon.getIconWidth() > 0);
    assertTrue(icon.getIconHeight() > 0);
  }

  /**
   * Performs the plugin and tool window icons are bundled at rider sizes operation.
   */
  @Test
  public void pluginAndToolWindowIconsAreBundledAtRiderSizes()
  {
    Icon pluginIcon = IconLoader.getIcon("/META-INF/pluginIcon.svg", LauncherConfigurationIOTest.class);
    Icon toolWindowIcon = IconLoader.getIcon("/icons/toolWindow.svg", LauncherConfigurationIOTest.class);

    assertEquals(40, pluginIcon.getIconWidth());
    assertEquals(40, pluginIcon.getIconHeight());
    assertEquals(13, toolWindowIcon.getIconWidth());
    assertEquals(13, toolWindowIcon.getIconHeight());
    assertNotNull(LauncherConfigurationIOTest.class.getResource("/META-INF/pluginIcon_dark.svg"));
    assertNotNull(LauncherConfigurationIOTest.class.getResource("/icons/toolWindow_dark.svg"));
  }

  /**
   * Performs the replacing settings from their own backing list does not clear them operation.
   */
  @Test
  public void replacingSettingsFromTheirOwnBackingListDoesNotClearThem()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    state.services.add(item("Carlo", "Backend", 0));
    state.services.add(item("Frontend", "Frontend", 0));
    settings.loadState(state);

    settings.replaceWith(state.services);

    assertEquals(2, settings.getState().services.size());
    assertEquals("Carlo", settings.getState().services.get(0).configurationName);
    assertEquals("Frontend", settings.getState().services.get(1).configurationName);
  }

  /**
   * Performs the discovered run configurations do not create launcher configurations operation.
   */
  @Test
  public void discoveredRunConfigurationsDoNotCreateLauncherConfigurations()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();

    List<ServiceLauncherSettings.LauncherConfiguration> items = settings.reconciledWith(List.of("New API"));

    assertTrue(items.isEmpty());
  }

  /**
   * Performs the missing run configuration only unlinks the existing launcher configuration operation.
   */
  @Test
  public void missingRunConfigurationOnlyUnlinksTheExistingLauncherConfiguration()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    ServiceLauncherSettings.LauncherConfiguration existing = item("Old API", "Backend", 0);
    existing.visible = true;
    state.services.add(existing);
    settings.loadState(state);

    List<ServiceLauncherSettings.LauncherConfiguration> items = settings.reconciledWith(List.of("Renamed API"));

    assertEquals(1, items.size());
    assertEquals(existing.itemId, items.get(0).itemId);
    assertEquals("", items.get(0).configurationName);
    assertEquals("Old API", items.get(0).expectedConfigurationName);
    assertTrue(items.get(0).visible);
  }

  /**
   * Performs the returning run configuration restores its existing link operation.
   */
  @Test
  public void returningRunConfigurationRestoresItsExistingLink()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    ServiceLauncherSettings.LauncherConfiguration existing = item("", "Backend", 0);
    existing.expectedConfigurationName = "Orders API";
    state.services.add(existing);
    settings.loadState(state);

    List<ServiceLauncherSettings.LauncherConfiguration> items = settings.reconciledWith(List.of("Orders API"));

    assertEquals(1, items.size());
    assertEquals("Orders API", existing.configurationName);
    assertEquals("", existing.expectedConfigurationName);
  }

  /**
   * Performs the round trip preserves stable identity and missing link information operation.
   *
   * @throws Exception if the operation cannot be completed
   */
  @Test
  public void roundTripPreservesStableIdentityAndMissingLinkInformation() throws Exception
  {
    ServiceLauncherSettings.LauncherConfiguration source = item("", "Backend", 0);
    source.expectedConfigurationName = "Unavailable API";
    source.displayName = "Administration";
    source.visible = true;

    File export = Files.createTempFile("service-launcher-unlinked", ".xml").toFile();
    try
    {
      LauncherConfigurationIO.exportTo(export, List.of(source));
      ServiceLauncherSettings.LauncherConfiguration result = LauncherConfigurationIO.importFrom(export).get(0);

      assertEquals(source.itemId, result.itemId);
      assertEquals("", result.configurationName);
      assertEquals("Unavailable API", result.expectedConfigurationName);
      assertEquals("Administration", result.displayName);
      assertTrue(result.visible);
    }
    finally
    {
      assertTrue(export.delete() || !export.exists());
    }
  }

  /**
   * Returns the result of item.
   *
   * @param name the name
   * @param group the group
   * @param order the order
   * @return the item result
   */
  private static ServiceLauncherSettings.LauncherConfiguration item(String name, String group, int order)
  {
    ServiceLauncherSettings.LauncherConfiguration item = new ServiceLauncherSettings.LauncherConfiguration(name, order);
    item.group = group;
    return item;
  }
}
