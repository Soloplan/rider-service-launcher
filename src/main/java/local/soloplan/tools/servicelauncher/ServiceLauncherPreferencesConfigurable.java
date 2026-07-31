//-----------------------------------------------------------------------
// <copyright file="ServiceLauncherPreferencesConfigurable.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;

/**
 * Provides the Service Launcher page in Rider's application settings.
 */
public final class ServiceLauncherPreferencesConfigurable implements Configurable
{
  private JPanel panel;
  private JBCheckBox perConfigStartDebug;
  private JBCheckBox perConfigRestartStop;
  private JBCheckBox showToggleAll;
  private JButton primaryColor;
  private JComboBox<ServiceLauncherPreferences.StartupAnimation> startupAnimation;
  private Color selectedPrimaryColor;

  /** {@inheritDoc} */
  @Override
  public String getDisplayName()
  {
    return "Service Launcher";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable JComponent createComponent()
  {
    perConfigStartDebug = new JBCheckBox("Show Run and Debug buttons on stopped service cards");
    perConfigRestartStop = new JBCheckBox("Show Restart and Stop buttons on running service cards");
    showToggleAll = new JBCheckBox("Show Select/Deselect All buttons");
    primaryColor = new JButton();
    primaryColor.addActionListener(actionEvent -> choosePrimaryColor());
    startupAnimation = new JComboBox<>(ServiceLauncherPreferences.StartupAnimation.values());
    panel = FormBuilder.createFormBuilder()
      .addComponent(perConfigStartDebug)
      .addComponent(perConfigRestartStop)
      .addComponent(showToggleAll)
      .addLabeledComponent("Primary color:", primaryColor)
      .addLabeledComponent("Service startup animation:", startupAnimation)
      .addComponentFillVertically(new JPanel(), 0)
      .getPanel();
    reset();
    return panel;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isModified()
  {
    ServiceLauncherPreferences preferences = ServiceLauncherPreferences.getInstance();
    return perConfigStartDebug.isSelected() != preferences.perConfigStartDebugButtons()
      || perConfigRestartStop.isSelected() != preferences.perConfigRestartStopButtons()
      || showToggleAll.isSelected() != preferences.showToggleAllButton()
      || !selectedPrimaryColor.equals(preferences.primaryColor())
      || startupAnimation.getSelectedItem() != preferences.startupAnimation();
  }

  /** {@inheritDoc} */
  @Override
  public void apply() throws ConfigurationException
  {
    ServiceLauncherPreferences preferences = ServiceLauncherPreferences.getInstance();
    preferences.setPerConfigStartDebugButtons(perConfigStartDebug.isSelected());
    preferences.setPerConfigRestartStopButtons(perConfigRestartStop.isSelected());
    preferences.setShowToggleAllButton(showToggleAll.isSelected());
    preferences.setPrimaryColor(selectedPrimaryColor);
    preferences.setStartupAnimation(
      (ServiceLauncherPreferences.StartupAnimation) startupAnimation.getSelectedItem()
    );
    preferences.notifyChanged();
  }

  /** {@inheritDoc} */
  @Override
  public void reset()
  {
    ServiceLauncherPreferences preferences = ServiceLauncherPreferences.getInstance();
    perConfigStartDebug.setSelected(preferences.perConfigStartDebugButtons());
    perConfigRestartStop.setSelected(preferences.perConfigRestartStopButtons());
    showToggleAll.setSelected(preferences.showToggleAllButton());
    selectedPrimaryColor = preferences.primaryColor();
    startupAnimation.setSelectedItem(preferences.startupAnimation());
    refreshPrimaryColorButton();
  }

  /** {@inheritDoc} */
  @Override
  public void disposeUIResources()
  {
    panel = null;
    perConfigStartDebug = null;
    perConfigRestartStop = null;
    showToggleAll = null;
    primaryColor = null;
    startupAnimation = null;
  }

  /**
   * Opens the standard color chooser for the primary accent.
   */
  private void choosePrimaryColor()
  {
    Color chosen = JColorChooser.showDialog(panel, "Primary Color", selectedPrimaryColor);
    if (chosen != null)
    {
      selectedPrimaryColor = chosen;
      refreshPrimaryColorButton();
    }
  }

  /**
   * Updates the primary color button's swatch and hexadecimal label.
   */
  private void refreshPrimaryColorButton()
  {
    primaryColor.setText(String.format("#%06X", selectedPrimaryColor.getRGB() & 0xFFFFFF));
    primaryColor.setBackground(selectedPrimaryColor);
    int brightness =
      (selectedPrimaryColor.getRed() * 299 + selectedPrimaryColor.getGreen() * 587
        + selectedPrimaryColor.getBlue() * 114) / 1000;
    primaryColor.setForeground(brightness < 128 ? Color.WHITE : Color.BLACK);
    primaryColor.setOpaque(true);
  }
}
