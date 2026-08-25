//-----------------------------------------------------------------------
// <copyright file="HighQualityImageIcon.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;

/**
 * Represents a high quality image icon.
 */
final class HighQualityImageIcon implements Icon
{
  private final Image source;
  private final int width;
  private final int height;

  /**
   * Creates a new {@code HighQualityImageIcon} instance.
   *
   * @param source the source
   * @param maximumSize the maximum size
   */
  HighQualityImageIcon(Image source, int maximumSize)
  {
    this.source = source;
    int sourceWidth = Math.max(1, source.getWidth(null));
    int sourceHeight = Math.max(1, source.getHeight(null));
    double scale = Math.min((double) maximumSize / sourceWidth, (double) maximumSize / sourceHeight);
    width = Math.max(1, (int) Math.round(sourceWidth * scale));
    height = Math.max(1, (int) Math.round(sourceHeight * scale));
  }

  /** {@inheritDoc} */
  @Override
  public void paintIcon(Component component, Graphics graphics, int x, int y)
  {
    Graphics2D graphics2D = (Graphics2D) graphics.create();
    try
    {
      configureRendering(graphics2D);
      graphics2D.drawImage(source, x, y, width, height, component);
    }
    finally
    {
      graphics2D.dispose();
    }
  }

  /**
   * Configures the rendering.
   *
   * @param graphics the graphics
   */
  private void configureRendering(Graphics2D graphics)
  {
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  }

  /** {@inheritDoc} */
  @Override
  public int getIconWidth()
  {
    return width;
  }

  /** {@inheritDoc} */
  @Override
  public int getIconHeight()
  {
    return height;
  }
}
