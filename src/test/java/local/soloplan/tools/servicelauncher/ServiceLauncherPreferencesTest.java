//-----------------------------------------------------------------------
// <copyright file="ServiceLauncherPreferencesTest.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.util.xmlb.XmlSerializer;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies defaults and compatibility behavior of persisted plug-in preferences.
 */
public class ServiceLauncherPreferencesTest
{
  /**
   * Verifies the requested defaults for a new installation.
   */
  @Test
  public void newPreferencesUseStableDefaults()
  {
    // Arrange
    ServiceLauncherPreferences preferences = new ServiceLauncherPreferences();

    // Act / Assert
    assertFalse(preferences.perConfigStartDebugButtons());
    assertTrue(preferences.perConfigRestartStopButtons());
    assertTrue(preferences.showToggleAllButton());
    assertEquals(ServiceLauncherPreferences.DEFAULT_PRIMARY_COLOR, preferences.primaryColorHex());
    assertEquals(
      ServiceLauncherPreferences.StartupAnimation.PRETTY,
      preferences.startupAnimation()
    );
  }

  /**
   * Verifies that malformed known values fall back while unknown future options remain available.
   */
  @Test
  public void malformedAndUnknownOptionsDoNotBreakLoading()
  {
    // Arrange
    ServiceLauncherPreferences.StateData state = new ServiceLauncherPreferences.StateData();
    state.options.put("perConfigRestartStopButtons", "not-a-boolean");
    state.options.put("primaryColor", "not-a-color");
    state.options.put("startupAnimation", "FutureAnimation");
    state.options.put("futureOption", "future-value");
    ServiceLauncherPreferences preferences = new ServiceLauncherPreferences();

    // Act
    preferences.loadState(state);

    // Assert
    assertTrue(preferences.perConfigRestartStopButtons());
    assertEquals(ServiceLauncherPreferences.DEFAULT_PRIMARY_COLOR, preferences.primaryColorHex());
    assertEquals(
      ServiceLauncherPreferences.StartupAnimation.PRETTY,
      preferences.startupAnimation()
    );
    assertEquals("future-value", preferences.getState().options.get("futureOption"));
  }

  /**
   * Verifies that every exposed preference is stored through the independent option map.
   */
  @Test
  public void changedPreferencesRoundTripThroughOptionMap()
  {
    // Arrange
    ServiceLauncherPreferences source = new ServiceLauncherPreferences();
    source.setPerConfigStartDebugButtons(true);
    source.setPerConfigRestartStopButtons(false);
    source.setShowToggleAllButton(false);
    source.setPrimaryColor(new Color(0x12, 0x34, 0x56));
    source.setStartupAnimation(ServiceLauncherPreferences.StartupAnimation.SIMPLE);
    ServiceLauncherPreferences restored = new ServiceLauncherPreferences();

    // Act
    ServiceLauncherPreferences.StateData persisted = XmlSerializer.deserialize(
      XmlSerializer.serialize(source.getState()),
      ServiceLauncherPreferences.StateData.class
    );
    restored.loadState(persisted);

    // Assert
    assertTrue(restored.perConfigStartDebugButtons());
    assertFalse(restored.perConfigRestartStopButtons());
    assertFalse(restored.showToggleAllButton());
    assertEquals("#123456", restored.primaryColorHex());
    assertEquals(ServiceLauncherPreferences.StartupAnimation.SIMPLE, restored.startupAnimation());
  }
}
