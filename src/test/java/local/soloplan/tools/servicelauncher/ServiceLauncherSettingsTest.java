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
   * Excludes linked services and returning links while leaving standalone display names available.
   */
  @Test
  public void bulkAddOffersOnlyUnclaimedConfigurationNames()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.LauncherConfiguration hidden = new ServiceLauncherSettings.LauncherConfiguration("Orders", 0);
    ServiceLauncherSettings.LauncherConfiguration returning = new ServiceLauncherSettings.LauncherConfiguration("", 1);
    returning.expectedConfigurationName = "Payments";
    ServiceLauncherSettings.LauncherConfiguration standalone = new ServiceLauncherSettings.LauncherConfiguration("", 2);
    standalone.displayName = "Inventory";
    settings.replaceWith(List.of(hidden, returning, standalone));

    assertEquals(List.of("Inventory", "Shipping"),
      settings.unlinkedConfigurationNames(List.of("Orders", "Inventory", "Payments", "Shipping")));
    assertEquals(0, settings.addServicesFromRunConfigurations(List.of("Orders", "Payments"), "Backend"));
    assertEquals(List.of(ServiceLauncherSettings.DEFAULT_GROUP), settings.groupNames());
  }

  /**
   * Persists only the selected additions as visible links at the end of the destination group.
   */
  @Test
  public void bulkAddAppendsVisibleServicesAndPreservesExistingEntriesAfterReload()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    ServiceLauncherSettings.LauncherConfiguration existing = new ServiceLauncherSettings.LauncherConfiguration("", 0);
    existing.displayName = "Standalone";
    existing.group = "Backend";
    existing.visible = true;
    settings.replaceWith(List.of(existing));
    List<String> available = List.of("Inventory", "Orders", "Shipping");

    assertEquals(2, settings.addServicesFromRunConfigurations(List.of("Inventory", "Shipping", "Shipping"), "Backend"));
    assertEquals(0, settings.addServicesFromRunConfigurations(List.of("Inventory", "Shipping"), "Backend"));

    Element serialized = XmlSerializer.serialize(settings.getState());
    ServiceLauncherSettings reloaded = new ServiceLauncherSettings();
    reloaded.loadState(XmlSerializer.deserialize(serialized, ServiceLauncherSettings.StateData.class));
    List<ServiceLauncherSettings.LauncherConfiguration> services = reloaded.reconciledWith(available);
    assertEquals(List.of("Standalone", "Inventory", "Shipping"), services.stream().map(item -> item.displayName).toList());
    assertEquals(List.of("", "Inventory", "Shipping"), services.stream().map(item -> item.configurationName).toList());
    assertEquals(List.of(0, 1, 2), services.stream().map(item -> item.order).toList());
    assertEquals(existing.itemId, services.getFirst().itemId);
    assertEquals(3, services.stream().map(item -> item.itemId).distinct().count());
    assertTrue(services.stream().allMatch(item -> item.visible && "Backend".equals(item.group)));
    assertEquals(List.of("Orders"), reloaded.unlinkedConfigurationNames(available));
  }

  /**
   * Moves cards in both directions and keeps a drop in the same visible slot from moving a hidden card.
   */
  @Test
  public void movingAServiceUsesTheVisibleInsertionSlot()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    settings.addServicesFromRunConfigurations(List.of("First", "Hidden", "Last"), "Backend");
    List<ServiceLauncherSettings.LauncherConfiguration> services = settings.reconciledWith(List.of("First", "Hidden", "Last"));
    ServiceLauncherSettings.LauncherConfiguration first = services.get(0);
    ServiceLauncherSettings.LauncherConfiguration hidden = services.get(1);
    ServiceLauncherSettings.LauncherConfiguration last = services.get(2);
    hidden.visible = false;

    assertFalse(settings.moveService(first.itemId, "Backend", first.itemId));
    assertFalse(settings.moveService(first.itemId, "Backend", last.itemId));
    assertEquals(List.of(first, hidden, last), services);
    assertTrue(settings.moveService(first.itemId, "Backend", null));
    assertEquals(List.of(hidden, last, first), services);
    assertTrue(settings.moveService(first.itemId, "Backend", last.itemId));
    assertEquals(List.of(hidden, first, last), services);
  }

  /**
   * Persists moves into empty and occupied groups without changing the service identity or Rider link.
   */
  @Test
  public void movingAcrossGroupsPreservesTheLinkAfterReload()
  {
    ServiceLauncherSettings settings = new ServiceLauncherSettings();
    settings.addServicesFromRunConfigurations(List.of("Orders"), "Backend");
    settings.addServicesFromRunConfigurations(List.of("Website"), "Frontend");
    settings.addGroup("Empty");
    List<String> available = List.of("Orders", "Website");
    List<ServiceLauncherSettings.LauncherConfiguration> services = settings.reconciledWith(available);
    ServiceLauncherSettings.LauncherConfiguration orders = services.getFirst();
    String ordersId = orders.itemId;
    String websiteId = services.get(1).itemId;

    assertFalse(settings.moveService(ordersId, "Empty", websiteId));
    assertTrue(settings.moveService(ordersId, "Empty", null));
    assertEquals("Empty", orders.group);
    assertTrue(settings.moveService(ordersId, "Frontend", websiteId));

    Element serialized = XmlSerializer.serialize(settings.getState());
    ServiceLauncherSettings reloaded = new ServiceLauncherSettings();
    reloaded.loadState(XmlSerializer.deserialize(serialized, ServiceLauncherSettings.StateData.class));
    var moved = reloaded.reconciledWith(available);
    assertEquals(List.of(ordersId, websiteId), moved.stream().map(item -> item.itemId).toList());
    assertEquals(available, moved.stream().map(item -> item.configurationName).toList());
    assertEquals(List.of(0, 1), moved.stream().map(item -> item.order).toList());
    assertTrue(moved.stream().allMatch(item -> "Frontend".equals(item.group)));
    assertEquals(List.of("Backend", "Frontend", "Empty"), reloaded.groupNames());
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
