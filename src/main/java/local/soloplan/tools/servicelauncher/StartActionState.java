//-----------------------------------------------------------------------
// <copyright file="StartActionState.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

/**
 * Represents a start action state.
 */
record StartActionState(boolean starting, boolean canStart)
{
  /**
   * Determines whether enabled.
   *
   * @return whether enabled
   */
  boolean isEnabled()
  {
    return !starting && canStart;
  }
}
