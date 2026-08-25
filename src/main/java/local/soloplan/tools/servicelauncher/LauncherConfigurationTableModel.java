//-----------------------------------------------------------------------
// <copyright file="LauncherConfigurationTableModel.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import javax.swing.table.AbstractTableModel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a launcher configuration table model.
 */
final class LauncherConfigurationTableModel extends AbstractTableModel
{
  private static final String[] COLUMNS =
  {
    "Show",
    "Run configuration",
    "Display name",
    "Group",
    "Icon",
    "Order"
  };

  private final List<ServiceLauncherSettings.LauncherConfiguration> rows;

  /**
   * Creates a new {@code LauncherConfigurationTableModel} instance.
   *
   * @param rows the rows
   */
  LauncherConfigurationTableModel(List<ServiceLauncherSettings.LauncherConfiguration> rows)
  {
    this.rows = rows;
  }

  /**
   * Returns the result of configuration at.
   *
   * @param row the row
   * @return the configuration at result
   */
  ServiceLauncherSettings.LauncherConfiguration configurationAt(int row)
  {
    return rows.get(row);
  }

  /**
   * Returns the result of configurations.
   *
   * @return the configurations result
   */
  List<ServiceLauncherSettings.LauncherConfiguration> configurations()
  {
    ServiceLauncherSettings.normalizeOrders(rows);
    return List.copyOf(rows);
  }

  /**
   * Returns the result of group names.
   *
   * @return the group names result
   */
  Set<String> groupNames()
  {
    Set<String> groups = new LinkedHashSet<>();
    rows.stream()
      .map(configuration -> ServiceLauncherSettings.normalizeGroup(configuration.group))
      .forEach(groups::add);
    return groups;
  }

  /** {@inheritDoc} */
  @Override
  public int getRowCount()
  {
    return rows.size();
  }

  /** {@inheritDoc} */
  @Override
  public int getColumnCount()
  {
    return COLUMNS.length;
  }

  /** {@inheritDoc} */
  @Override
  public String getColumnName(int column)
  {
    return COLUMNS[column];
  }

  /** {@inheritDoc} */
  @Override
  public Class<?> getColumnClass(int columnIndex)
  {
    return switch (columnIndex)
    {
      case 0 -> Boolean.class;
      case 5 -> Integer.class;
      default -> String.class;
    };
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex)
  {
    return columnIndex != 1 && columnIndex != 4 && columnIndex != 5;
  }

  /** {@inheritDoc} */
  @Override
  public Object getValueAt(int rowIndex, int columnIndex)
  {
    ServiceLauncherSettings.LauncherConfiguration item = rows.get(rowIndex);
    return switch (columnIndex)
    {
      case 0 -> item.visible;
      case 1 -> linkDescription(item);
      case 2 -> item.displayName;
      case 3 -> item.group;
      case 4 -> iconDescription(item);
      case 5 -> item.order + 1;
      default -> "";
    };
  }

  /**
   * Returns the result of link description.
   *
   * @param item the item
   * @return the link description result
   */
  private String linkDescription(ServiceLauncherSettings.LauncherConfiguration item)
  {
    if (item.configurationName != null && !item.configurationName.isBlank())
    {
      return item.configurationName;
    }
    String expected = item.expectedConfigurationName == null ? "" : item.expectedConfigurationName;
    return expected.isBlank() ? "⚠ Not linked" : "⚠ Not linked (expected: " + expected + ")";
  }

  /**
   * Returns the result of icon description.
   *
   * @param item the item
   * @return the icon description result
   */
  private String iconDescription(ServiceLauncherSettings.LauncherConfiguration item)
  {
    return item.customIconData == null || item.customIconData.isBlank()
      ? item.icon
      : "Custom: " + item.customIconName;
  }

