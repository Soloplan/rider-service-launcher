//-----------------------------------------------------------------------
// <copyright file="LauncherConfigurationDialog.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Represents a launcher configuration dialog.
 */
final class LauncherConfigurationDialog extends DialogWrapper
{
  private static final long MAX_ICON_BYTES = 5 * 1024 * 1024;
  private static final String NOT_LINKED = "<Not linked>";

  private final Project project;
  private final ServiceLauncherSettings.LauncherConfiguration configuration;
  private final JBTextField displayNameField = new JBTextField();
  private final JComboBox<String> runConfigurationSelector;
  private final JComboBox<String> groupSelector;
  private final JComboBox<String> iconSelector = new JComboBox<>(LauncherIcons.keys());
  private final JSpinner orderSpinner;
  private final JBLabel iconPreview = new JBLabel();
  private final JBLabel customIconDescription = new JBLabel();

  /**
   * Creates a new {@code LauncherConfigurationDialog} instance.
   *
   * @param project the project
   * @param request the request
   */
  LauncherConfigurationDialog(Project project, LauncherConfigurationDialogRequest request)
  {
    super(project);
    this.project = project;
    ServiceLauncherSettings.LauncherConfiguration source = request.source();
    LauncherConfigurationEditorOptions options = request.options();
    configuration = source.copy();
    groupSelector = createGroupSelector(options);
    runConfigurationSelector = createRunConfigurationSelector(options);
    orderSpinner = createOrderSpinner(options.maximumOrder());
    configureDialog(request);
    init();
  }

  /**
   * Creates the group selector.
   *
   * @param options the options
   * @return the create group selector result
   */
  private JComboBox<String> createGroupSelector(LauncherConfigurationEditorOptions options)
  {
    DefaultComboBoxModel<String> groupModel = new DefaultComboBoxModel<>();
    options.groups().stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(groupModel::addElement);
    if (groupModel.getIndexOf(ServiceLauncherSettings.normalizeGroup(configuration.group)) < 0)
    {
      groupModel.addElement(ServiceLauncherSettings.normalizeGroup(configuration.group));
    }
    JComboBox<String> selector = new JComboBox<>(groupModel);
    selector.setEditable(true);
    return selector;
  }

  /**
   * Creates the run configuration selector.
   *
   * @param options the options
   * @return the create run configuration selector result
   */
  private JComboBox<String> createRunConfigurationSelector(LauncherConfigurationEditorOptions options)
  {
    DefaultComboBoxModel<String> configurationModel = new DefaultComboBoxModel<>();
    configurationModel.addElement(NOT_LINKED);
    options.runConfigurations().stream()
      .sorted(String.CASE_INSENSITIVE_ORDER)
      .forEach(configurationModel::addElement);
    JComboBox<String> selector = new JComboBox<>(configurationModel);
    selector.setSelectedItem(
      configuration.configurationName == null || configuration.configurationName.isBlank()
        ? NOT_LINKED
        : configuration.configurationName
    );
    return selector;
  }

  /**
   * Creates the order spinner.
   *
   * @param requestedMaximum the requested maximum
   * @return the create order spinner result
   */
  private JSpinner createOrderSpinner(int requestedMaximum)
  {
    int maximumOrder = Math.max(1, requestedMaximum + 1);
    SpinnerNumberModel model =
      new SpinnerNumberModel(Math.max(0, configuration.order + 1), 1, maximumOrder, 1);
    return new JSpinner(model);
  }

  /**
   * Configures the dialog.
   *
   * @param request the request
   */
  private void configureDialog(LauncherConfigurationDialogRequest request)
  {
    String title = request.isCreating() ? "Create Service" : "Edit " + displayName(request.source());
    setTitle(title);
    setOKButtonText(request.isCreating() ? "Create" : "Apply");
  }

