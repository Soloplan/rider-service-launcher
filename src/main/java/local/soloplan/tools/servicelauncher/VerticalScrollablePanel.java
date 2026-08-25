//-----------------------------------------------------------------------
// <copyright file="VerticalScrollablePanel.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Represents a vertical scrollable panel.
 */
final class VerticalScrollablePanel extends JPanel implements Scrollable
{
  private static final int SCROLL_INCREMENT = 18;

  /** {@inheritDoc} */
  @Override
  public Dimension getPreferredScrollableViewportSize()
  {
    return getPreferredSize();
  }

  /** {@inheritDoc} */
  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRectangle, int orientation, int direction)
  {
    return SCROLL_INCREMENT;
  }

  /** {@inheritDoc} */
  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRectangle, int orientation, int direction)
  {
    return Math.max(SCROLL_INCREMENT, visibleRectangle.height - SCROLL_INCREMENT);
  }

  /** {@inheritDoc} */
  @Override
  public boolean getScrollableTracksViewportWidth()
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean getScrollableTracksViewportHeight()
  {
    return false;
  }
}
