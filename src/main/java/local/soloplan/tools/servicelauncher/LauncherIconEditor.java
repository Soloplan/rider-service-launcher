//-----------------------------------------------------------------------
// <copyright file="LauncherIconEditor.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Edits and previews the built-in or embedded icon on a draft launcher configuration.
 */
final class LauncherIconEditor extends JPanel
{
  private static final long MAX_ICON_BYTES = 5 * 1024 * 1024;

  private final Project project;
  private final ServiceLauncherSettings.LauncherConfiguration configuration;
  private final JComboBox<String> iconSelector = new JComboBox<>(LauncherIcons.keys());
  private final JBLabel iconPreview = new JBLabel();
  private final JBLabel customIconDescription = new JBLabel();

  /**
   * Creates an icon editor bound to the supplied draft.
   *
   * @param project the Rider project
   * @param configuration the draft that receives icon changes
   */
  LauncherIconEditor(Project project, ServiceLauncherSettings.LauncherConfiguration configuration)
  {
    super(new BorderLayout(0, 8));
    this.project = project;
    this.configuration = configuration;
    iconSelector.setSelectedItem(configuration.icon);
    iconSelector.addActionListener(event -> useSelectedBuiltInIcon());
    add(createIconActions(), BorderLayout.NORTH);
    add(createPreviewPanel(), BorderLayout.CENTER);
    refreshPreview();
  }

  /**
   * Creates the icon actions.
   *
   * @return the create icon actions result
   */
  private JPanel createIconActions()
  {
    JButton chooseIcon = new JButton("Choose image…");
    chooseIcon.addActionListener(actionEvent -> chooseCustomIcon());
    JButton clearIcon = new JButton("Use built-in");
    clearIcon.addActionListener(actionEvent -> useBuiltInIcon());
    JPanel iconActions = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
    iconActions.add(iconSelector);
    iconActions.add(chooseIcon);
    iconActions.add(clearIcon);
    return iconActions;
  }

  /**
   * Creates the preview panel.
   *
   * @return the create preview panel result
   */
  private JPanel createPreviewPanel()
  {
    JPanel previewPanel = new JPanel(new BorderLayout(8, 0));
    previewPanel.add(iconPreview, BorderLayout.WEST);
    previewPanel.add(customIconDescription, BorderLayout.CENTER);
    return previewPanel;
  }

  /**
   * Performs the use selected built in icon operation.
   */
  private void useSelectedBuiltInIcon()
  {
    configuration.icon = String.valueOf(iconSelector.getSelectedItem());
    useBuiltInIcon();
  }

  /**
   * Performs the use built in icon operation.
   */
  private void useBuiltInIcon()
  {
    configuration.customIconName = "";
    configuration.customIconData = "";
    refreshPreview();
  }

  /**
   * Performs the choose custom icon operation.
   */
  private void chooseCustomIcon()
  {
    JFileChooser chooser = createIconChooser();
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
    {
      loadCustomIcon(chooser.getSelectedFile());
    }
  }

  /**
   * Creates the icon chooser.
   *
   * @return the create icon chooser result
   */
  private JFileChooser createIconChooser()
  {
    JFileChooser chooser = new JFileChooser(project.getBasePath());
    chooser.setDialogTitle("Choose service icon");
    chooser.setFileFilter(new FileNameExtensionFilter(
      "Images (SVG, PNG, JPEG, GIF)", "svg", "png", "jpg", "jpeg", "gif"
    ));
    return chooser;
  }

  /**
   * Loads the custom icon.
   *
   * @param file the file
   */
  private void loadCustomIcon(File file)
  {
    try
    {
      byte[] bytes = Files.readAllBytes(file.toPath());
      validateIconSize(bytes);
      configuration.customIconName = file.getName();
      configuration.customIconData = Base64.getEncoder().encodeToString(bytes);
      refreshPreview();
    }
    catch (IOException exception)
    {
      Messages.showErrorDialog(project, exception.getMessage(), "Could Not Load Icon");
    }
  }

  /**
   * Performs the validate icon size operation.
   *
   * @param bytes the bytes
   * @throws IOException if the operation cannot be completed
   */
  private void validateIconSize(byte[] bytes) throws IOException
  {
    if (bytes.length == 0 || bytes.length > MAX_ICON_BYTES)
    {
      throw new IOException("Icons must be between 1 byte and 5 MB.");
    }
  }

  /**
   * Refreshes the preview.
   */
  private void refreshPreview()
  {
    iconPreview.setIcon(LauncherIcons.get(configuration));
    customIconDescription.setText(configuration.customIconData == null || configuration.customIconData.isBlank()
      ? "Built-in: " + configuration.icon
      : configuration.customIconName + " (embedded)");
  }
}
