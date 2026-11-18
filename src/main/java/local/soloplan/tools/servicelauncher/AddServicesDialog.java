//-----------------------------------------------------------------------
// <copyright file="AddServicesDialog.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Lets the user add launcher services from several Rider run configurations at once.
 */
final class AddServicesDialog extends DialogWrapper
{
  private final ElementsChooser<String> configurations;
  private final ComboBox<String> group;

  /**
   * Creates a picker with all configurations initially unchecked.
   *
   * @param project the Rider project
   * @param configurationNames the available, unlinked run configuration names
   * @param groupNames the destination groups in launcher order
   */
  AddServicesDialog(Project project, List<String> configurationNames, List<String> groupNames)
  {
    super(project);
    configurations = new ElementsChooser<>(configurationNames, false);
    group = new ComboBox<>(groupNames.toArray(String[]::new));
    setTitle("Add Services from Rider");
    setOKButtonText("Add Selected Services");
    init();
    setOKActionEnabled(false);
    configurations.addElementsMarkListener((String name, boolean marked) ->
      setOKActionEnabled(!configurations.getMarkedElements().isEmpty()));
  }

  /** {@inheritDoc} */
  @Override
  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
    JBLabel groupLabel = new JBLabel("Add to group:");
    groupLabel.setLabelFor(group);
    JPanel destination = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    destination.add(groupLabel, BorderLayout.WEST);
    destination.add(group, BorderLayout.CENTER);

    JButton selectAll = new JButton("Select All");
    selectAll.addActionListener(event -> configurations.setAllElementsMarked(true));
    JButton clear = new JButton("Clear Selection");
    clear.addActionListener(event -> configurations.setAllElementsMarked(false));
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    actions.add(selectAll);
    actions.add(clear);

    configurations.setPreferredSize(JBUI.size(450, 320));
    panel.add(destination, BorderLayout.NORTH);
    panel.add(configurations, BorderLayout.CENTER);
    panel.add(actions, BorderLayout.SOUTH);
    return panel;
  }

  /**
   * Returns the checked run configuration names.
   *
   * @return the configurations chosen for addition
   */
  List<String> selectedConfigurations()
  {
    return configurations.getMarkedElements();
  }

  /**
   * Returns the destination group.
   *
   * @return the selected group name
   */
  String selectedGroup()
  {
    return group.getItemAt(group.getSelectedIndex());
  }
}
