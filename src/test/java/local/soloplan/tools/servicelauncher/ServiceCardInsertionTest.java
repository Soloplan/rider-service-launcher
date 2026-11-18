//-----------------------------------------------------------------------
// <copyright file="ServiceCardInsertionTest.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import org.junit.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests the live card layout and preview independently of a running IDE.
 */
public class ServiceCardInsertionTest
{
  private static final int TWO_COLUMN_WIDTH = 366;

  /**
   * Uses the existing launcher width immediately when rebuilding a grid after a drop.
   *
   * @throws Exception if the Swing test cannot run
   */
  @Test
  public void rebuiltGridUsesMultipleColumnsBeforeAnyResizeEvent() throws Exception
  {
    SwingUtilities.invokeAndWait(() ->
    {
      JPanel first = new JPanel();
      JPanel second = new JPanel();
      ServiceCardGrid grid = new ServiceCardGrid(List.of(first, second), true, TWO_COLUMN_WIDTH);

      assertEquals(48, grid.getPreferredSize().height);
      grid.setSize(TWO_COLUMN_WIDTH, grid.getPreferredSize().height);
      grid.doLayout();
      assertEquals(new Rectangle(186, 0, 180, 48), second.getBounds());

      grid.updateColumns(180);
      grid.setSize(180, grid.getPreferredSize().height);
      grid.doLayout();
      assertEquals(new Rectangle(0, 54, 180, 48), second.getBounds());
    });
  }

  /**
   * Reserves the same slot before and after the surrounding cards shift across a row boundary.
   *
   * @throws Exception if the Swing test cannot run
   */
  @Test
  public void previewInAFullRowShiftsTheNextCardToANewRow() throws Exception
  {
    SwingUtilities.invokeAndWait(() ->
    {
      JPanel first = new JPanel();
      JPanel second = new JPanel();
      ServiceCardGrid grid = createGrid(first, second);
      Point pointer = new Point(210, 25);

      assertEquals(1, grid.insertionAt(pointer));
      grid.setPreview(createPreview(), 1);
      grid.doLayout();

      assertEquals(1, grid.insertionAt(pointer));
      assertEquals(new Rectangle(0, 0, 180, 48), first.getBounds());
      assertEquals(new Rectangle(0, 54, 180, 48), second.getBounds());
      assertEquals(102, grid.getPreferredSize().height);
      assertSame(second, grid.cardAfter(1));
    });
  }

  /**
   * Keeps the preview in place near the boundary between two cells.
   *
   * @throws Exception if the Swing test cannot run
   */
  @Test
  public void pointerHasToCrossTheBoundaryToMoveThePreview() throws Exception
  {
    SwingUtilities.invokeAndWait(() ->
    {
      ServiceCardGrid grid = createGrid(new JPanel(), new JPanel());
      grid.setPreview(createPreview(), 0);

      assertEquals(0, grid.insertionAt(new Point(185, 30)));
      assertEquals(1, grid.insertionAt(new Point(192, 30)));
      assertEquals(2, grid.insertionAt(new Point(220, 62)));
    });
  }

  /**
   * Shifts the saved cards while keeping the mouse source attached, then restores their positions on cancellation.
   *
   * @throws Exception if the Swing test cannot run
   */
  @Test
  public void removingPreviewRestoresTheSourceGrid() throws Exception
  {
    SwingUtilities.invokeAndWait(() ->
    {
      JPanel source = new JPanel();
      JPanel next = new JPanel();
      JPanel last = new JPanel();
      ServiceCardGrid grid = createGrid(source, next, last);

      grid.setDraggedCard(source);
      grid.setPreview(createPreview(), 2);
      grid.doLayout();

      assertSame(grid, source.getParent());
      assertTrue(source.getBounds().isEmpty());
      assertEquals(new Rectangle(0, 0, 180, 48), next.getBounds());
      assertEquals(new Rectangle(186, 0, 180, 48), last.getBounds());
      assertNull(grid.cardAfter(2));

      grid.setPreview(null, -1);
      grid.doLayout();
      assertEquals(102, grid.getPreferredSize().height);
      grid.setDraggedCard(null);
      grid.doLayout();
      assertEquals(new Rectangle(0, 0, 180, 48), source.getBounds());
      assertEquals(new Rectangle(0, 54, 180, 48), last.getBounds());
    });
  }

  /**
   * Shows a translucent card in an empty group and restores the short drop area when cleared.
   *
   * @throws Exception if the Swing test cannot run
   */
  @Test
  public void emptyGroupDisplaysAFadedCardPreview() throws Exception
  {
    SwingUtilities.invokeAndWait(() ->
    {
      ServiceCardGrid grid = createGrid();
      assertEquals(24, grid.getPreferredSize().height);
      assertEquals(0, grid.insertionAt(new Point(350, 23)));
      grid.setPreview(createPreview(), 0);
      grid.setSize(TWO_COLUMN_WIDTH, grid.getPreferredSize().height);
      assertEquals(48, grid.getHeight());

      BufferedImage rendered = new BufferedImage(TWO_COLUMN_WIDTH, grid.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = rendered.createGraphics();
      try
      {
        grid.paint(graphics);
      }
      finally
      {
        graphics.dispose();
      }
      int alpha = rendered.getRGB(90, 24) >>> 24;
      assertTrue(alpha > 0 && alpha < 255);
      assertEquals(0, rendered.getRGB(300, 24) >>> 24);

      grid.setPreview(null, -1);
      assertEquals(24, grid.getPreferredSize().height);
    });
  }

  /**
   * Creates a two-column edit grid.
   *
   * @param cards the cards in saved order
   * @return the sized grid
   */
  private ServiceCardGrid createGrid(JPanel... cards)
  {
    ServiceCardGrid grid = new ServiceCardGrid(List.of(cards), true, TWO_COLUMN_WIDTH);
    grid.setSize(TWO_COLUMN_WIDTH, grid.getPreferredSize().height);
    grid.doLayout();
    return grid;
  }

  /**
   * Creates an opaque snapshot for the grid to fade.
   *
   * @return the snapshot in the dimensions of a card
   */
  private BufferedImage createPreview()
  {
    BufferedImage preview = new BufferedImage(180, 48, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = preview.createGraphics();
    try
    {
      graphics.setColor(Color.BLUE);
      graphics.fillRect(0, 0, preview.getWidth(), preview.getHeight());
    }
    finally
    {
      graphics.dispose();
    }
    return preview;
  }
}
