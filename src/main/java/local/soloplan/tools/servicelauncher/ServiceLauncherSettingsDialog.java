//-----------------------------------------------------------------------
// <copyright file="ServiceLauncherSettingsDialog.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a service launcher settings dialog.
 */
final class ServiceLauncherSettingsDialog extends DialogWrapper
{
  private final Project project;
  private final LauncherConfigurationTableModel model;

  /**
   * Creates a new {@code ServiceLauncherSettingsDialog} instance.
   *
   * @param project the project
   * @param source the source
   */
  ServiceLauncherSettingsDialog(Project project, List<ServiceLauncherSettings.LauncherConfiguration> source)
  {
    super(project);
    this.project = project;
    model = new LauncherConfigurationTableModel(new ArrayList<>(
      source.stream().map(ServiceLauncherSettings.LauncherConfiguration::copy).toList()
    ));
    setTitle("Customize Service Launcher");
    setOKButtonText("Apply");
    init();
  }

  /** {@inheritDoc} */
  @Override
  protected @Nullable JComponent createCenterPanel()
  {
    JBTable table = new JBTable(model);
    table.setRowHeight(26);
    table.setShowGrid(false);
    table.setStriped(true);
    table.getColumnModel().getColumn(0).setPreferredWidth(55);
    table.getColumnModel().getColumn(1).setPreferredWidth(190);
    table.getColumnModel().getColumn(2).setPreferredWidth(190);
    table.getColumnModel().getColumn(3).setPreferredWidth(120);
    table.getColumnModel().getColumn(4).setPreferredWidth(150);
    table.getColumnModel().getColumn(5).setPreferredWidth(55);

    JPanel decorated = ToolbarDecorator.createDecorator(table)
      .disableAddAction()
      .setRemoveAction(button -> removeSelected(table))
      .setMoveUpAction(button -> move(table, -1))
      .setMoveDownAction(button -> move(table, 1))
      .createPanel();
    decorated.setPreferredSize(new Dimension(900, 430));

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    JButton showAll = new JButton("Show all");
    showAll.addActionListener(actionEvent -> model.setAllVisible(true));
    JButton hideAll = new JButton("Hide all");
    hideAll.addActionListener(actionEvent -> model.setAllVisible(false));
    JButton editSelected = new JButton("Edit selected…");
    editSelected.addActionListener(actionEvent -> editSelected(table));
    JButton importButton = new JButton("Import…");
    importButton.addActionListener(actionEvent -> importConfiguration());
    JButton exportButton = new JButton("Export…");
    exportButton.addActionListener(actionEvent -> exportConfiguration());
    actions.add(showAll);
    actions.add(hideAll);
    actions.add(editSelected);
    actions.add(importButton);
    actions.add(exportButton);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(actions, BorderLayout.NORTH);
    panel.add(decorated, BorderLayout.CENTER);
    return panel;
  }

  /**
   * Edits the selected.
   *
   * @param table the table
   */
  private void editSelected(JBTable table)
  {
    int selectedRow = table.getSelectedRow();
    if (selectedRow < 0)
    {
      Messages.showInfoMessage(project, "Select a launcher item first.", "Service Launcher");
      return;
    }
    ServiceLauncherSettings.LauncherConfiguration source = model.configurationAt(selectedRow);
    Set<String> groups = new LinkedHashSet<>(ServiceLauncherSettings.getInstance(project).groupNames());
    groups.addAll(model.groupNames());
    List<String> configurations = RunManager.getInstance(project).getAllSettings().stream()
      .filter(settings -> !settings.isTemporary())
      .map(RunnerAndConfigurationSettings::getName)
      .sorted(String.CASE_INSENSITIVE_ORDER)
      .toList();
    LauncherConfigurationEditorOptions options =
      new LauncherConfigurationEditorOptions(groups, configurations, model.getRowCount());
    LauncherConfigurationDialogRequest request = LauncherConfigurationDialogRequest.edit(source, options);
    LauncherConfigurationDialog dialog = new LauncherConfigurationDialog(project, request);
    if (dialog.showAndGet())
    {
      model.replaceItem(dialog.getConfiguration());
    }
  }

  /**
   * Moves the operation.
   *
   * @param table the table
   * @param delta the delta
   */
  private void move(JBTable table, int delta)
  {
    int selectedRow = table.getSelectedRow();
    if (selectedRow < 0)
    {
      return;
    }
    int target = model.moveWithinGroup(selectedRow, delta);
    if (target < 0)
    {
      return;
    }
    table.getSelectionModel().setSelectionInterval(target, target);
  }

  /**
   * Removes the selected.
   *
   * @param table the table
   */
  private void removeSelected(JBTable table)
  {
    int selectedRow = table.getSelectedRow();
    if (selectedRow < 0)
    {
      return;
    }
    int nextRow = model.removeAt(selectedRow);
    if (nextRow >= 0)
    {
      table.getSelectionModel().setSelectionInterval(nextRow, nextRow);
    }
  }

  /**
   * Returns the configurations.
   *
   * @return the configurations
   */
  List<ServiceLauncherSettings.LauncherConfiguration> getConfigurations()
  {
    return model.configurations();
  }

  /**
   * Exports the configuration.
   */
  private void exportConfiguration()
  {
    JFileChooser chooser = chooser("Export Service Launcher Configuration");
    chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), "services." + LauncherConfigurationIO.FILE_EXTENSION));
    if (chooser.showSaveDialog(getContentPanel()) != JFileChooser.APPROVE_OPTION)
    {
      return;
    }
    try
    {
      LauncherConfigurationIO.exportTo(ensureExtension(chooser.getSelectedFile()), model.configurations());
    }
    catch (IOException exception)
    {
      Messages.showErrorDialog(project, exception.getMessage(), "Could Not Export Configuration");
    }
  }

  /**
   * Imports the configuration.
   */
  private void importConfiguration()
  {
    JFileChooser chooser = chooser("Import Service Launcher Configuration");
    if (chooser.showOpenDialog(getContentPanel()) != JFileChooser.APPROVE_OPTION)
    {
      return;
    }
    try
    {
      List<ServiceLauncherSettings.LauncherConfiguration> imported =
        LauncherConfigurationIO.importFrom(chooser.getSelectedFile());
      model.replaceWith(LauncherConfigurationIO.merge(model.configurations(), imported));
    }
    catch (IOException exception)
    {
      Messages.showErrorDialog(project, exception.getMessage(), "Could Not Import Configuration");
    }
  }

  /**
   * Returns the result of chooser.
   *
   * @param title the title
   * @return the chooser result
   */
  private JFileChooser chooser(String title)
  {
    JFileChooser chooser = new JFileChooser(project.getBasePath());
    chooser.setDialogTitle(title);
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
      "Service Launcher export (*.service-launcher.xml)", "xml"
    ));
    return chooser;
  }

  /**
   * Ensures the extension.
   *
   * @param file the file
   * @return the ensure extension result
   */
  private File ensureExtension(File file)
  {
    return file.getName().endsWith("." + LauncherConfigurationIO.FILE_EXTENSION)
      ? file
      : new File(file.getParentFile(), file.getName() + "." + LauncherConfigurationIO.FILE_EXTENSION);
  }

}
