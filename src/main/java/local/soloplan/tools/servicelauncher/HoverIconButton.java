//-----------------------------------------------------------------------
// <copyright file="HoverIconButton.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;

import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Represents a hover icon button.
 */
final class HoverIconButton extends JButton
{
  private static final Color HOVER_BACKGROUND = new JBColor(new Color(0xDFE1E5), new Color(0x45474D));
  private static final Color PRESSED_BACKGROUND = new JBColor(new Color(0xC9CCD2), new Color(0x55575E));
  private static final int CORNER_RADIUS = 6;

  /**
   * Defines a size.
   */
  enum Size
  {
    TOOLBAR(34, 30),
    COMPACT(15, 15);

    private final Dimension dimension;

    /**
     * Creates a new {@code Size} instance.
     *
     * @param width the width
     * @param height the height
     */
    Size(int width, int height)
    {
      dimension = new Dimension(width, height);
    }
  }

  /**
   * Creates a new {@code HoverIconButton} instance.
   *
   * @param icon the icon
   * @param tooltip the tooltip
   * @param size the size
   */
  HoverIconButton(Icon icon, String tooltip, Size size)
  {
    super(icon);
    setDisabledIcon(IconLoader.getDisabledIcon(icon));
    setToolTipText(tooltip);
    setFocusable(false);
    setRolloverEnabled(true);
    setContentAreaFilled(false);
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);
    setPreferredSize(size.dimension);
  }

  /** {@inheritDoc} */
  @Override
  protected void paintComponent(Graphics graphics)
  {
    paintInteractionBackground(graphics);
    super.paintComponent(graphics);
  }

  /**
   * Paints the interaction background.
   *
   * @param graphics the graphics
   */
  private void paintInteractionBackground(Graphics graphics)
  {
    ButtonModel model = getModel();
    if (!isEnabled() || (!model.isPressed() && !model.isRollover()))
    {
      return;
    }
    Graphics2D graphics2D = (Graphics2D) graphics.create();
    try
    {
      graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics2D.setColor(model.isPressed() ? PRESSED_BACKGROUND : HOVER_BACKGROUND);
      graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
    }
    finally
    {
      graphics2D.dispose();
    }
  }
}
