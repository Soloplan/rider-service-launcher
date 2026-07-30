//-----------------------------------------------------------------------
// <copyright file="LauncherConfigurationEditorOptions.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import java.util.Collection;
import java.util.List;

/**
 * Represents a launcher configuration editor options.
 */
record LauncherConfigurationEditorOptions(
  List<String> groups,
  List<String> runConfigurations,
  int maximumOrder
)
{
  /**
   * Creates a new {@code LauncherConfigurationEditorOptions} instance.
   *
   * @param groups the groups
   * @param runConfigurations the run configurations
   * @param maximumOrder the maximum order
   */
  LauncherConfigurationEditorOptions(
    Collection<String> groups,
    Collection<String> runConfigurations,
    int maximumOrder
  )
  {
    this(List.copyOf(groups), List.copyOf(runConfigurations), maximumOrder);
  }
}
