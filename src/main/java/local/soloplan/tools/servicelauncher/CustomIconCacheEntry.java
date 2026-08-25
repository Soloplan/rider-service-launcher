//-----------------------------------------------------------------------
// <copyright file="CustomIconCacheEntry.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import javax.swing.Icon;

/**
 * Represents a custom icon cache entry.
 */
record CustomIconCacheEntry(String name, String data, Icon icon)
{
}
