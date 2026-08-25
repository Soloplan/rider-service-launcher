//-----------------------------------------------------------------------
// <copyright file="ServiceLauncherSettingsTest.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Represents a service launcher settings test.
 */
public class ServiceLauncherSettingsTest
{
  /**
   * Performs the legacy service appearance elements are deserialized operation.
   */
  @Test
  public void legacyServiceAppearanceElementsAreDeserialized()
  {
    Element serializedState = new Element("state");
    Element services = new Element("option").setAttribute("name", "services");
    Element list = new Element("list");
    Element service = new Element("ServiceAppearance");
    service.addContent(new Element("option").setAttribute("name", "itemId").setAttribute("value", "legacy-id"));
    service.addContent(new Element("option").setAttribute("name", "configurationName").setAttribute("value", "Orders API"));
    list.addContent(service);
    services.addContent(list);
    serializedState.addContent(services);

    ServiceLauncherSettings.StateData state =
      XmlSerializer.deserialize(serializedState, ServiceLauncherSettings.StateData.class);

    assertEquals(1, state.services.size());
    assertEquals("legacy-id", state.services.get(0).itemId);
    assertEquals("Orders API", state.services.get(0).configurationName);
  }

  /**
   * Performs the malformed null service entries do not prevent state loading operation.
   */
  @Test
  public void malformedNullServiceEntriesDoNotPreventStateLoading()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    state.services.add(null);

    settings.loadState(state);

    assertTrue(settings.reconciledWith(List.of()).isEmpty());
  }

  /**
   * Performs the empty groups are stored and can be reordered operation.
   */
  @Test
  public void emptyGroupsAreStoredAndCanBeReordered()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();

    assertTrue(settings.addGroup("Backend"));
    assertTrue(settings.addGroup("Frontend"));
    assertTrue(settings.moveGroup("Frontend", -1));

    assertEquals(List.of("Frontend", "Backend"), settings.groupNames());
    assertEquals(List.of("Frontend", "Backend"), settings.getState().groups);
  }

  /**
   * Performs the groups are migrated from existing services operation.
   */
  @Test
  public void groupsAreMigratedFromExistingServices()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    ServiceLauncherSettings.LauncherConfiguration service =
      new ServiceLauncherSettings.LauncherConfiguration("Orders API", 0);
    service.group = "Backend";
    state.services.add(service);

    settings.loadState(state);

    assertEquals(List.of("Backend"), settings.groupNames());
  }

  /**
   * Performs the renaming agroup also moves its services operation.
   */
  @Test
  public void renamingAGroupAlsoMovesItsServices()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    ServiceLauncherSettings.LauncherConfiguration service =
      new ServiceLauncherSettings.LauncherConfiguration("", 0);
    service.group = "Backend";
    service.visible = true;
    state.services.add(service);
    settings.loadState(state);

    assertTrue(settings.renameGroup("Backend", "APIs"));

    assertEquals(List.of("APIs"), settings.groupNames());
    assertEquals("APIs", service.group);
  }

  /**
   * Performs the standalone services remain unlinked during run configuration discovery operation.
   */
  @Test
  public void standaloneServicesRemainUnlinkedDuringRunConfigurationDiscovery()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    ServiceLauncherSettings.LauncherConfiguration standalone =
      new ServiceLauncherSettings.LauncherConfiguration("", 0);
    standalone.displayName = "Orders API";
    standalone.visible = true;
    state.services.add(standalone);
    settings.loadState(state);

    List<ServiceLauncherSettings.LauncherConfiguration> services =
      settings.reconciledWith(List.of("Generated Profile"));

    assertEquals(1, services.size());
    assertEquals("", standalone.configurationName);
    assertEquals("", standalone.expectedConfigurationName);
    assertTrue(standalone.visible);
  }

  /**
   * Performs the group names are unique ignoring case operation.
   */
  @Test
  public void groupNamesAreUniqueIgnoringCase()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();

    assertTrue(settings.addGroup("Backend"));
    assertFalse(settings.addGroup("backend"));

    assertEquals(List.of("Backend"), settings.groupNames());
  }

  /**
   * Performs the only empty groups can be removed operation.
   */
  @Test
  public void onlyEmptyGroupsCanBeRemoved()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    state.groups.addAll(List.of("Backend", "Frontend"));
    ServiceLauncherSettings.LauncherConfiguration service =
      new ServiceLauncherSettings.LauncherConfiguration("Orders API", 0);
    service.group = "Backend";
    state.services.add(service);
    settings.loadState(state);

    assertFalse(settings.removeGroup("Backend"));
    assertTrue(settings.removeGroup("Frontend"));

    assertEquals(List.of("Backend"), settings.groupNames());
  }

  /**
   * Removes the d launcher configuration is not recreated from its run configuration.
   */
  @Test
  public void removedLauncherConfigurationIsNotRecreatedFromItsRunConfiguration()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
    ServiceLauncherSettings.LauncherConfiguration service =
      new ServiceLauncherSettings.LauncherConfiguration("Orders API", 0);
    state.services.add(service);
    settings.loadState(state);

    assertTrue(settings.removeService(service.itemId));
    List<ServiceLauncherSettings.LauncherConfiguration> services =
      settings.reconciledWith(List.of("Orders API"));

    assertTrue(services.isEmpty());
  }
}
