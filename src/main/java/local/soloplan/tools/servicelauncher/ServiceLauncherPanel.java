//-----------------------------------------------------------------------
// <copyright file="ServiceLauncherPanel.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a service launcher panel.
 */
final class ServiceLauncherPanel extends JPanel implements Disposable
{
  private static final int MIN_CARD_WIDTH = 180;
  private static final int RUNNING_BORDER_WIDTH = 2;
  private static final int PRETTY_STARTING_ANIMATION_FRAME_DELAY_MS = 16;
  private static final int SIMPLE_STARTING_ANIMATION_FRAME_DELAY_MS = 100;
  private static final float STARTING_ANIMATION_SPEED_PX_PER_SECOND = 87.5f;
  private static final Color CARD_BACKGROUND = new JBColor(new Color(0xF2F2F2), new Color(0x343434));
  private static final Color UNSELECTED_CARD_BACKGROUND =
    new JBColor(new Color(0xDADADA), new Color(0x292929));
  private static final Color UNSELECTED_TEXT =
    new JBColor(new Color(0x747474), new Color(0x929292));
  private static final Color UNSELECTED_BORDER =
    new JBColor(new Color(0xB8B8B8), new Color(0x515151));
  private static final Color SELECTED_BORDER =
    new JBColor(new Color(0x8A8A8A), new Color(0x737373));
  private final Project project;
  private final ServiceLauncherPreferences preferences;
  private final VerticalScrollablePanel groupsPanel = new VerticalScrollablePanel();
  private final Set<String> selected = new HashSet<>();
  private final Map<String, List<RunningSession>> runningSessions = new HashMap<>();
  private final Map<String, ExecutionMode> pendingRestarts = new HashMap<>();
  private final Set<String> pendingStarts = new HashSet<>();
  private final Map<String, ServiceCard> cards = new HashMap<>();
  private final Map<String, ServiceCard> cardsByConfiguration = new HashMap<>();
  private final List<ServiceGroupPanel> groupPanels = new ArrayList<>();
  private final AtomicBoolean rebuildScheduled = new AtomicBoolean();
  private final Timer startingAnimationTimer =
    new Timer(PRETTY_STARTING_ANIMATION_FRAME_DELAY_MS, event -> advanceStartingAnimation());
  private float startingAnimationOffset;
  private long lastStartingAnimationFrameNanos;
  private Color primaryColor;
  private StartingBorder startingBorder;
  private Icon simpleStartingIcon;
  private final MessageBusConnection connection;
  private Map<String, RunnerAndConfigurationSettings> availableByName = Map.of();
  private Map<String, ServiceLauncherSettings.LauncherConfiguration> configurationsById = Map.of();
  private List<ServiceLauncherSettings.LauncherConfiguration> currentConfigurations = List.of();
  private JButton runButton;
  private JButton debugButton;
  private JButton restartAllButton;
  private JButton stopAllButton;
  private JButton selectionToggleButton;
  private final Icon startingIcon = new AnimatedIcon.Default();

  /**
   * Creates a new {@code ServiceLauncherPanel} instance.
   *
   * @param project the project
   */
  ServiceLauncherPanel(Project project)
  {
    super(new BorderLayout());
    this.project = project;
    preferences = ServiceLauncherPreferences.getInstance();
    refreshPreferenceValues();
    configureUserInterface();
    connection = project.getMessageBus().connect(this);
    MessageBusConnection preferencesConnection =
      ApplicationManager.getApplication().getMessageBus().connect(this);
    preferencesConnection.subscribe(ServiceLauncherPreferences.TOPIC, this::preferencesChanged);
    subscribeToExecutionEvents();
    subscribeToRunConfigurationEvents();
    rebuild();
  }

  /**
   * Configures the user interface.
   */
  private void configureUserInterface()
  {
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    groupsPanel.setOpaque(false);
    groupsPanel.setLayout(new BoxLayout(groupsPanel, BoxLayout.Y_AXIS));
    add(createToolbar(), BorderLayout.NORTH);
    JScrollPane scrollPane = new JBScrollPane(groupsPanel);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    add(scrollPane, BorderLayout.CENTER);
    installSelectionPopup(groupsPanel);
    installSelectionPopup(scrollPane.getViewport());
  }

  /**
   * Performs the subscribe to execution events operation.
   */
  private void subscribeToExecutionEvents()
  {
    ExecutionListener listener = new ExecutionListener()
    {
      /** {@inheritDoc} */
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment,
                   @NotNull ProcessHandler handler)
                   {
        sessionStarted(executorId, environment, handler);
                   }

      /** {@inheritDoc} */
      @Override
      public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment)
      {
        sessionNotStarted(environment);
      }

