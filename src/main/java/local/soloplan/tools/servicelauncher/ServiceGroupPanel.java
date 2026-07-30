//-----------------------------------------------------------------------
// <copyright file="ServiceGroupPanel.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.ui.components.JBLabel;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a service group panel.
 */
final class ServiceGroupPanel extends JPanel
{
  private static final int MINIMUM_CARD_WIDTH = 180;
  private static final int CARD_GAP = 6;
  private static final int HEADING_SIZE_INCREASE = 2;

  private final JPanel cardGrid = new JPanel();
  private int columns;

  /**
   * Creates a new {@code ServiceGroupPanel} instance.
   *
   * @param title the title
   * @param cards the cards
   * @param popupInstaller the popup installer
   */
  ServiceGroupPanel(String title, List<? extends Component> cards, Consumer<JComponent> popupInstaller)
  {
    super(new BorderLayout(0, CARD_GAP));
    setOpaque(false);
    popupInstaller.accept(this);
    add(createHeading(title, popupInstaller), BorderLayout.NORTH);
    configureCardGrid(cards, popupInstaller);
    add(cardGrid, BorderLayout.CENTER);
    addComponentListener(new ComponentAdapter()
    {
      /** {@inheritDoc} */
      @Override
      public void componentResized(ComponentEvent event)
      {
        updateColumns();
      }
    });
    javax.swing.SwingUtilities.invokeLater(this::updateColumns);
  }

  /**
   * Creates the heading.
   *
   * @param title the title
   * @param popupInstaller the popup installer
   * @return the create heading result
   */
  private JBLabel createHeading(String title, Consumer<JComponent> popupInstaller)
  {
    JBLabel heading = new JBLabel(title, SwingConstants.CENTER);
    float fontSize = heading.getFont().getSize2D() + HEADING_SIZE_INCREASE;
    heading.setFont(heading.getFont().deriveFont(Font.PLAIN, fontSize));
    heading.setToolTipText("Right-click to rename or reorder this group");
    popupInstaller.accept(heading);
    return heading;
  }

  /**
   * Configures the card grid.
   *
   * @param cards the cards
   * @param popupInstaller the popup installer
   */
  private void configureCardGrid(List<? extends Component> cards, Consumer<JComponent> popupInstaller)
  {
    cardGrid.setOpaque(false);
    popupInstaller.accept(cardGrid);
    cards.forEach(cardGrid::add);
  }

  /**
   * Updates the columns.
   */
  private void updateColumns()
  {
    int usableWidth = Math.max(MINIMUM_CARD_WIDTH, getWidth());
    int desiredColumns = Math.max(1, (usableWidth + CARD_GAP) / (MINIMUM_CARD_WIDTH + CARD_GAP));
    if (desiredColumns == columns)
    {
      return;
    }
    columns = desiredColumns;
    cardGrid.setLayout(new GridLayout(0, columns, CARD_GAP, CARD_GAP));
    revalidate();
  }

  /** {@inheritDoc} */
  @Override
  public Dimension getMaximumSize()
  {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}
