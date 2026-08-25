//-----------------------------------------------------------------------
// <copyright file="SimpleStartingIcon.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import javax.swing.Icon;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.DoubleSupplier;

/**
 * Displays a small, low-frequency startup spinner.
 */
final class SimpleStartingIcon implements Icon
{
  private static final int SIZE = 14;
  private static final int STROKE_WIDTH = 2;
  private static final double ANGLE_MULTIPLIER = 3.5;

  private final Color color;
  private final DoubleSupplier offsetSupplier;

  /**
   * Creates a new {@code SimpleStartingIcon} instance.
   *
   * @param color the spinner color
   * @param offsetSupplier supplies the current animation offset
   */
  SimpleStartingIcon(Color color, DoubleSupplier offsetSupplier)
  {
    this.color = color;
    this.offsetSupplier = offsetSupplier;
  }

  /** {@inheritDoc} */
  @Override
  public void paintIcon(Component component, Graphics graphics, int x, int y)
  {
    Graphics2D graphics2D = (Graphics2D) graphics.create();
    try
    {
      graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics2D.setStroke(new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      graphics2D.setColor(color);
      graphics2D.setComposite(AlphaComposite.SrcOver.derive(0.22f));
      graphics2D.drawOval(x + 2, y + 2, SIZE - 5, SIZE - 5);

      int angle = (int) Math.round(offsetSupplier.getAsDouble() * ANGLE_MULTIPLIER) % 360;
      graphics2D.setComposite(AlphaComposite.SrcOver);
      graphics2D.drawArc(x + 2, y + 2, SIZE - 5, SIZE - 5, 90 - angle, 105);
    }
    finally
    {
      graphics2D.dispose();
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getIconWidth()
  {
    return SIZE;
  }

  /** {@inheritDoc} */
  @Override
  public int getIconHeight()
  {
    return SIZE;
  }
}
