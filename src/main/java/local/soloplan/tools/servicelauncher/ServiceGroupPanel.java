//-----------------------------------------------------------------------
// <copyright file="ServiceGroupPanel.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.ui.components.JBLabel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Represents a service group panel.
 */
final class ServiceGroupPanel extends JPanel
{
  private static final int MINIMUM_CARD_WIDTH = 180;
  private static final int CARD_GAP = 6;
  private static final int HEADING_SIZE_INCREASE = 2;
  private static final int SELECTION_ICON_SIZE = 12;
  private static final int SELECTION_SLOT_SIZE = 18;

  private final JPanel cardGrid = new JPanel();
  private final JButton selectionToggleButton;
  private final BooleanSupplier allSelected;
  private final boolean showSelectionToggle;
  private int columns;

  /**
   * Creates a new {@code ServiceGroupPanel} instance.
   *
   * @param title the title
   * @param cards the cards
   * @param popupInstaller the popup installer
   * @param allSelected supplies whether all cards in the category are selected
   * @param selectionToggle toggles the selection of every card in the category
   * @param showSelectionToggle whether the category toggle should be available
   */
  ServiceGroupPanel(
    String title,
    List<? extends Component> cards,
    Consumer<JComponent> popupInstaller,
    BooleanSupplier allSelected,
    Runnable selectionToggle,
    boolean showSelectionToggle
  )
  {
    super(new BorderLayout(0, CARD_GAP));
    this.allSelected = allSelected;
    this.showSelectionToggle = showSelectionToggle;
    selectionToggleButton = createSelectionToggleButton(selectionToggle);
    setOpaque(false);
    popupInstaller.accept(this);
    add(createHeading(title, popupInstaller), BorderLayout.NORTH);
    configureCardGrid(cards, popupInstaller);
    add(cardGrid, BorderLayout.CENTER);
    selectionToggleButton.setEnabled(showSelectionToggle && !cards.isEmpty());
    refreshSelectionState();
    if (showSelectionToggle)
    {
      installSelectionToggleVisibility();
    }
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
   * @return the category heading
   */
  private JComponent createHeading(String title, Consumer<JComponent> popupInstaller)
  {
    JPanel heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    JBLabel titleLabel = new JBLabel(title, SwingConstants.CENTER);
    float fontSize = titleLabel.getFont().getSize2D() + HEADING_SIZE_INCREASE;
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, fontSize));
    titleLabel.setToolTipText("Right-click to rename or reorder this group");
    popupInstaller.accept(titleLabel);
    Dimension slotSize = new Dimension(SELECTION_SLOT_SIZE, SELECTION_SLOT_SIZE);
    JPanel selectionSlot = new JPanel(new BorderLayout());
    selectionSlot.setOpaque(false);
    selectionSlot.setPreferredSize(slotSize);
    selectionSlot.add(selectionToggleButton, BorderLayout.CENTER);
    Component selectionSpacer = Box.createRigidArea(slotSize);
    selectionSpacer.setVisible(showSelectionToggle);
    selectionSlot.setVisible(showSelectionToggle);
    heading.add(selectionSpacer, BorderLayout.WEST);
    heading.add(titleLabel, BorderLayout.CENTER);
    heading.add(selectionSlot, BorderLayout.EAST);
    return heading;
  }

  /**
   * Creates the category selection toggle.
   *
   * @param selectionToggle toggles the category selection
   * @return the category selection button
   */
  private JButton createSelectionToggleButton(Runnable selectionToggle)
  {
    JButton button = new HoverIconButton(
      LauncherIcons.scaleIcon(AllIcons.Actions.Selectall, SELECTION_ICON_SIZE),
      "Select all services in this category",
      HoverIconButton.Size.COMPACT
    );
    button.addActionListener(actionEvent ->
    {
      selectionToggle.run();
      refreshSelectionState();
    });
    button.setVisible(false);
    return button;
  }

  /**
   * Refreshes the icon and tooltip to describe the next category selection action.
   */
  void refreshSelectionState()
  {
    boolean deselect = selectionToggleButton.isEnabled() && allSelected.getAsBoolean();
    selectionToggleButton.setIcon(LauncherIcons.scaleIcon(
      deselect ? AllIcons.Actions.Unselectall : AllIcons.Actions.Selectall,
      SELECTION_ICON_SIZE
    ));
    selectionToggleButton.setDisabledIcon(IconLoader.getDisabledIcon(selectionToggleButton.getIcon()));
    selectionToggleButton.setToolTipText(
      deselect ? "Deselect all services in this category" : "Select all services in this category"
    );
  }

  /**
   * Shows the category selection toggle only while the pointer is inside this category.
   */
  private void installSelectionToggleVisibility()
  {
    MouseAdapter hoverListener = new MouseAdapter()
    {
      /** {@inheritDoc} */
      @Override
      public void mouseEntered(MouseEvent event)
      {
        selectionToggleButton.setVisible(true);
      }

      /** {@inheritDoc} */
      @Override
      public void mouseExited(MouseEvent event)
      {
        javax.swing.SwingUtilities.invokeLater(() ->
          selectionToggleButton.setVisible(isShowing() && getMousePosition(true) != null));
      }
    };
    installHoverListener(this, hoverListener);
  }

  /**
   * Installs the category hover listener on a component and all its descendants.
   *
   * @param component the component whose pointer transitions should be observed
   * @param hoverListener the shared category hover listener
   */
  private void installHoverListener(Component component, MouseAdapter hoverListener)
  {
    component.addMouseListener(hoverListener);
    if (component instanceof Container container)
    {
      for (Component child : container.getComponents())
      {
        installHoverListener(child, hoverListener);
      }
    }
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
