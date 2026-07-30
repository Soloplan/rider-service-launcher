//-----------------------------------------------------------------------
// <copyright file="LauncherConfigurationDialogRequest.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

/**
 * Represents a launcher configuration dialog request.
 */
record LauncherConfigurationDialogRequest(
  ServiceLauncherSettings.LauncherConfiguration source,
  LauncherConfigurationEditorOptions options,
  Purpose purpose
)
{
  /**
   * Defines a purpose.
   */
  enum Purpose
  {
    CREATE,
    EDIT
  }

  /**
   * Creates the operation.
   *
   * @param source the source
   * @param options the options
   * @return the create result
   */
  static LauncherConfigurationDialogRequest create(
    ServiceLauncherSettings.LauncherConfiguration source,
    LauncherConfigurationEditorOptions options
  )
  {
    return new LauncherConfigurationDialogRequest(source, options, Purpose.CREATE);
  }

  /**
   * Edits the operation.
   *
   * @param source the source
   * @param options the options
   * @return the edit result
   */
  static LauncherConfigurationDialogRequest edit(
    ServiceLauncherSettings.LauncherConfiguration source,
    LauncherConfigurationEditorOptions options
  )
  {
    return new LauncherConfigurationDialogRequest(source, options, Purpose.EDIT);
  }

  /**
   * Determines whether creating.
   *
   * @return whether creating
   */
  boolean isCreating()
  {
    return purpose == Purpose.CREATE;
  }
}