  /** {@inheritDoc} */
  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex)
  {
    ServiceLauncherSettings.LauncherConfiguration item = rows.get(rowIndex);
    switch (columnIndex)
    {
      case 0 -> item.visible = Boolean.TRUE.equals(value);
      case 2 -> item.displayName = String.valueOf(value).trim();
      case 3 -> item.group = ServiceLauncherSettings.normalizeGroup(String.valueOf(value));
      default ->
      {
      }
    }
    fireTableCellUpdated(rowIndex, columnIndex);
  }

  /**
   * Sets the all visible.
   *
   * @param visible whether visible
   */
  void setAllVisible(boolean visible)
  {
    rows.forEach(item -> item.visible = visible);
    fireTableDataChanged();
  }

  /**
   * Replaces the with.
   *
   * @param replacement the replacement
   */
  void replaceWith(List<ServiceLauncherSettings.LauncherConfiguration> replacement)
  {
    rows.clear();
    rows.addAll(replacement);
    fireTableDataChanged();
  }

  /**
   * Replaces the item.
   *
   * @param replacement the replacement
   */
  void replaceItem(ServiceLauncherSettings.LauncherConfiguration replacement)
  {
    unlinkDuplicateConfiguration(replacement);
    replaceMatchingItem(replacement);
    ServiceLauncherSettings.normalizeOrders(rows);
    fireTableDataChanged();
  }

  /**
   * Moves the within group.
   *
   * @param selectedRow the selected row
   * @param delta the delta
   * @return the move within group result
   */
  int moveWithinGroup(int selectedRow, int delta)
  {
    String group = ServiceLauncherSettings.normalizeGroup(rows.get(selectedRow).group);
    int targetRow = findTargetRow(selectedRow, delta, group);
    if (targetRow < 0)
    {
      return -1;
    }
    ServiceLauncherSettings.LauncherConfiguration configuration = rows.remove(selectedRow);
    rows.add(targetRow, configuration);
    ServiceLauncherSettings.normalizeOrders(rows);
    fireTableDataChanged();
    return targetRow;
  }

  /**
   * Finds the target row.
   *
   * @param selectedRow the selected row
   * @param delta the delta
   * @param group the group
   * @return the find target row result
   */
  private int findTargetRow(int selectedRow, int delta, String group)
  {
    int targetRow = selectedRow + delta;
    while (targetRow >= 0 && targetRow < rows.size()
      && !group.equals(ServiceLauncherSettings.normalizeGroup(rows.get(targetRow).group)))
      {
      targetRow += delta;
      }
    return targetRow >= 0 && targetRow < rows.size() ? targetRow : -1;
  }

  /**
   * Removes the at.
   *
   * @param selectedRow the selected row
   * @return the remove at result
   */
  int removeAt(int selectedRow)
  {
    rows.remove(selectedRow);
    ServiceLauncherSettings.normalizeOrders(rows);
    fireTableDataChanged();
    return rows.isEmpty() ? -1 : Math.min(selectedRow, rows.size() - 1);
  }

  /**
   * Performs the unlink duplicate configuration operation.
   *
   * @param replacement the replacement
   */
  private void unlinkDuplicateConfiguration(ServiceLauncherSettings.LauncherConfiguration replacement)
  {
    if (replacement.configurationName == null || replacement.configurationName.isBlank())
    {
      return;
    }
    for (ServiceLauncherSettings.LauncherConfiguration item : rows)
    {
      boolean isDifferentItem = !item.itemId.equals(replacement.itemId);
      if (isDifferentItem && replacement.configurationName.equals(item.configurationName))
      {
        item.expectedConfigurationName = item.configurationName;
        item.configurationName = "";
      }
    }
  }

  /**
   * Replaces the matching item.
   *
   * @param replacement the replacement
   */
  private void replaceMatchingItem(ServiceLauncherSettings.LauncherConfiguration replacement)
  {
    for (int index = 0; index < rows.size(); index++)
    {
      if (rows.get(index).itemId.equals(replacement.itemId))
      {
        rows.set(index, replacement);
        return;
      }
    }
  }
}
