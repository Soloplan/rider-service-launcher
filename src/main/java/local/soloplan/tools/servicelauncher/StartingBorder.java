//-----------------------------------------------------------------------
// <copyright file="StartingBorder.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import javax.swing.border.Border;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.function.DoubleSupplier;

/**
 * Represents a starting border.
 */
final class StartingBorder implements Border
{
  private static final int WIDTH = 2;
  private static final int MITER_LIMIT = 10;
  private static final int CORNER_RADIUS = 6;
  private static final float SEGMENT_RATIO = 0.20f;
  private static final float GAP_RATIO = 0.30f;

  private final Color color;
  private final DoubleSupplier offsetSupplier;

  /**
   * Creates a new {@code StartingBorder} instance.
   *
   * @param color the color
   * @param offsetSupplier the offset supplier
   */
  StartingBorder(Color color, DoubleSupplier offsetSupplier)
  {
    this.color = color;
    this.offsetSupplier = offsetSupplier;
  }

  /** {@inheritDoc} */
  @Override
  public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height)
  {
    if (width <= WIDTH * 2 || height <= WIDTH * 2)
    {
      return;
    }
    Graphics2D graphics2D = (Graphics2D) graphics.create();
    try
    {
      paintBorder(graphics2D, x, y, width, height);
    }
    finally
    {
      graphics2D.dispose();
    }
  }

  /**
   * Paints the border.
   *
   * @param graphics the graphics
   * @param x the x
   * @param y the y
   * @param width the width
   * @param height the height
   */
  private void paintBorder(Graphics2D graphics, int x, int y, int width, int height)
  {
    float perimeter = Math.max(1, 2f * ((width - WIDTH * 2f) + (height - WIDTH * 2f)));
    float segmentLength = perimeter * SEGMENT_RATIO;
    float gapLength = perimeter * GAP_RATIO;
    float offset = (float) offsetSupplier.getAsDouble() % (segmentLength + gapLength);
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setStroke(createStroke(segmentLength, gapLength, offset));
    graphics.setColor(color);
    graphics.drawRoundRect(x + 1, y + 1, width - 3, height - 3, CORNER_RADIUS, CORNER_RADIUS);
  }

  /**
   * Creates the stroke.
   *
   * @param segmentLength the segment length
   * @param gapLength the gap length
   * @param offset the offset
   * @return the create stroke result
   */
  private BasicStroke createStroke(float segmentLength, float gapLength, float offset)
  {
    return new BasicStroke(
      WIDTH,
      BasicStroke.CAP_ROUND,
      BasicStroke.JOIN_ROUND,
      MITER_LIMIT,
      new float[]
      {segmentLength, gapLength
      },
      offset
    );
  }

  /** {@inheritDoc} */
  @Override
  public Insets getBorderInsets(Component component)
  {
    return new Insets(WIDTH, WIDTH, WIDTH, WIDTH);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isBorderOpaque()
  {
    return false;
  }
}