      /** {@inheritDoc} */
      @Override
      public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment environment,
                     @NotNull ProcessHandler handler, int exitCode)
                     {
        sessionTerminated(environment, handler);
                     }
    };
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, listener);
  }

  /**
   * Performs the subscribe to run configuration events operation.
   */
  private void subscribeToRunConfigurationEvents()
  {
    RunManagerListener listener = new RunManagerListener()
    {
      /** {@inheritDoc} */
      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings)
      {
        rebuildLater();
      }

      /** {@inheritDoc} */
      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings)
      {
        rebuildLater();
      }

      /** {@inheritDoc} */
      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings, String existingId)
      {
        rebuildLater();
      }
    };
    connection.subscribe(RunManagerListener.TOPIC, listener);
  }

  /**
   * Creates the toolbar.
   *
   * @return the create toolbar result
   */
  private JPanel createToolbar()
  {
    JPanel toolbar = new JPanel(new BorderLayout());
    toolbar.setOpaque(false);
    toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    toolbar.add(createConfigurationActions(), BorderLayout.WEST);
    toolbar.add(createLaunchActions(), BorderLayout.EAST);
    refreshAggregateActionState();
    return toolbar;
  }

  /**
   * Creates the launcher configuration actions.
   *
   * @return the launcher configuration actions
   */
  private JPanel createConfigurationActions()
  {
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    actions.setOpaque(false);
    JButton editButton = iconButton(AllIcons.Actions.Edit, "Customize services");
    editButton.addActionListener(actionEvent -> editLauncherConfigurations());
    selectionToggleButton = iconButton(AllIcons.Actions.Selectall, "Select all visible services");
    selectionToggleButton.addActionListener(actionEvent -> toggleSelection(cards.keySet()));
    selectionToggleButton.setVisible(preferences.showToggleAllButton());
    actions.add(editButton);
    actions.add(selectionToggleButton);
    return actions;
  }

  /**
   * Creates the launch actions.
   *
   * @return the create launch actions result
   */
  private JPanel createLaunchActions()
  {
    JPanel launchActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    launchActions.setOpaque(false);
    runButton = iconButton(AllIcons.Actions.Execute, "Run selected services that are not already running");
    runButton.addActionListener(actionEvent -> startSelectedServices(ExecutionMode.RUN));
    debugButton = iconButton(AllIcons.Actions.StartDebugger, "Debug selected services that are not already running");
    debugButton.addActionListener(actionEvent -> startSelectedServices(ExecutionMode.DEBUG));
    restartAllButton = iconButton(AllIcons.Actions.Restart, "Start missing and restart all running selected services");
    restartAllButton.addActionListener(actionEvent -> restartSelected());
    stopAllButton = iconButton(AllIcons.Actions.StopRefresh, "Stop all services running from this launcher");
    stopAllButton.addActionListener(actionEvent -> stopAllServices());
    launchActions.add(runButton);
    launchActions.add(debugButton);
    launchActions.add(restartAllButton);
    launchActions.add(stopAllButton);
    return launchActions;
  }

  /**
   * Returns the result of icon button.
   *
   * @param icon the icon
   * @param tooltip the tooltip
   * @return the icon button result
   */
  private JButton iconButton(Icon icon, String tooltip)
  {
    return new HoverIconButton(icon, tooltip, HoverIconButton.Size.TOOLBAR);
  }

  /**
   * Rebuilds the operation.
   */
  private void rebuild()
  {
    cards.clear();
    cardsByConfiguration.clear();
    groupPanels.clear();
    groupsPanel.removeAll();
    Map<String, RunnerAndConfigurationSettings> available = availableConfigurations();
    ServiceLauncherSettings launcherSettings = ServiceLauncherSettings.getInstance(project);
    List<ServiceLauncherSettings.LauncherConfiguration> launcherConfigurations =
      launcherSettings.reconciledWith(new ArrayList<>(available.keySet()));
    updateConfigurationSnapshot(available, launcherConfigurations);
    Map<String, List<ServiceLauncherSettings.LauncherConfiguration>> groups =
      groupVisibleConfigurations(launcherSettings, launcherConfigurations);
    renderGroups(groups, available);
    finishRebuild(available);
  }

  /**
   * Updates the configuration snapshot.
   *
   * @param available the available
   * @param launcherConfigurations the launcher configurations
   */
  private void updateConfigurationSnapshot(
    Map<String, RunnerAndConfigurationSettings> available,
    List<ServiceLauncherSettings.LauncherConfiguration> launcherConfigurations
  )
  {
    availableByName = available;
    currentConfigurations = launcherConfigurations;
    Map<String, ServiceLauncherSettings.LauncherConfiguration> byId = new HashMap<>();
    launcherConfigurations.forEach(configuration -> byId.put(configuration.itemId, configuration));
    configurationsById = byId;
  }

  /**
   * Returns the result of group visible configurations.
   *
   * @param launcherSettings the launcher settings
   * @param launcherConfigurations the launcher configurations
   * @return the group visible configurations result
   */
  private Map<String, List<ServiceLauncherSettings.LauncherConfiguration>> groupVisibleConfigurations(
    ServiceLauncherSettings launcherSettings,
    List<ServiceLauncherSettings.LauncherConfiguration> launcherConfigurations
  )
  {
    Map<String, List<ServiceLauncherSettings.LauncherConfiguration>> groups = new LinkedHashMap<>();
    launcherSettings.groupNames().forEach(group -> groups.put(group, new ArrayList<>()));
    launcherConfigurations.stream()
      .filter(configuration -> configuration.visible)
      .forEach(configuration -> addToGroup(groups, configuration));
    return groups;
  }

  /**
   * Adds the to group.
   *
   * @param groups the groups
   * @param configuration the configuration
   */
  private void addToGroup(
    Map<String, List<ServiceLauncherSettings.LauncherConfiguration>> groups,
    ServiceLauncherSettings.LauncherConfiguration configuration
  )
  {
    String group = ServiceLauncherSettings.normalizeGroup(configuration.group);
    groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(configuration);
  }

  /**
   * Performs the render groups operation.
   *
   * @param groups the groups
   * @param available the available
   */
  private void renderGroups(
    Map<String, List<ServiceLauncherSettings.LauncherConfiguration>> groups,
    Map<String, RunnerAndConfigurationSettings> available
  )
  {
    if (groups.isEmpty())
    {
      renderEmptyState();
    }
    else
    {
      groups.forEach((name, configurations) -> renderGroup(name, configurations, available));
      groupsPanel.add(Box.createVerticalGlue());
    }
  }

  /**
   * Performs the render empty state operation.
   */
  private void renderEmptyState()
  {
    String message = "<html><div style='text-align:center'>No services or groups yet.<br>"
      + "Right-click here to create a launcher configuration or group.</div></html>";
    JBLabel emptyState = new JBLabel(message, SwingConstants.CENTER);
    emptyState.setAlignmentX(Component.CENTER_ALIGNMENT);
    groupsPanel.add(Box.createVerticalGlue());
    groupsPanel.add(emptyState);
    groupsPanel.add(Box.createVerticalGlue());
  }

  /**
   * Performs the render group operation.
   *
   * @param name the name
   * @param configurations the configurations
   * @param available the available
   */
  private void renderGroup(
    String name,
    List<ServiceLauncherSettings.LauncherConfiguration> configurations,
    Map<String, RunnerAndConfigurationSettings> available
  )
  {
    ServiceGroupPanel group = createGroupPanel(name, configurations, available);
    groupPanels.add(group);
    group.setAlignmentX(Component.LEFT_ALIGNMENT);
    groupsPanel.add(group);
    groupsPanel.add(Box.createVerticalStrut(14));
  }

  /**
   * Performs the finish rebuild operation.
   *
   * @param available the available
   */
  private void finishRebuild(Map<String, RunnerAndConfigurationSettings> available)
  {
    selected.retainAll(cards.keySet());
    pendingStarts.retainAll(available.keySet());
    synchronizeStartingAnimation();
    refreshSelectionControls();
    refreshAggregateActionState();
    groupsPanel.revalidate();
    groupsPanel.repaint();
  }

  /**
   * Returns the result of launcher configurations.
   *
   * @return the launcher configurations result
   */
  private List<ServiceLauncherSettings.LauncherConfiguration> launcherConfigurations()
  {
    return currentConfigurations;
  }

  /**
   * Returns the result of available configurations.
   *
   * @return the available configurations result
   */
  private Map<String, RunnerAndConfigurationSettings> availableConfigurations()
  {
    Map<String, RunnerAndConfigurationSettings> result = new LinkedHashMap<>();
    RunManager.getInstance(project).getAllSettings().stream()
      .filter(settings -> !settings.isTemporary())
      .sorted(Comparator.comparing(RunnerAndConfigurationSettings::getName, String.CASE_INSENSITIVE_ORDER))
      .forEach(settings -> result.put(settings.getName(), settings));
    return result;
  }

  /**
   * Creates the group panel.
   *
   * @param title the title
   * @param launcherConfigurations the launcher configurations
   * @param available the available
   * @return the create group panel result
   */
  private ServiceGroupPanel createGroupPanel(
    String title,
    List<ServiceLauncherSettings.LauncherConfiguration> launcherConfigurations,
    Map<String, RunnerAndConfigurationSettings> available
  )
  {
    List<ServiceCard> groupCards = launcherConfigurations.stream()
      .map(launcherConfiguration -> createCard(launcherConfiguration, available.get(launcherConfiguration.configurationName)))
      .toList();
    Set<String> itemIds = launcherConfigurations.stream()
      .map(configuration -> configuration.itemId)
      .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    return new ServiceGroupPanel(
      title,
      groupCards,
      component -> installGroupPopup(component, title),
      () -> allSelected(itemIds),
      () -> toggleSelection(itemIds),
      preferences.showToggleAllButton()
    );
  }

  /**
   * Creates the card.
   *
   * @param launcherConfiguration the launcher configuration
   * @param settings the settings
   * @return the create card result
   */
  private ServiceCard createCard(
    ServiceLauncherSettings.LauncherConfiguration launcherConfiguration,
    RunnerAndConfigurationSettings settings
  )
  {
    ServiceCard card = new ServiceCard(launcherConfiguration, settings);
    cards.put(launcherConfiguration.itemId, card);
    if (launcherConfiguration.configurationName != null && !launcherConfiguration.configurationName.isBlank())
    {
      cardsByConfiguration.put(launcherConfiguration.configurationName, card);
    }
    return card;
  }

  /**
   * Selects the all.
   */
  private void selectAll()
  {
    selected.addAll(cards.keySet());
    cards.values().forEach(ServiceCard::refreshBorder);
    refreshSelectionControls();
    refreshLaunchActionState();
  }

  /**
   * Selects the none.
   */
  private void selectNone()
  {
    selected.clear();
    cards.values().forEach(ServiceCard::refreshBorder);
    refreshSelectionControls();
    refreshLaunchActionState();
  }

  /**
   * Toggles the selection of the requested launcher configurations.
   *
   * @param itemIds the stable launcher configuration identifiers to toggle
   */
  private void toggleSelection(Collection<String> itemIds)
  {
    if (itemIds.isEmpty())
    {
      return;
    }
    if (allSelected(itemIds))
    {
      selected.removeAll(itemIds);
    }
    else
    {
      selected.addAll(itemIds);
    }
    itemIds.stream()
      .map(cards::get)
      .filter(java.util.Objects::nonNull)
      .forEach(ServiceCard::refreshBorder);
    refreshSelectionControls();
    refreshLaunchActionState();
  }

  /**
   * Determines whether all requested launcher configurations are selected.
   *
   * @param itemIds the stable launcher configuration identifiers to inspect
   * @return whether the collection is nonempty and every identifier is selected
   */
  private boolean allSelected(Collection<String> itemIds)
  {
    return !itemIds.isEmpty() && selected.containsAll(itemIds);
  }

  /**
   * Refreshes the global and category selection toggles.
   */
  private void refreshSelectionControls()
  {
    if (selectionToggleButton != null)
    {
      boolean hasCards = !cards.isEmpty();
      boolean deselect = hasCards && allSelected(cards.keySet());
      selectionToggleButton.setEnabled(hasCards);
      selectionToggleButton.setIcon(deselect ? AllIcons.Actions.Unselectall : AllIcons.Actions.Selectall);
      selectionToggleButton.setDisabledIcon(IconLoader.getDisabledIcon(selectionToggleButton.getIcon()));
      selectionToggleButton.setToolTipText(
        deselect ? "Deselect all visible services" : "Select all visible services"
      );
    }
    groupPanels.forEach(ServiceGroupPanel::refreshSelectionState);
  }

  /**
   * Performs the install selection popup operation.
   *
   * @param component the component
   */
  private void installSelectionPopup(JComponent component)
  {
    component.addMouseListener(new MouseAdapter()
    {
      /** {@inheritDoc} */
      @Override
      public void mousePressed(MouseEvent event)
      {
        showSelectionPopup(event);
      }

      /** {@inheritDoc} */
      @Override
      public void mouseReleased(MouseEvent event)
      {
        showSelectionPopup(event);
      }
    });
  }

  /**
   * Shows the selection popup.
   *
   * @param event the event
   */
  private void showSelectionPopup(MouseEvent event)
  {
    if (!event.isPopupTrigger())
    {
      return;
    }
    JPopupMenu menu = new JPopupMenu();
    JMenuItem addService = new JMenuItem("Add service…");
    addService.addActionListener(actionEvent -> createService(null));
    JMenuItem addGroup = new JMenuItem("Add group…");
    addGroup.addActionListener(actionEvent -> createGroup());
    menu.add(addService);
    menu.add(addGroup);
    menu.addSeparator();
    JMenuItem selectAllItem = new JMenuItem("Select all visible services");
    selectAllItem.addActionListener(actionEvent -> selectAll());
    JMenuItem selectNoneItem = new JMenuItem("Clear selection");
    selectNoneItem.addActionListener(actionEvent -> selectNone());
    menu.add(selectAllItem);
    menu.add(selectNoneItem);
    menu.show((Component) event.getSource(), event.getX(), event.getY());
  }

  /**
   * Starts the selected services.
   *
   * @param mode the mode
   */
  private void startSelectedServices(ExecutionMode mode)
  {
    if (selected.isEmpty())
    {
      Messages.showInfoMessage(project, "Select one or more service cards first.", "Service Launcher");
      return;
    }
    Map<String, RunnerAndConfigurationSettings> available = availableByName;
    List<RunnerAndConfigurationSettings> toStart = selected.stream()
      .map(configurationsById::get)
      .filter(item -> item != null && item.configurationName != null && !item.configurationName.isBlank())
      .filter(item -> !isRunning(item.configurationName) && !pendingStarts.contains(item.configurationName))
      .map(item -> available.get(item.configurationName))
      .filter(settings -> settings != null)
      .toList();
    markStarting(toStart.stream().map(RunnerAndConfigurationSettings::getName).toList());
    toStart.forEach(settings -> launch(settings, mode));
  }

  /**
   * Restarts the selected.
   */
  private void restartSelected()
  {
    if (selected.isEmpty())
    {
      Messages.showInfoMessage(project, "Select one or more service cards first.", "Service Launcher");
      return;
    }
    for (String itemId : new ArrayList<>(selected))
    {
      ServiceLauncherSettings.LauncherConfiguration item = configurationsById.get(itemId);
      if (item != null && item.configurationName != null && !item.configurationName.isBlank())
      {
        restartService(item.configurationName);
      }
    }
  }

  /**
   * Restarts the service.
   *
   * @param configurationName the configuration name
   */
  private void restartService(String configurationName)
  {
    RunnerAndConfigurationSettings settings = availableByName.get(configurationName);
    if (settings == null)
    {
      return;
    }
    List<RunningSession> sessions = runningSessions.get(configurationName);
    if (sessions == null || sessions.isEmpty())
    {
      launch(settings, ExecutionMode.RUN);
      return;
    }
    ExecutionMode mode = sessions.stream().anyMatch(session -> session.mode() == ExecutionMode.DEBUG)
      ? ExecutionMode.DEBUG
      : ExecutionMode.RUN;
    pendingRestarts.put(configurationName, mode);
    sessions.stream().map(RunningSession::handler).distinct().forEach(this::stopHandler);
  }

  /**
   * Starts the service.
   *
   * @param configurationName the configuration name
   * @param mode the mode
   */
  private void startService(String configurationName, ExecutionMode mode)
  {
    RunnerAndConfigurationSettings settings = availableByName.get(configurationName);
    if (settings == null || isRunning(configurationName) || pendingStarts.contains(configurationName))
    {
      return;
    }
    launch(settings, mode);
  }

  /**
   * Stops the service.
   *
   * @param configurationName the configuration name
   */
  private void stopService(String configurationName)
  {
    pendingRestarts.remove(configurationName);
    List<RunningSession> sessions = runningSessions.get(configurationName);
    if (sessions != null)
    {
      sessions.stream().map(RunningSession::handler).distinct().forEach(this::stopHandler);
    }
  }

  /**
   * Stops the all services.
   */
  private void stopAllServices()
  {
    pendingRestarts.clear();
    runningSessions.values().stream().flatMap(List::stream).map(RunningSession::handler).distinct()
      .forEach(this::stopHandler);
  }

  /**
   * Stops the handler.
   *
   * @param handler the handler
   */
  private void stopHandler(ProcessHandler handler)
  {
    if (!handler.isProcessTerminated() && !handler.isProcessTerminating())
    {
      handler.destroyProcess();
    }
  }

  /**
   * Determines whether running.
   *
   * @param configurationName the configuration name
   * @return whether running
   */
  private boolean isRunning(String configurationName)
  {
    if (configurationName == null || configurationName.isBlank())
    {
      return false;
    }
    List<RunningSession> sessions = runningSessions.get(configurationName);
    return sessions != null && !sessions.isEmpty();
  }

  /**
   * Performs the launch operation.
   *
   * @param settings the settings
   * @param mode the mode
   */
  private void launch(RunnerAndConfigurationSettings settings, ExecutionMode mode)
  {
    markStarting(List.of(settings.getName()));
    ProgramRunnerUtil.executeConfiguration(settings, mode.executor());
  }

  /**
   * Performs the mark starting operation.
   *
   * @param configurationNames the configuration names
   */
  private void markStarting(List<String> configurationNames)
  {
    boolean changed = false;
    for (String configurationName : configurationNames)
    {
      if (pendingStarts.add(configurationName))
      {
        changed = true;
        refreshCardsForConfiguration(configurationName);
      }
    }
    if (changed)
    {
      synchronizeStartingAnimation();
      refreshLaunchActionState();
    }
  }

  /**
   * Performs the clear starting operation.
   *
   * @param configurationName the configuration name
   */
  private void clearStarting(String configurationName)
  {
    if (pendingStarts.remove(configurationName))
    {
      refreshCardsForConfiguration(configurationName);
      synchronizeStartingAnimation();
      refreshLaunchActionState();
    }
  }

  /**
   * Performs the synchronize starting animation operation.
   */
  private void synchronizeStartingAnimation()
  {
    if (pendingStarts.isEmpty())
    {
      startingAnimationTimer.stop();
      startingAnimationOffset = 0;
      lastStartingAnimationFrameNanos = 0;
    }
    else
    {
      int frameDelay = preferences.startupAnimation() == ServiceLauncherPreferences.StartupAnimation.PRETTY
        ? PRETTY_STARTING_ANIMATION_FRAME_DELAY_MS
        : SIMPLE_STARTING_ANIMATION_FRAME_DELAY_MS;
      startingAnimationTimer.setDelay(frameDelay);
      startingAnimationTimer.setInitialDelay(frameDelay);
      if (!startingAnimationTimer.isRunning())
      {
        lastStartingAnimationFrameNanos = System.nanoTime();
        startingAnimationTimer.start();
      }
    }
  }

  /**
   * Performs the advance starting animation operation.
   */
  private void advanceStartingAnimation()
  {
    long now = System.nanoTime();
    float elapsedSeconds = (now - lastStartingAnimationFrameNanos) / 1_000_000_000f;
    lastStartingAnimationFrameNanos = now;
    startingAnimationOffset += elapsedSeconds * STARTING_ANIMATION_SPEED_PX_PER_SECOND;
    for (String configurationName : pendingStarts)
    {
      ServiceCard card = cardsByConfiguration.get(configurationName);
      if (card != null)
      {
        card.repaint();
      }
    }
  }

  /**
   * Applies updated application-level preferences to the open launcher.
   */
  private void preferencesChanged()
  {
    ApplicationManager.getApplication().invokeLater(() ->
    {
      if (project.isDisposed())
      {
        return;
      }
      refreshPreferenceValues();
      rebuild();
    });
  }

  /**
   * Refreshes preference values used while rendering service cards.
   */
  private void refreshPreferenceValues()
  {
    primaryColor = preferences.primaryColor();
    startingBorder = new StartingBorder(primaryColor, () -> startingAnimationOffset);
    simpleStartingIcon = new SimpleStartingIcon(primaryColor, () -> startingAnimationOffset);
    if (selectionToggleButton != null)
    {
      selectionToggleButton.setVisible(preferences.showToggleAllButton());
    }
    synchronizeStartingAnimation();
  }

  /**
   * Edits the launcher configurations.
   */
  private void editLauncherConfigurations()
  {
    List<ServiceLauncherSettings.LauncherConfiguration> current = launcherConfigurations();
    ServiceLauncherSettingsDialog dialog = new ServiceLauncherSettingsDialog(project, current);
    if (dialog.showAndGet())
    {
      ServiceLauncherSettings.getInstance(project).replaceWith(dialog.getConfigurations());
      rebuild();
    }
  }

  /**
   * Edits the launcher configuration.
   *
   * @param source the source
   */
  private void editLauncherConfiguration(ServiceLauncherSettings.LauncherConfiguration source)
  {
    List<ServiceLauncherSettings.LauncherConfiguration> current = launcherConfigurations();
    Set<String> groups = new LinkedHashSet<>();
    current.forEach(item -> groups.add(ServiceLauncherSettings.normalizeGroup(item.group)));
    int maxOrder = current.size();
    LauncherConfigurationEditorOptions options =
      new LauncherConfigurationEditorOptions(groups, availableByName.keySet(), maxOrder);
    LauncherConfigurationDialogRequest request = LauncherConfigurationDialogRequest.edit(source, options);
    LauncherConfigurationDialog dialog = new LauncherConfigurationDialog(project, request);
    if (dialog.showAndGet())
    {
      applyEditedLauncherConfiguration(current, dialog.getConfiguration());
      ServiceLauncherSettings.getInstance(project).replaceWith(current);
      rebuild();
    }
  }

  /**
   * Creates the service.
   *
   * @param initialGroup the initial group
   */
  private void createService(String initialGroup)
  {
    List<ServiceLauncherSettings.LauncherConfiguration> current = launcherConfigurations();
    List<String> groups = groupNames();
    String group = initialGroup == null
      ? (groups.isEmpty() ? ServiceLauncherSettings.DEFAULT_GROUP : groups.get(0))
      : initialGroup;
    ServiceLauncherSettings.LauncherConfiguration created =
      new ServiceLauncherSettings.LauncherConfiguration("", (int) current.stream()
        .filter(item -> group.equals(ServiceLauncherSettings.normalizeGroup(item.group)))
        .count());
    created.group = group;
    created.visible = true;
    LauncherConfigurationEditorOptions options =
      new LauncherConfigurationEditorOptions(groups, availableByName.keySet(), current.size() + 1);
    LauncherConfigurationDialogRequest request = LauncherConfigurationDialogRequest.create(created, options);
    LauncherConfigurationDialog dialog = new LauncherConfigurationDialog(project, request);
    if (dialog.showAndGet())
    {
      ServiceLauncherSettings.LauncherConfiguration configuration = dialog.getConfiguration();
      applyEditedLauncherConfiguration(current, configuration);
      ServiceLauncherSettings.getInstance(project).replaceWith(current);
      rebuild();
    }
  }

  /**
   * Creates the group.
   */
  private void createGroup()
  {
    String requested = Messages.showInputDialog(
      project,
      "Enter a name for the new group:",
      "Create Group",
      Messages.getQuestionIcon()
    );
    if (requested == null)
    {
      return;
    }
    String group = requested.trim();
    if (group.isEmpty())
    {
      Messages.showErrorDialog(project, "The group name cannot be empty.", "Create Group");
      return;
    }
    if (!ServiceLauncherSettings.getInstance(project).addGroup(group))
    {
      Messages.showErrorDialog(project, "A group with that name already exists.", "Create Group");
      return;
    }
    rebuild();
  }

  /**
   * Removes the service.
   *
   * @param service the service
   */
  private void removeService(ServiceLauncherSettings.LauncherConfiguration service)
  {
    String name = service.displayName == null || service.displayName.isBlank()
      ? (service.configurationName == null || service.configurationName.isBlank()
        ? "this launcher configuration"
        : service.configurationName)
      : service.displayName;
    int choice = Messages.showYesNoDialog(
      project,
      "Delete the launcher configuration '" + name + "'?",
      "Delete Launcher Configuration",
      "Delete",
      "Cancel",
      Messages.getWarningIcon()
    );
    if (choice != Messages.YES)
    {
      return;
    }
    selected.remove(service.itemId);
    if (ServiceLauncherSettings.getInstance(project).removeService(service.itemId))
    {
      rebuild();
    }
  }

  /**
   * Applies the edited launcher configuration.
   *
   * @param current the current
   * @param edited the edited
   */
  private void applyEditedLauncherConfiguration(List<ServiceLauncherSettings.LauncherConfiguration> current,
                   ServiceLauncherSettings.LauncherConfiguration edited)
                   {
    if (edited.configurationName != null && !edited.configurationName.isBlank())
    {
      for (ServiceLauncherSettings.LauncherConfiguration item : current)
      {
        if (!item.itemId.equals(edited.itemId)
          && edited.configurationName.equals(item.configurationName))
          {
          item.expectedConfigurationName = item.configurationName;
          item.configurationName = "";
          }
      }
    }
    current.removeIf(item -> item.itemId.equals(edited.itemId));
    String targetGroup = ServiceLauncherSettings.normalizeGroup(edited.group);
    int insertionIndex = current.size();
    int seenInGroup = 0;
    for (int index = 0; index < current.size(); index++)
    {
      ServiceLauncherSettings.LauncherConfiguration configuration = current.get(index);
      if (targetGroup.equals(ServiceLauncherSettings.normalizeGroup(configuration.group)))
      {
        if (seenInGroup == edited.order)
        {
          insertionIndex = index;
          break;
        }
        insertionIndex = index + 1;
        seenInGroup++;
      }
    }
    current.add(insertionIndex, edited);
    ServiceLauncherSettings.normalizeOrders(current);
                   }

  /**
   * Moves the within group.
   *
   * @param source the source
   * @param delta the delta
   */
  private void moveWithinGroup(ServiceLauncherSettings.LauncherConfiguration source, int delta)
  {
    List<ServiceLauncherSettings.LauncherConfiguration> current = launcherConfigurations();
    List<ServiceLauncherSettings.LauncherConfiguration> group = current.stream()
      .filter(item -> ServiceLauncherSettings.normalizeGroup(item.group)
        .equals(ServiceLauncherSettings.normalizeGroup(source.group)))
      .sorted(Comparator.comparingInt(item -> item.order))
      .toList();
    int index = -1;
    for (int groupIndex = 0; groupIndex < group.size(); groupIndex++)
    {
      if (group.get(groupIndex).itemId.equals(source.itemId))
      {
        index = groupIndex;
        break;
      }
    }
    int target = index + delta;
    if (index < 0 || target < 0 || target >= group.size())
    {
      return;
    }
    int oldOrder = group.get(index).order;
    group.get(index).order = group.get(target).order;
    group.get(target).order = oldOrder;
    ServiceLauncherSettings.normalizeOrders(current);
    rebuild();
  }

  /**
   * Moves the to group.
   *
   * @param source the source
   * @param group the group
   */
  private void moveToGroup(ServiceLauncherSettings.LauncherConfiguration source, String group)
  {
    ServiceLauncherSettings.LauncherConfiguration edited = source.copy();
    edited.group = group;
    List<ServiceLauncherSettings.LauncherConfiguration> current = launcherConfigurations();
    edited.order = (int) current.stream()
      .filter(item -> ServiceLauncherSettings.normalizeGroup(item.group).equals(group)).count();
    applyEditedLauncherConfiguration(current, edited);
    ServiceLauncherSettings.getInstance(project).replaceWith(current);
    rebuild();
  }

  /**
   * Performs the rename group operation.
   *
   * @param sourceGroup the source group
   */
  private void renameGroup(String sourceGroup)
  {
    String requested = Messages.showInputDialog(
      project,
      "Enter a new name for the group:",
      "Rename Group",
      Messages.getQuestionIcon(),
      sourceGroup,
      null
    );
    if (requested == null)
    {
      return;
    }
    String targetGroup = requested.trim();
    if (targetGroup.isEmpty())
    {
      Messages.showErrorDialog(project, "The group name cannot be empty.", "Rename Group");
      return;
    }
    if (sourceGroup.equals(targetGroup))
    {
      return;
    }
    ServiceLauncherSettings settings = ServiceLauncherSettings.getInstance(project);
    if (!settings.renameGroup(sourceGroup, targetGroup))
    {
      Messages.showErrorDialog(project, "A group with that name already exists.", "Rename Group");
      return;
    }
    rebuild();
  }

  /**
   * Moves the group.
   *
   * @param sourceGroup the source group
   * @param delta the delta
   */
  private void moveGroup(String sourceGroup, int delta)
  {
    if (ServiceLauncherSettings.getInstance(project).moveGroup(sourceGroup, delta))
    {
      rebuild();
    }
  }

  /**
   * Removes the group.
   *
   * @param group the group
   */
  private void removeGroup(String group)
  {
    if (ServiceLauncherSettings.getInstance(project).removeGroup(group))
    {
      rebuild();
    }
  }

  /**
   * Returns the result of group names.
   *
   * @return the group names result
   */
  private List<String> groupNames()
  {
    return ServiceLauncherSettings.getInstance(project).groupNames();
  }

  /**
   * Performs the install group popup operation.
   *
   * @param component the component
   * @param group the group
   */
  private void installGroupPopup(JComponent component, String group)
  {
    component.addMouseListener(new MouseAdapter()
    {
      /** {@inheritDoc} */
      @Override
      public void mousePressed(MouseEvent event)
      {
        showGroupPopup(event, group);
      }

      /** {@inheritDoc} */
      @Override
      public void mouseReleased(MouseEvent event)
      {
        showGroupPopup(event, group);
      }
    });
  }

  /**
   * Shows the group popup.
   *
   * @param event the event
   * @param group the group
   */
  private void showGroupPopup(MouseEvent event, String group)
  {
    if (!event.isPopupTrigger())
    {
      return;
    }
    List<String> groups = groupNames();
    int groupIndex = groups.indexOf(group);
    JPopupMenu menu = new JPopupMenu();
    JMenuItem addService = new JMenuItem("Add service to group…");
    addService.addActionListener(actionEvent -> createService(group));
    JMenuItem addGroup = new JMenuItem("Add group…");
    addGroup.addActionListener(actionEvent -> createGroup());
    menu.add(addService);
    menu.add(addGroup);
    menu.addSeparator();
    JMenuItem rename = new JMenuItem("Rename group…", AllIcons.Actions.Edit);
    rename.addActionListener(actionEvent -> renameGroup(group));
    JMenuItem moveUp = new JMenuItem("Move group up");
    moveUp.setEnabled(groupIndex > 0);
    moveUp.addActionListener(actionEvent -> moveGroup(group, -1));
    JMenuItem moveDown = new JMenuItem("Move group down");
    moveDown.setEnabled(groupIndex >= 0 && groupIndex < groups.size() - 1);
    moveDown.addActionListener(actionEvent -> moveGroup(group, 1));
    JMenuItem remove = new JMenuItem("Remove empty group");
    remove.setEnabled(launcherConfigurations().stream()
      .noneMatch(item -> group.equalsIgnoreCase(ServiceLauncherSettings.normalizeGroup(item.group))));
    remove.addActionListener(actionEvent -> removeGroup(group));
    menu.add(rename);
    menu.add(moveUp);
    menu.add(moveDown);
    menu.add(remove);
    menu.addSeparator();
    JMenuItem selectAllItem = new JMenuItem("Select all visible services");
    selectAllItem.addActionListener(actionEvent -> selectAll());
    JMenuItem selectNoneItem = new JMenuItem("Clear selection");
    selectNoneItem.addActionListener(actionEvent -> selectNone());
    menu.add(selectAllItem);
    menu.add(selectNoneItem);
    menu.show((Component) event.getSource(), event.getX(), event.getY());
  }

  /**
   * Performs the session started operation.
   *
   * @param executorId the executor id
   * @param environment the environment
   * @param handler the handler
   */
  private void sessionStarted(String executorId, ExecutionEnvironment environment, ProcessHandler handler)
  {
    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    if (settings == null)
    {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() ->
    {
      String name = settings.getName();
      clearStarting(name);
      if (!cardsByConfiguration.containsKey(name))
      {
        refreshLaunchActionState();
        return;
      }
      ExecutionMode mode = ExecutionMode.fromExecutorId(executorId);
      List<RunningSession> sessions = runningSessions.computeIfAbsent(name, ignored -> new ArrayList<>());
      if (sessions.stream().noneMatch(session -> session.handler() == handler))
      {
        sessions.add(new RunningSession(handler, mode));
      }
      refreshCardsForConfiguration(name);
      refreshAggregateActionState();
    });
  }

  /**
   * Performs the session not started operation.
   *
   * @param environment the environment
   */
  private void sessionNotStarted(ExecutionEnvironment environment)
  {
    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    if (settings == null)
    {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() ->
    {
      clearStarting(settings.getName());
    });
  }

  /**
   * Performs the session terminated operation.
   *
   * @param environment the environment
   * @param handler the handler
   */
  private void sessionTerminated(ExecutionEnvironment environment, ProcessHandler handler)
  {
    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    if (settings == null)
    {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() ->
    {
      String name = settings.getName();
      List<RunningSession> sessions = runningSessions.get(name);
      if (sessions != null)
      {
        sessions.removeIf(session -> session.handler() == handler);
        if (sessions.isEmpty())
        {
          runningSessions.remove(name);
        }
      }
      refreshCardsForConfiguration(name);
      refreshAggregateActionState();
      if (!isRunning(name) && pendingRestarts.containsKey(name))
      {
        ExecutionMode mode = pendingRestarts.remove(name);
        RunnerAndConfigurationSettings configuration = availableByName.get(name);
        if (configuration != null)
        {
          launch(configuration, mode);
        }
      }
    });
  }

  /**
   * Rebuilds the later.
   */
  private void rebuildLater()
  {
    if (!rebuildScheduled.compareAndSet(false, true))
    {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() ->
    {
      rebuildScheduled.set(false);
      if (!project.isDisposed())
      {
        rebuild();
      }
    });
  }

  /**
   * Refreshes the cards for configuration.
   *
   * @param configurationName the configuration name
   */
  private void refreshCardsForConfiguration(String configurationName)
  {
    ServiceCard card = cardsByConfiguration.get(configurationName);
    if (card != null)
    {
      card.refreshState();
    }
  }

  /**
   * Refreshes the aggregate action state.
   */
  private void refreshAggregateActionState()
  {
    boolean anyRunning = !runningSessions.isEmpty();
    if (restartAllButton != null && restartAllButton.isEnabled() != anyRunning)
    {
      restartAllButton.setEnabled(anyRunning);
    }
    if (stopAllButton != null && stopAllButton.isEnabled() != anyRunning)
    {
      stopAllButton.setEnabled(anyRunning);
    }
    refreshLaunchActionState();
  }

  /**
   * Refreshes the launch action state.
   */
  private void refreshLaunchActionState()
  {
    if (runButton == null || debugButton == null)
    {
      return;
    }
    boolean hasPendingService = false;
    boolean hasStartableService = false;
    for (String itemId : selected)
    {
      ServiceCard card = cards.get(itemId);
      if (card == null || card.settings == null
        || card.launcherConfiguration.configurationName == null || card.launcherConfiguration.configurationName.isBlank())
        {
        continue;
        }
      String configurationName = card.launcherConfiguration.configurationName;
      boolean pending = pendingStarts.contains(configurationName);
      hasPendingService |= pending;
      hasStartableService |= !pending && !isRunning(configurationName);
      if (hasPendingService && hasStartableService)
      {
        break;
      }
    }
    boolean showStarting = hasPendingService && !hasStartableService;
    StartActionState state = new StartActionState(showStarting, hasStartableService);
    updateStartButton(
      runButton,
      new StartButtonPresentation(
        AllIcons.Actions.Execute,
        "Run selected services that are not already running"
      ),
      state
    );
    updateStartButton(
      debugButton,
      new StartButtonPresentation(
        AllIcons.Actions.StartDebugger,
        "Debug selected services that are not already running"
      ),
      state
    );
  }

  /**
   * Updates the start button.
   *
   * @param button the button
   * @param presentation the presentation
   * @param state the state
   */
  private void updateStartButton(
    JButton button,
    StartButtonPresentation presentation,
    StartActionState state
  )
  {
    boolean wasStarting = Boolean.TRUE.equals(button.getClientProperty("serviceLauncher.starting"));
    if (wasStarting != state.starting())
    {
      button.putClientProperty("serviceLauncher.starting", state.starting());
      button.setIcon(state.starting() ? startingIcon : presentation.icon());
      button.setDisabledIcon(
        state.starting() ? startingIcon : IconLoader.getDisabledIcon(presentation.icon())
      );
    }
    String tooltip = state.starting() ? "Starting selected services…" : presentation.tooltip();
    if (!tooltip.equals(button.getToolTipText()))
    {
      button.setToolTipText(tooltip);
    }
    if (button.isEnabled() != state.isEnabled())
    {
      button.setEnabled(state.isEnabled());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void dispose()
  {
    startingAnimationTimer.stop();
  }

  /**
   * Represents a service card.
   */
  private final class ServiceCard extends JLayeredPane
  {
    private final ServiceLauncherSettings.LauncherConfiguration launcherConfiguration;
    private final RunnerAndConfigurationSettings settings;
    private final JPanel content = new JPanel(new BorderLayout(5, 0));
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
    private final JBLabel linkWarning = new JBLabel(AllIcons.General.Warning);
    private final JButton run;
    private final JButton debug;
    private final JButton restart;
    private final JButton stop;
    private final Icon launcherIcon;
    private final JBLabel serviceIcon;
    private final JBLabel nameLabel;
    private final Color normalTextColor;

    /**
     * Creates a new {@code ServiceCard} instance.
     *
     * @param launcherConfiguration the launcher configuration
     * @param settings the settings
     */
    private ServiceCard(ServiceLauncherSettings.LauncherConfiguration launcherConfiguration,
              RunnerAndConfigurationSettings settings)
              {
      this.launcherConfiguration = launcherConfiguration;
      this.settings = settings;
      setOpaque(true);
      setBackground(CARD_BACKGROUND);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setPreferredSize(new Dimension(MIN_CARD_WIDTH, 48));
      setMinimumSize(new Dimension(140, 44));

      launcherIcon = LauncherIcons.get(launcherConfiguration);
      serviceIcon = new JBLabel(launcherIcon);
      serviceIcon.setDisabledIcon(IconLoader.getDisabledIcon(launcherIcon));
      serviceIcon.setVerticalAlignment(SwingConstants.CENTER);
      serviceIcon.setBorder(BorderFactory.createEmptyBorder(0, 9, 0, 0));
      content.add(serviceIcon, BorderLayout.WEST);

      String linkedOrExpectedName = launcherConfiguration.configurationName == null
        || launcherConfiguration.configurationName.isBlank()
        ? launcherConfiguration.expectedConfigurationName
        : launcherConfiguration.configurationName;
      String cardName = launcherConfiguration.displayName == null || launcherConfiguration.displayName.isBlank()
        ? (linkedOrExpectedName == null || linkedOrExpectedName.isBlank() ? "Unlinked service" : linkedOrExpectedName)
        : launcherConfiguration.displayName;
      nameLabel = new JBLabel(toHtml(cardName), SwingConstants.CENTER);
      nameLabel.setVerticalAlignment(SwingConstants.CENTER);
      nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, nameLabel.getFont().getSize2D()));
      nameLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 5));
      normalTextColor = nameLabel.getForeground();
      content.add(nameLabel, BorderLayout.CENTER);
      content.setOpaque(false);
      add(content, JLayeredPane.DEFAULT_LAYER);

      actions.setOpaque(false);
      run = compactButton(AllIcons.Actions.Execute, "Run " + cardName);
      run.addActionListener(actionEvent -> startService(launcherConfiguration.configurationName, ExecutionMode.RUN));
      debug = compactButton(AllIcons.Actions.StartDebugger, "Debug " + cardName);
      debug.addActionListener(actionEvent -> startService(launcherConfiguration.configurationName, ExecutionMode.DEBUG));
      restart = compactButton(AllIcons.Actions.Restart, "Restart " + cardName);
      restart.addActionListener(actionEvent -> restartService(launcherConfiguration.configurationName));
      stop = compactButton(AllIcons.Actions.StopRefresh, "Stop " + cardName);
      stop.addActionListener(actionEvent -> stopService(launcherConfiguration.configurationName));
      actions.add(run);
      actions.add(debug);
      actions.add(restart);
      actions.add(stop);
      add(actions, JLayeredPane.PALETTE_LAYER);
      String expectedName = launcherConfiguration.expectedConfigurationName == null
        ? ""
        : launcherConfiguration.expectedConfigurationName;
      linkWarning.setToolTipText(expectedName.isBlank()
        ? "This item is not linked to a run configuration. Right-click to connect it."
        : "Run configuration '" + expectedName + "' was not found. Right-click to reconnect it.");
      linkWarning.setVisible(settings == null);
      add(linkWarning, JLayeredPane.PALETTE_LAYER);

      MouseAdapter listener = new MouseAdapter()
      {
        private boolean selectionArmed;

        /** {@inheritDoc} */
        @Override
        public void mousePressed(MouseEvent event)
        {
          maybeShowPopup(event);
          if (SwingUtilities.isLeftMouseButton(event))
          {
            selectionArmed = true;
          }
        }

        /** {@inheritDoc} */
        @Override
        public void mouseReleased(MouseEvent event)
        {
          maybeShowPopup(event);
          if (selectionArmed && SwingUtilities.isLeftMouseButton(event))
          {
            selectionArmed = false;
            Point releasePoint = SwingUtilities.convertPoint(
              event.getComponent(), event.getPoint(), ServiceCard.this
            );
            if (contains(releasePoint))
            {
              toggleSelection();
            }
          }
          else
          {
            selectionArmed = false;
          }
        }

        /** {@inheritDoc} */
        @Override
        public void mouseExited(MouseEvent event)
        {
          Point pointer = SwingUtilities.convertPoint(
            event.getComponent(), event.getPoint(), ServiceCard.this
          );
          if (!contains(pointer) && (event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0)
          {
            selectionArmed = false;
          }
        }
      };
      addMouseListener(listener);
      content.addMouseListener(listener);
      nameLabel.addMouseListener(listener);
      serviceIcon.addMouseListener(listener);
      refreshState();
              }

    /** {@inheritDoc} */
    @Override
    public void doLayout()
    {
      content.setBounds(0, 0, getWidth(), getHeight());
      Dimension actionSize = actions.getPreferredSize();
      actions.setBounds(Math.max(2, getWidth() - actionSize.width - 2), 2, actionSize.width, actionSize.height);
      Dimension warningSize = linkWarning.getPreferredSize();
      linkWarning.setBounds(2, 2, warningSize.width, warningSize.height);
    }

    /**
     * Performs the maybe show popup operation.
     *
     * @param event the event
     */
    private void maybeShowPopup(MouseEvent event)
    {
      if (!event.isPopupTrigger())
      {
        return;
      }
      JPopupMenu menu = new JPopupMenu();
      JMenuItem edit = new JMenuItem("Edit launcherConfiguration…", AllIcons.Actions.Edit);
      edit.addActionListener(actionEvent -> editLauncherConfiguration(launcherConfiguration));
      menu.add(edit);
      menu.addSeparator();

      JMenuItem moveUp = new JMenuItem("Move up");
      moveUp.addActionListener(actionEvent -> moveWithinGroup(launcherConfiguration, -1));
      JMenuItem moveDown = new JMenuItem("Move down");
      moveDown.addActionListener(actionEvent -> moveWithinGroup(launcherConfiguration, 1));
      menu.add(moveUp);
      menu.add(moveDown);

      JMenu groups = new JMenu("Move to group");
      groupNames().stream()
        .filter(group -> !group.equals(ServiceLauncherSettings.normalizeGroup(launcherConfiguration.group)))
        .forEach(group ->
        {
          JMenuItem target = new JMenuItem(group);
          target.addActionListener(actionEvent -> moveToGroup(launcherConfiguration, group));
          groups.add(target);
        });
      menu.add(groups);
      menu.addSeparator();
      JMenuItem remove = new JMenuItem("Delete launcher configuration…", AllIcons.General.Remove);
      remove.addActionListener(actionEvent -> removeService(launcherConfiguration));
      menu.add(remove);
      menu.addSeparator();
      JMenuItem selection = new JMenuItem(selected.contains(launcherConfiguration.itemId) ? "Deselect" : "Select");
      selection.addActionListener(actionEvent -> toggleSelection());
      menu.add(selection);
      menu.show((Component) event.getSource(), event.getX(), event.getY());
    }

    /**
     * Toggles the selection.
     */
    private void toggleSelection()
    {
      if (!selected.remove(launcherConfiguration.itemId))
      {
        selected.add(launcherConfiguration.itemId);
      }
      refreshState();
      refreshSelectionControls();
      refreshLaunchActionState();
    }

    /**
     * Returns the result of compact button.
     *
     * @param icon the icon
     * @param tooltip the tooltip
     * @return the compact button result
     */
    private JButton compactButton(Icon icon, String tooltip)
    {
      Icon compactIcon = LauncherIcons.scaleIcon(icon, 10);
      return new HoverIconButton(compactIcon, tooltip, HoverIconButton.Size.COMPACT);
    }

    /**
     * Refreshes the border.
     */
    private void refreshBorder()
    {
      refreshState();
    }

    /**
     * Refreshes the state.
     */
    private void refreshState()
    {
      boolean selectedNow = selected.contains(launcherConfiguration.itemId);
      boolean runningNow = isRunning(launcherConfiguration.configurationName);
      boolean startingNow = pendingStarts.contains(launcherConfiguration.configurationName);
      if (startingNow)
      {
        boolean prettyAnimation =
          preferences.startupAnimation() == ServiceLauncherPreferences.StartupAnimation.PRETTY;
        setBorder(prettyAnimation
          ? startingBorder
          : BorderFactory.createLineBorder(selectedNow ? SELECTED_BORDER : UNSELECTED_BORDER));
        serviceIcon.setIcon(prettyAnimation ? launcherIcon : simpleStartingIcon);
        serviceIcon.setDisabledIcon(prettyAnimation
          ? IconLoader.getDisabledIcon(launcherIcon)
          : simpleStartingIcon);
      }
      else
      {
        Color borderColor = runningNow
          ? primaryColor
          : (selectedNow ? SELECTED_BORDER : UNSELECTED_BORDER);
        setBorder(BorderFactory.createLineBorder(borderColor, runningNow ? RUNNING_BORDER_WIDTH : 1));
        serviceIcon.setIcon(launcherIcon);
        serviceIcon.setDisabledIcon(IconLoader.getDisabledIcon(launcherIcon));
      }
      setBackground(selectedNow ? CARD_BACKGROUND : UNSELECTED_CARD_BACKGROUND);
      serviceIcon.setEnabled(selectedNow);
      nameLabel.setForeground(selectedNow ? normalTextColor : UNSELECTED_TEXT);
      boolean canStart = settings != null && !runningNow && !startingNow;
      boolean showStartControls = canStart && preferences.perConfigStartDebugButtons();
      boolean showRunningControls = runningNow && preferences.perConfigRestartStopButtons();
      run.setVisible(showStartControls);
      debug.setVisible(showStartControls);
      restart.setVisible(showRunningControls);
      stop.setVisible(showRunningControls);
      actions.setVisible(showStartControls || showRunningControls);
      repaint();
    }

    /**
     * Returns the result of to html.
     *
     * @param value the value
     * @return the to html result
     */
    private String toHtml(String value)
    {
      String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
      return "<html><div style='text-align:center'>" + escaped.replace("\n", "<br>") + "</div></html>";
    }
  }

}
