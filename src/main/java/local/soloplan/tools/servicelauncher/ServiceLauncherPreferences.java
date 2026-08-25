//-----------------------------------------------------------------------
// <copyright file="ServiceLauncherPreferences.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores application-level Service Launcher preferences independently from project launcher configurations.
 */
@Service(Service.Level.APP)
@State(name = "ServiceLauncherPreferences", storages = @Storage("serviceLauncherPreferences.xml"))
public final class ServiceLauncherPreferences
  implements PersistentStateComponent<ServiceLauncherPreferences.StateData>
{
  static final String DEFAULT_PRIMARY_COLOR = "#E8007F";

  private static final String PER_CONFIG_START_DEBUG = "perConfigStartDebugButtons";
  private static final String PER_CONFIG_RESTART_STOP = "perConfigRestartStopButtons";
  private static final String SHOW_TOGGLE_ALL = "showToggleAllButton";
  private static final String PRIMARY_COLOR = "primaryColor";
  private static final String STARTUP_ANIMATION = "startupAnimation";

  static final Topic<Listener> TOPIC =
    Topic.create("Service Launcher preferences changed", Listener.class);

  private StateData state = new StateData();

  /**
   * Contains persisted options as stable string key-value pairs.
   */
  public static final class StateData
  {
    public Map<String, String> options = new LinkedHashMap<>();
  }

  /**
   * Describes the available service startup presentations.
   */
  enum StartupAnimation
  {
    PRETTY("Pretty"),
    SIMPLE("Simple");

    private final String displayName;

    /**
     * Creates a startup animation option.
     *
     * @param displayName the user-facing option name
     */
    StartupAnimation(String displayName)
    {
      this.displayName = displayName;
    }

    /**
     * Resolves a persisted option and falls back to the default presentation.
     *
     * @param value the persisted option value
     * @return the resolved startup animation
     */
    static StartupAnimation from(String value)
    {
      if (value != null)
      {
        for (StartupAnimation animation : values())
        {
          if (animation.name().equalsIgnoreCase(value))
          {
            return animation;
          }
        }
      }
      return PRETTY;
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return displayName;
    }
  }

  /**
   * Receives preference changes made through the Rider settings page.
   */
  interface Listener
  {
    /**
     * Applies the latest preferences to an open launcher.
     */
    void preferencesChanged();
  }

  /**
   * Returns the application-level preferences service.
   *
   * @return the preferences service
   */
  static ServiceLauncherPreferences getInstance()
  {
    return ApplicationManager.getApplication().getService(ServiceLauncherPreferences.class);
  }

  /** {@inheritDoc} */
  @Override
  public StateData getState()
  {
    return state;
  }

  /** {@inheritDoc} */
  @Override
  public void loadState(@NotNull StateData state)
  {
    StateData loaded = new StateData();
    if (state.options != null)
    {
      state.options.forEach((key, value) ->
      {
        if (key != null && value != null)
        {
          loaded.options.put(key, value);
        }
      });
    }
    this.state = loaded;
  }

  /**
   * Determines whether stopped cards show their Run and Debug buttons.
   *
   * @return whether per-configuration start buttons are enabled
   */
  boolean perConfigStartDebugButtons()
  {
    return booleanOption(PER_CONFIG_START_DEBUG, false);
  }

  /**
   * Determines whether running cards show their Restart and Stop buttons.
   *
   * @return whether per-configuration running controls are enabled
   */
  boolean perConfigRestartStopButtons()
  {
    return booleanOption(PER_CONFIG_RESTART_STOP, true);
  }

  /**
   * Determines whether global and category selection toggle buttons are shown.
   *
   * @return whether selection toggle buttons are shown
   */
  boolean showToggleAllButton()
  {
    return booleanOption(SHOW_TOGGLE_ALL, true);
  }

  /**
   * Returns the configured primary accent color.
   *
   * @return the primary accent color
   */
  Color primaryColor()
  {
    return Color.decode(primaryColorHex());
  }

  /**
   * Returns the normalized persisted primary color.
   *
   * @return a six-digit hexadecimal color prefixed with {@code #}
   */
  String primaryColorHex()
  {
    String value = state.options.get(PRIMARY_COLOR);
    if (value == null)
    {
      return DEFAULT_PRIMARY_COLOR;
    }
    String normalized = value.startsWith("#") ? value : "#" + value;
    return normalized.matches("#[0-9a-fA-F]{6}") ? normalized.toUpperCase() : DEFAULT_PRIMARY_COLOR;
  }

  /**
   * Returns the configured service startup presentation.
   *
   * @return the startup animation option
   */
  StartupAnimation startupAnimation()
  {
    return StartupAnimation.from(state.options.get(STARTUP_ANIMATION));
  }

  /**
   * Updates the per-configuration start controls preference.
   *
   * @param enabled whether the controls should be shown
   */
  void setPerConfigStartDebugButtons(boolean enabled)
  {
    state.options.put(PER_CONFIG_START_DEBUG, Boolean.toString(enabled));
  }

  /**
   * Updates the per-configuration running controls preference.
   *
   * @param enabled whether the controls should be shown
   */
  void setPerConfigRestartStopButtons(boolean enabled)
  {
    state.options.put(PER_CONFIG_RESTART_STOP, Boolean.toString(enabled));
  }

  /**
   * Updates the selection toggle preference.
   *
   * @param enabled whether selection toggle buttons should be shown
   */
  void setShowToggleAllButton(boolean enabled)
  {
    state.options.put(SHOW_TOGGLE_ALL, Boolean.toString(enabled));
  }

  /**
   * Updates the primary color preference.
   *
   * @param color the selected primary color
   */
  void setPrimaryColor(Color color)
  {
    state.options.put(PRIMARY_COLOR, String.format("#%06X", color.getRGB() & 0xFFFFFF));
  }

  /**
   * Updates the startup animation preference.
   *
   * @param animation the selected startup animation
   */
  void setStartupAnimation(StartupAnimation animation)
  {
    state.options.put(STARTUP_ANIMATION, animation.name());
  }

  /**
   * Notifies open launcher panels after preferences have been applied.
   */
  void notifyChanged()
  {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).preferencesChanged();
  }

  /**
   * Reads a boolean option without allowing malformed persisted values to change its default.
   *
   * @param key the stable option key
   * @param defaultValue the fallback value
   * @return the persisted boolean or its fallback
   */
  private boolean booleanOption(String key, boolean defaultValue)
  {
    String value = state.options.get(key);
    if ("true".equalsIgnoreCase(value))
    {
      return true;
    }
    if ("false".equalsIgnoreCase(value))
    {
      return false;
    }
    return defaultValue;
  }
}
