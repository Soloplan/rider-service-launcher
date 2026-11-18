//-----------------------------------------------------------------------
// <copyright file="ServiceCardGrid.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.util.ui.UIUtil;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Lays out the cards and reserves a slot for the translucent preview during a drag.
 */
final class ServiceCardGrid extends JPanel
{
  static final int MINIMUM_CARD_WIDTH = 180;
  static final int CARD_HEIGHT = 48;
  private static final int CARD_GAP = 6;
  private static final int EMPTY_EDIT_HEIGHT = 24;
  private static final int SLOT_HYSTERESIS = 4;
  private static final float PREVIEW_OPACITY = 0.45f;

  private final List<? extends Component> cards;
  private final boolean editing;
  private int columns = 1;
  private Component draggedCard;
  private BufferedImage preview;
  private int previewSlot = -1;

  /**
   * Creates a grid whose card components stay attached throughout a drag.
   *
   * @param cards the cards in their saved order
   * @param editing whether to give empty groups a drop area
   * @param initialWidth the width available before the first layout
   */
  ServiceCardGrid(List<? extends Component> cards, boolean editing, int initialWidth)
  {
    super(null);
    this.cards = List.copyOf(cards);
    this.editing = editing;
    updateColumns(initialWidth);
    setOpaque(false);
    cards.forEach(this::add);
  }

  /**
   * Adjusts the number of columns to the available width.
   *
   * @param width the group width
   */
  void updateColumns(int width)
  {
    int desired = Math.max(1, (width + CARD_GAP) / (MINIMUM_CARD_WIDTH + CARD_GAP));
    if (columns != desired)
    {
      columns = desired;
      revalidate();
    }
  }

  /**
   * Omits the grabbed card from the visual layout while keeping it attached for mouse events.
   *
   * @param card the grabbed card, or null when the drag ends
   */
  void setDraggedCard(Component card)
  {
    draggedCard = card;
    revalidate();
    repaint();
  }

  /**
   * Shows the faded card at the specified insertion slot.
   *
   * @param image the snapshot of the dragged card, or null to clear the preview
   * @param slot the insertion slot, or -1 when clearing
   */
  void setPreview(BufferedImage image, int slot)
  {
    if (preview != image || previewSlot != slot)
    {
      preview = image;
      previewSlot = slot;
      revalidate();
      repaint();
    }
  }

  /**
   * Resolves the pointer against the same prospective grid before and after showing the preview.
   *
   * @param pointer the pointer in grid coordinates
   * @return the slot for the dragged card
   */
  int insertionAt(Point pointer)
  {
    if (previewSlot >= 0)
    {
      Rectangle current = slotBounds(previewSlot);
      current.grow(CARD_GAP / 2 + SLOT_HYSTERESIS, CARD_GAP / 2 + SLOT_HYSTERESIS);
      if (current.contains(pointer))
      {
        return previewSlot;
      }
    }

    int lastSlot = remainingCardCount();
    Rectangle first = slotBounds(0);
    int x = getComponentOrientation().isLeftToRight()
      ? pointer.x - first.x
      : first.x + first.width - 1 - pointer.x;
    int column = Math.clamp((x + CARD_GAP / 2) / (first.width + CARD_GAP), 0, columns - 1);
    int row = Math.clamp((pointer.y + CARD_GAP / 2) / (CARD_HEIGHT + CARD_GAP), 0, lastSlot / columns);
    return Math.min(row * columns + column, lastSlot);
  }

  /**
   * Finds the saved card that would follow a drop into the given slot.
   *
   * @param slot the insertion slot
   * @return the next card, or null when appending
   */
  Component cardAfter(int slot)
  {
    for (Component card : cards)
    {
      if (card != draggedCard && slot-- == 0)
      {
        return card;
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Dimension getPreferredSize()
  {
    int displayedCards = remainingCardCount() + (preview == null ? 0 : 1);
    int capacity = Math.max(cards.size(), displayedCards);
    int rows = (capacity + columns - 1) / columns;
    int height = rows == 0 ? (editing ? EMPTY_EDIT_HEIGHT : 0) : rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
    return new Dimension(columns * (MINIMUM_CARD_WIDTH + CARD_GAP) - CARD_GAP, height);
  }

  /** {@inheritDoc} */
  @Override
  public void doLayout()
  {
    int slot = 0;
    for (Component card : cards)
    {
      if (card == draggedCard)
      {
        card.setBounds(0, 0, 0, 0);
        continue;
      }
      if (slot == previewSlot)
      {
        slot++;
      }
      card.setBounds(slotBounds(slot++));
    }
  }

  /** {@inheritDoc} */
  @Override
  protected void paintChildren(Graphics graphics)
  {
    super.paintChildren(graphics);
    if (preview == null)
    {
      return;
    }

    Rectangle cell = slotBounds(previewSlot);
    Graphics2D overlay = (Graphics2D) graphics.create();
    try
    {
      overlay.setComposite(AlphaComposite.SrcOver.derive(PREVIEW_OPACITY));
      UIUtil.drawImage(overlay, preview, cell, null, null);
    }
    finally
    {
      overlay.dispose();
    }
  }

  /**
   * Counts cards available for layout around the preview.
   *
   * @return the number of cards other than the dragged card
   */
  private int remainingCardCount()
  {
    return cards.size() - (draggedCard == null ? 0 : 1);
  }

  /**
   * Calculates the position of a cell independently of which card occupies it.
   *
   * @param slot the row-major cell index
   * @return the cell bounds in grid coordinates
   */
  private Rectangle slotBounds(int slot)
  {
    int gaps = (columns - 1) * CARD_GAP;
    int width = Math.max(0, (getWidth() - gaps) / columns);
    int inset = Math.max(0, (getWidth() - gaps - width * columns) / 2);
    int column = slot % columns;
    if (!getComponentOrientation().isLeftToRight())
    {
      column = columns - 1 - column;
    }
    return new Rectangle(inset + column * (width + CARD_GAP), slot / columns * (CARD_HEIGHT + CARD_GAP), width, CARD_HEIGHT);
  }
}