  /**
   * Returns the result of display name.
   *
   * @param source the source
   * @return the display name result
   */
  private String displayName(ServiceLauncherSettings.LauncherConfiguration source)
  {
    if (source.displayName != null && !source.displayName.isBlank())
    {
      return source.displayName;
    }
    if (source.configurationName != null && !source.configurationName.isBlank())
    {
      return source.configurationName;
    }
    return source.expectedConfigurationName == null || source.expectedConfigurationName.isBlank()
      ? "unlinked service"
      : source.expectedConfigurationName;
  }

  /** {@inheritDoc} */
  @Override
  protected @Nullable JComponent createCenterPanel()
  {
    populateFields();
    JPanel panel = createFormPanel();
    refreshPreview();
    return panel;
  }

  /**
   * Performs the populate fields operation.
   */
  private void populateFields()
  {
    displayNameField.setText(configuration.displayName);
    groupSelector.setSelectedItem(ServiceLauncherSettings.normalizeGroup(configuration.group));
    iconSelector.setSelectedItem(configuration.icon);
    iconSelector.addActionListener(actionEvent -> useSelectedBuiltInIcon());
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
    JPanel iconActions = new JPanel();
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
   * Creates the form panel.
   *
   * @return the create form panel result
   */
  private JPanel createFormPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    addRow(panel, 0, "Run configuration", runConfigurationSelector);
    addRow(panel, 1, "Display name", displayNameField);
    addRow(panel, 2, "Group", groupSelector);
    addRow(panel, 3, "Order in group", orderSpinner);
    addRow(panel, 4, "Icon", createIconActions());
    addRow(panel, 5, "Preview", createPreviewPanel());
    panel.setPreferredSize(new Dimension(620, 260));
    return panel;
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
   * Adds the row.
   *
   * @param panel the panel
   * @param row the row
   * @param label the label
   * @param component the component
   */
  private void addRow(JPanel panel, int row, String label, JComponent component)
  {
    GridBagConstraints labelConstraints = new GridBagConstraints();
    labelConstraints.gridx = 0;
    labelConstraints.gridy = row;
    labelConstraints.anchor = GridBagConstraints.WEST;
    labelConstraints.insets = new Insets(6, 4, 6, 12);
    panel.add(new JBLabel(label), labelConstraints);

    GridBagConstraints componentConstraints = new GridBagConstraints();
    componentConstraints.gridx = 1;
    componentConstraints.gridy = row;
    componentConstraints.weightx = 1;
    componentConstraints.fill = GridBagConstraints.HORIZONTAL;
    componentConstraints.insets = new Insets(6, 4, 6, 4);
    panel.add(component, componentConstraints);
  }

  /**
   * Performs the choose custom icon operation.
   */
  private void chooseCustomIcon()
  {
    JFileChooser chooser = createIconChooser();
    if (chooser.showOpenDialog(getContentPanel()) == JFileChooser.APPROVE_OPTION)
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
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
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

  /**
   * Returns the configuration.
   *
   * @return the configuration
   */
  ServiceLauncherSettings.LauncherConfiguration getConfiguration()
  {
    String selectedConfiguration = String.valueOf(runConfigurationSelector.getSelectedItem());
    updateRunConfigurationLink(selectedConfiguration);
    updatePresentation();
    return configuration;
  }

  /**
   * Updates the run configuration link.
   *
   * @param selectedConfiguration the selected configuration
   */
  private void updateRunConfigurationLink(String selectedConfiguration)
  {
    if (NOT_LINKED.equals(selectedConfiguration))
    {
      if (configuration.configurationName != null && !configuration.configurationName.isBlank())
      {
        configuration.expectedConfigurationName = configuration.configurationName;
      }
      configuration.configurationName = "";
    }
    else
    {
      configuration.configurationName = selectedConfiguration;
      configuration.expectedConfigurationName = "";
    }
  }

  /**
   * Updates the presentation.
   */
  private void updatePresentation()
  {
    configuration.displayName = displayNameField.getText().trim();
    configuration.group = ServiceLauncherSettings.normalizeGroup(String.valueOf(groupSelector.getEditor().getItem()));
    configuration.order = Math.max(0, ((Number) orderSpinner.getValue()).intValue() - 1);
  }
}
