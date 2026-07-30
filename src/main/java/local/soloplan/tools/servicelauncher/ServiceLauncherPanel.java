package local.soloplan.tools.servicelauncher;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
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
import javax.swing.ButtonModel;
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
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class ServiceLauncherPanel extends JPanel implements Disposable {
    private static final int MIN_CARD_WIDTH = 180;
    private static final int CARD_GAP = 6;
    private static final int RUNNING_BORDER_WIDTH = 2;
    private static final int STARTING_ANIMATION_FRAME_DELAY_MS = 16;
    private static final float STARTING_ANIMATION_SPEED_PX_PER_SECOND = 87.5f;
    private static final Color RUNNING_ACCENT = new JBColor(new Color(0xE8007F), new Color(0xFB068D));
    private static final Color CARD_BACKGROUND = new JBColor(new Color(0xF2F2F2), new Color(0x343434));
    private static final Color UNSELECTED_CARD_BACKGROUND =
        new JBColor(new Color(0xDADADA), new Color(0x292929));
    private static final Color UNSELECTED_TEXT =
        new JBColor(new Color(0x747474), new Color(0x929292));
    private static final Color MUTED_BORDER = new JBColor(new Color(0xA0A0A0), new Color(0x8B8B8B));
    private static final Color UNSELECTED_BORDER =
        new JBColor(new Color(0xB8B8B8), new Color(0x515151));
    private static final Color HOVER_BACKGROUND = new JBColor(new Color(0xDFE1E5), new Color(0x45474D));
    private static final Color PRESSED_BACKGROUND = new JBColor(new Color(0xC9CCD2), new Color(0x55575E));

    private final Project project;
    private final VerticalScrollablePanel groupsPanel = new VerticalScrollablePanel();
    private final Set<String> selected = new HashSet<>();
    private final Map<String, List<RunningSession>> runningSessions = new HashMap<>();
    private final Map<String, Boolean> pendingRestarts = new HashMap<>();
    private final Set<String> pendingStarts = new HashSet<>();
    private final Map<String, ServiceCard> cards = new HashMap<>();
    private final Map<String, ServiceCard> cardsByConfiguration = new HashMap<>();
    private final AtomicBoolean rebuildScheduled = new AtomicBoolean();
    private final Timer startingAnimationTimer =
        new Timer(STARTING_ANIMATION_FRAME_DELAY_MS, event -> advanceStartingAnimation());
    private final Border startingBorder = new StartingBorder();
    private final MessageBusConnection connection;
    private Map<String, RunnerAndConfigurationSettings> availableByName = Map.of();
    private Map<String, ServiceLauncherSettings.ServiceAppearance> appearancesById = Map.of();
    private List<ServiceLauncherSettings.ServiceAppearance> currentAppearances = List.of();
    private JButton runButton;
    private JButton debugButton;
    private JButton restartAllButton;
    private JButton stopAllButton;
    private final Icon startingIcon = new AnimatedIcon.Default();
    private float startingAnimationOffset;
    private long lastStartingAnimationFrameNanos;

    ServiceLauncherPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
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

        connection = project.getMessageBus().connect(this);
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment,
                                       @NotNull ProcessHandler handler) {
                sessionStarted(executorId, environment, handler);
            }

            @Override
            public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment) {
                sessionNotStarted(environment);
            }

            @Override
            public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment environment,
                                          @NotNull ProcessHandler handler, int exitCode) {
                sessionTerminated(environment, handler);
            }
        });
        connection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
            @Override
            public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
                rebuildLater();
            }

            @Override
            public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
                rebuildLater();
            }

            @Override
            public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings, String existingId) {
                rebuildLater();
            }
        });

        rebuild();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JButton edit = iconButton(AllIcons.Actions.Edit, "Customize services");
        edit.addActionListener(e -> editAppearances());
        toolbar.add(edit, BorderLayout.WEST);

        JPanel launchActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        launchActions.setOpaque(false);
        runButton = iconButton(AllIcons.Actions.Execute, "Run selected services that are not already running");
        runButton.addActionListener(e -> startMissingSelected(false));
        debugButton = iconButton(AllIcons.Actions.StartDebugger, "Debug selected services that are not already running");
        debugButton.addActionListener(e -> startMissingSelected(true));
        restartAllButton = iconButton(AllIcons.Actions.Restart, "Start missing and restart all running selected services");
        restartAllButton.addActionListener(e -> restartSelected());
        stopAllButton = iconButton(AllIcons.Actions.StopRefresh, "Stop all services running from this launcher");
        stopAllButton.addActionListener(e -> stopAllServices());
        launchActions.add(runButton);
        launchActions.add(debugButton);
        launchActions.add(restartAllButton);
        launchActions.add(stopAllButton);
        toolbar.add(launchActions, BorderLayout.EAST);
        refreshAggregateActionState();
        return toolbar;
    }

    private JButton iconButton(Icon icon, String tooltip) {
        return new HoverIconButton(icon, tooltip, 34, 30);
    }

    private void rebuild() {
        cards.clear();
        cardsByConfiguration.clear();
        groupsPanel.removeAll();

        Map<String, RunnerAndConfigurationSettings> available = availableConfigurations();
        List<ServiceLauncherSettings.ServiceAppearance> appearances = ServiceLauncherSettings.getInstance(project)
            .synchronizedWith(new ArrayList<>(available.keySet()));
        availableByName = available;
        currentAppearances = appearances;
        Map<String, ServiceLauncherSettings.ServiceAppearance> byId = new HashMap<>();
        appearances.forEach(item -> byId.put(item.itemId, item));
        appearancesById = byId;
        Map<String, List<ServiceLauncherSettings.ServiceAppearance>> groups = new LinkedHashMap<>();
        appearances.stream()
            .filter(item -> item.visible)
            .forEach(item -> groups.computeIfAbsent(ServiceLauncherSettings.normalizeGroup(item.group), ignored -> new ArrayList<>())
                .add(item));

        if (groups.isEmpty()) {
            JBLabel emptyState = new JBLabel(
                "<html><div style='text-align:center'>No visible permanent run configurations.<br>" +
                    "Create configurations or use the pencil button to show them.</div></html>",
                SwingConstants.CENTER
            );
            emptyState.setAlignmentX(Component.CENTER_ALIGNMENT);
            groupsPanel.add(Box.createVerticalGlue());
            groupsPanel.add(emptyState);
            groupsPanel.add(Box.createVerticalGlue());
        } else {
            for (Map.Entry<String, List<ServiceLauncherSettings.ServiceAppearance>> entry : groups.entrySet()) {
                ResponsiveGroupPanel group = new ResponsiveGroupPanel(entry.getKey(), entry.getValue(), available);
                group.setAlignmentX(Component.LEFT_ALIGNMENT);
                groupsPanel.add(group);
                groupsPanel.add(Box.createVerticalStrut(14));
            }
            groupsPanel.add(Box.createVerticalGlue());
        }

        selected.retainAll(cards.keySet());
        pendingStarts.retainAll(available.keySet());
        synchronizeStartingAnimation();
        refreshAggregateActionState();
        groupsPanel.revalidate();
        groupsPanel.repaint();
    }

    private List<ServiceLauncherSettings.ServiceAppearance> appearances() {
        return currentAppearances;
    }

    private Map<String, RunnerAndConfigurationSettings> availableConfigurations() {
        Map<String, RunnerAndConfigurationSettings> result = new LinkedHashMap<>();
        RunManager.getInstance(project).getAllSettings().stream()
            .filter(settings -> !settings.isTemporary())
            .sorted(Comparator.comparing(RunnerAndConfigurationSettings::getName, String.CASE_INSENSITIVE_ORDER))
            .forEach(settings -> result.put(settings.getName(), settings));
        return result;
    }

    private void selectAll() {
        selected.addAll(cards.keySet());
        cards.values().forEach(ServiceCard::refreshBorder);
        refreshLaunchActionState();
    }

    private void selectNone() {
        selected.clear();
        cards.values().forEach(ServiceCard::refreshBorder);
        refreshLaunchActionState();
    }

    private void installSelectionPopup(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showSelectionPopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showSelectionPopup(event);
            }
        });
    }

    private void showSelectionPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem selectAllItem = new JMenuItem("Select all visible services");
        selectAllItem.addActionListener(e -> selectAll());
        JMenuItem selectNoneItem = new JMenuItem("Clear selection");
        selectNoneItem.addActionListener(e -> selectNone());
        menu.add(selectAllItem);
        menu.add(selectNoneItem);
        menu.show((Component) event.getSource(), event.getX(), event.getY());
    }

    private void startMissingSelected(boolean debug) {
        if (selected.isEmpty()) {
            Messages.showInfoMessage(project, "Select one or more service cards first.", "Service Launcher");
            return;
        }
        Map<String, RunnerAndConfigurationSettings> available = availableByName;
        List<RunnerAndConfigurationSettings> toStart = selected.stream()
            .map(appearancesById::get)
            .filter(item -> item != null && item.configurationName != null && !item.configurationName.isBlank())
            .filter(item -> !isRunning(item.configurationName) && !pendingStarts.contains(item.configurationName))
            .map(item -> available.get(item.configurationName))
            .filter(settings -> settings != null)
            .toList();
        markStarting(toStart.stream().map(RunnerAndConfigurationSettings::getName).toList());
        toStart.forEach(settings -> launch(settings, debug));
    }

    private void restartSelected() {
        if (selected.isEmpty()) {
            Messages.showInfoMessage(project, "Select one or more service cards first.", "Service Launcher");
            return;
        }
        for (String itemId : new ArrayList<>(selected)) {
            ServiceLauncherSettings.ServiceAppearance item = appearancesById.get(itemId);
            if (item != null && item.configurationName != null && !item.configurationName.isBlank()) {
                restartService(item.configurationName);
            }
        }
    }

    private void restartService(String configurationName) {
        RunnerAndConfigurationSettings settings = availableByName.get(configurationName);
        if (settings == null) {
            return;
        }
        List<RunningSession> sessions = runningSessions.get(configurationName);
        if (sessions == null || sessions.isEmpty()) {
            launch(settings, false);
            return;
        }
        boolean debug = sessions.stream().anyMatch(session -> session.debug);
        pendingRestarts.put(configurationName, debug);
        sessions.stream().map(session -> session.handler).distinct().forEach(this::stopHandler);
    }

    private void stopService(String configurationName) {
        pendingRestarts.remove(configurationName);
        List<RunningSession> sessions = runningSessions.get(configurationName);
        if (sessions != null) {
            sessions.stream().map(session -> session.handler).distinct().forEach(this::stopHandler);
        }
    }

    private void stopAllServices() {
        pendingRestarts.clear();
        runningSessions.values().stream().flatMap(List::stream).map(session -> session.handler).distinct()
            .forEach(this::stopHandler);
    }

    private void stopHandler(ProcessHandler handler) {
        if (!handler.isProcessTerminated() && !handler.isProcessTerminating()) {
            handler.destroyProcess();
        }
    }

    private boolean isRunning(String configurationName) {
        if (configurationName == null || configurationName.isBlank()) {
            return false;
        }
        List<RunningSession> sessions = runningSessions.get(configurationName);
        return sessions != null && !sessions.isEmpty();
    }

    private void launch(RunnerAndConfigurationSettings settings, boolean debug) {
        markStarting(List.of(settings.getName()));
        ProgramRunnerUtil.executeConfiguration(
            settings,
            debug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance()
        );
    }

    private void markStarting(List<String> configurationNames) {
        boolean changed = false;
        for (String configurationName : configurationNames) {
            if (pendingStarts.add(configurationName)) {
                changed = true;
                refreshCardsForConfiguration(configurationName);
            }
        }
        if (changed) {
            synchronizeStartingAnimation();
            refreshLaunchActionState();
        }
    }

    private void clearStarting(String configurationName) {
        if (pendingStarts.remove(configurationName)) {
            refreshCardsForConfiguration(configurationName);
            synchronizeStartingAnimation();
            refreshLaunchActionState();
        }
    }

    private void synchronizeStartingAnimation() {
        if (pendingStarts.isEmpty()) {
            startingAnimationTimer.stop();
            startingAnimationOffset = 0;
            lastStartingAnimationFrameNanos = 0;
        } else if (!startingAnimationTimer.isRunning()) {
            lastStartingAnimationFrameNanos = System.nanoTime();
            startingAnimationTimer.start();
        }
    }

    private void advanceStartingAnimation() {
        long now = System.nanoTime();
        float elapsedSeconds = (now - lastStartingAnimationFrameNanos) / 1_000_000_000f;
        lastStartingAnimationFrameNanos = now;
        startingAnimationOffset += elapsedSeconds * STARTING_ANIMATION_SPEED_PX_PER_SECOND;
        for (String configurationName : pendingStarts) {
            ServiceCard card = cardsByConfiguration.get(configurationName);
            if (card != null) {
                card.repaint();
            }
        }
    }

    private void editAppearances() {
        List<ServiceLauncherSettings.ServiceAppearance> current = appearances();
        ServiceLauncherSettingsDialog dialog = new ServiceLauncherSettingsDialog(project, current);
        if (dialog.showAndGet()) {
            ServiceLauncherSettings.getInstance(project).replaceWith(dialog.result());
            rebuild();
        }
    }

    private void editAppearance(ServiceLauncherSettings.ServiceAppearance source) {
        List<ServiceLauncherSettings.ServiceAppearance> current = appearances();
        Set<String> groups = new LinkedHashSet<>();
        current.forEach(item -> groups.add(ServiceLauncherSettings.normalizeGroup(item.group)));
        int maxOrder = current.size();
        ServiceAppearanceDialog dialog = new ServiceAppearanceDialog(
            project, source, groups, availableByName.keySet(), maxOrder
        );
        if (dialog.showAndGet()) {
            applyEditedAppearance(current, dialog.result());
            ServiceLauncherSettings.getInstance(project).replaceWith(current);
            rebuild();
        }
    }

    private void applyEditedAppearance(List<ServiceLauncherSettings.ServiceAppearance> current,
                                       ServiceLauncherSettings.ServiceAppearance edited) {
        if (edited.configurationName != null && !edited.configurationName.isBlank()) {
            for (ServiceLauncherSettings.ServiceAppearance item : current) {
                if (!item.itemId.equals(edited.itemId)
                    && edited.configurationName.equals(item.configurationName)) {
                    item.expectedConfigurationName = item.configurationName;
                    item.configurationName = "";
                }
            }
        }
        current.removeIf(item -> item.itemId.equals(edited.itemId));
        String targetGroup = ServiceLauncherSettings.normalizeGroup(edited.group);
        int insertionIndex = current.size();
        int seenInGroup = 0;
        for (int i = 0; i < current.size(); i++) {
            ServiceLauncherSettings.ServiceAppearance item = current.get(i);
            if (targetGroup.equals(ServiceLauncherSettings.normalizeGroup(item.group))) {
                if (seenInGroup == edited.order) {
                    insertionIndex = i;
                    break;
                }
                insertionIndex = i + 1;
                seenInGroup++;
            }
        }
        current.add(insertionIndex, edited);
        ServiceLauncherSettings.normalizeOrders(current);
    }

    private void moveWithinGroup(ServiceLauncherSettings.ServiceAppearance source, int delta) {
        List<ServiceLauncherSettings.ServiceAppearance> current = appearances();
        List<ServiceLauncherSettings.ServiceAppearance> group = current.stream()
            .filter(item -> ServiceLauncherSettings.normalizeGroup(item.group)
                .equals(ServiceLauncherSettings.normalizeGroup(source.group)))
            .sorted(Comparator.comparingInt(item -> item.order))
            .toList();
        int index = -1;
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i).itemId.equals(source.itemId)) {
                index = i;
                break;
            }
        }
        int target = index + delta;
        if (index < 0 || target < 0 || target >= group.size()) {
            return;
        }
        int oldOrder = group.get(index).order;
        group.get(index).order = group.get(target).order;
        group.get(target).order = oldOrder;
        ServiceLauncherSettings.normalizeOrders(current);
        rebuild();
    }

    private void moveToGroup(ServiceLauncherSettings.ServiceAppearance source, String group) {
        ServiceLauncherSettings.ServiceAppearance edited = source.copy();
        edited.group = group;
        List<ServiceLauncherSettings.ServiceAppearance> current = appearances();
        edited.order = (int) current.stream()
            .filter(item -> ServiceLauncherSettings.normalizeGroup(item.group).equals(group)).count();
        applyEditedAppearance(current, edited);
        ServiceLauncherSettings.getInstance(project).replaceWith(current);
        rebuild();
    }

    private void renameGroup(String sourceGroup) {
        String requested = Messages.showInputDialog(
            project,
            "Enter a new name for the group:",
            "Rename Group",
            Messages.getQuestionIcon(),
            sourceGroup,
            null
        );
        if (requested == null) {
            return;
        }
        String targetGroup = requested.trim();
        if (targetGroup.isEmpty()) {
            Messages.showErrorDialog(project, "The group name cannot be empty.", "Rename Group");
            return;
        }
        if (sourceGroup.equals(targetGroup)) {
            return;
        }
        boolean duplicate = groupNames().stream()
            .anyMatch(group -> !group.equals(sourceGroup) && group.equalsIgnoreCase(targetGroup));
        if (duplicate) {
            Messages.showErrorDialog(project, "A group with that name already exists.", "Rename Group");
            return;
        }
        List<ServiceLauncherSettings.ServiceAppearance> current = appearances();
        current.stream()
            .filter(item -> sourceGroup.equals(ServiceLauncherSettings.normalizeGroup(item.group)))
            .forEach(item -> item.group = targetGroup);
        ServiceLauncherSettings.getInstance(project).replaceWith(current);
        rebuild();
    }

    private void moveGroup(String sourceGroup, int delta) {
        Map<String, List<ServiceLauncherSettings.ServiceAppearance>> grouped = new LinkedHashMap<>();
        appearances().forEach(item -> grouped
            .computeIfAbsent(ServiceLauncherSettings.normalizeGroup(item.group), ignored -> new ArrayList<>())
            .add(item));
        List<String> groups = new ArrayList<>(grouped.keySet());
        int sourceIndex = groups.indexOf(sourceGroup);
        int targetIndex = sourceIndex + delta;
        if (sourceIndex < 0 || targetIndex < 0 || targetIndex >= groups.size()) {
            return;
        }
        Collections.swap(groups, sourceIndex, targetIndex);
        List<ServiceLauncherSettings.ServiceAppearance> reordered = new ArrayList<>();
        groups.forEach(group -> reordered.addAll(grouped.get(group)));
        ServiceLauncherSettings.getInstance(project).replaceWith(reordered);
        rebuild();
    }

    private List<String> groupNames() {
        return appearances().stream()
            .map(item -> ServiceLauncherSettings.normalizeGroup(item.group))
            .distinct()
            .toList();
    }

    private void installGroupPopup(JComponent component, String group) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showGroupPopup(event, group);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showGroupPopup(event, group);
            }
        });
    }

    private void showGroupPopup(MouseEvent event, String group) {
        if (!event.isPopupTrigger()) {
            return;
        }
        List<String> groups = groupNames();
        int groupIndex = groups.indexOf(group);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("Rename group…", AllIcons.Actions.Edit);
        rename.addActionListener(e -> renameGroup(group));
        JMenuItem moveUp = new JMenuItem("Move group up");
        moveUp.setEnabled(groupIndex > 0);
        moveUp.addActionListener(e -> moveGroup(group, -1));
        JMenuItem moveDown = new JMenuItem("Move group down");
        moveDown.setEnabled(groupIndex >= 0 && groupIndex < groups.size() - 1);
        moveDown.addActionListener(e -> moveGroup(group, 1));
        menu.add(rename);
        menu.add(moveUp);
        menu.add(moveDown);
        menu.addSeparator();
        JMenuItem selectAllItem = new JMenuItem("Select all visible services");
        selectAllItem.addActionListener(e -> selectAll());
        JMenuItem selectNoneItem = new JMenuItem("Clear selection");
        selectNoneItem.addActionListener(e -> selectNone());
        menu.add(selectAllItem);
        menu.add(selectNoneItem);
        menu.show((Component) event.getSource(), event.getX(), event.getY());
    }

    private void sessionStarted(String executorId, ExecutionEnvironment environment, ProcessHandler handler) {
        RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
        if (settings == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            String name = settings.getName();
            clearStarting(name);
            if (!cardsByConfiguration.containsKey(name)) {
                refreshLaunchActionState();
                return;
            }
            boolean debug = DefaultDebugExecutor.getDebugExecutorInstance().getId().equals(executorId);
            List<RunningSession> sessions = runningSessions.computeIfAbsent(name, ignored -> new ArrayList<>());
            if (sessions.stream().noneMatch(session -> session.handler == handler)) {
                sessions.add(new RunningSession(handler, debug));
            }
            refreshCardsForConfiguration(name);
            refreshAggregateActionState();
        });
    }

    private void sessionNotStarted(ExecutionEnvironment environment) {
        RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
        if (settings == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            clearStarting(settings.getName());
        });
    }

    private void sessionTerminated(ExecutionEnvironment environment, ProcessHandler handler) {
        RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
        if (settings == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            String name = settings.getName();
            List<RunningSession> sessions = runningSessions.get(name);
            if (sessions != null) {
                sessions.removeIf(session -> session.handler == handler);
                if (sessions.isEmpty()) {
                    runningSessions.remove(name);
                }
            }
            refreshCardsForConfiguration(name);
            refreshAggregateActionState();
            if (!isRunning(name) && pendingRestarts.containsKey(name)) {
                boolean debug = pendingRestarts.remove(name);
                RunnerAndConfigurationSettings configuration = availableByName.get(name);
                if (configuration != null) {
                    launch(configuration, debug);
                }
            }
        });
    }

    private void rebuildLater() {
        if (!rebuildScheduled.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            rebuildScheduled.set(false);
            if (!project.isDisposed()) {
                rebuild();
            }
        });
    }

    private void refreshCardsForConfiguration(String configurationName) {
        ServiceCard card = cardsByConfiguration.get(configurationName);
        if (card != null) {
            card.refreshState();
        }
    }

    private void refreshAggregateActionState() {
        boolean anyRunning = !runningSessions.isEmpty();
        if (restartAllButton != null && restartAllButton.isEnabled() != anyRunning) {
            restartAllButton.setEnabled(anyRunning);
        }
        if (stopAllButton != null && stopAllButton.isEnabled() != anyRunning) {
            stopAllButton.setEnabled(anyRunning);
        }
        refreshLaunchActionState();
    }

    private void refreshLaunchActionState() {
        if (runButton == null || debugButton == null) {
            return;
        }
        boolean hasPendingService = false;
        boolean hasStartableService = false;
        for (String itemId : selected) {
            ServiceCard card = cards.get(itemId);
            if (card == null || card.settings == null
                || card.appearance.configurationName == null || card.appearance.configurationName.isBlank()) {
                continue;
            }
            String configurationName = card.appearance.configurationName;
            boolean pending = pendingStarts.contains(configurationName);
            hasPendingService |= pending;
            hasStartableService |= !pending && !isRunning(configurationName);
            if (hasPendingService && hasStartableService) {
                break;
            }
        }
        boolean showStarting = hasPendingService && !hasStartableService;
        updateStartButton(runButton, AllIcons.Actions.Execute, showStarting, hasStartableService,
            "Run selected services that are not already running");
        updateStartButton(debugButton, AllIcons.Actions.StartDebugger, showStarting, hasStartableService,
            "Debug selected services that are not already running");
    }

    private void updateStartButton(JButton button, Icon normalIcon, boolean starting, boolean canStart,
                                   String normalTooltip) {
        boolean wasStarting = Boolean.TRUE.equals(button.getClientProperty("serviceLauncher.starting"));
        if (wasStarting != starting) {
            button.putClientProperty("serviceLauncher.starting", starting);
            button.setIcon(starting ? startingIcon : normalIcon);
            button.setDisabledIcon(starting ? startingIcon : IconLoader.getDisabledIcon(normalIcon));
        }
        String tooltip = starting ? "Starting selected services…" : normalTooltip;
        if (!tooltip.equals(button.getToolTipText())) {
            button.setToolTipText(tooltip);
        }
        boolean enabled = !starting && canStart;
        if (button.isEnabled() != enabled) {
            button.setEnabled(enabled);
        }
    }

    @Override
    public void dispose() {
        startingAnimationTimer.stop();
    }

    private final class ResponsiveGroupPanel extends JPanel {
        private final JPanel cardGrid = new JPanel();
        private int columns = 0;

        private ResponsiveGroupPanel(String title, List<ServiceLauncherSettings.ServiceAppearance> items,
                                     Map<String, RunnerAndConfigurationSettings> available) {
            super(new BorderLayout(0, 6));
            setOpaque(false);
            installGroupPopup(this, title);
            JBLabel heading = new JBLabel(title, SwingConstants.CENTER);
            heading.setFont(heading.getFont().deriveFont(Font.PLAIN, heading.getFont().getSize2D() + 2));
            heading.setToolTipText("Right-click to rename or reorder this group");
            installGroupPopup(heading, title);
            add(heading, BorderLayout.NORTH);
            cardGrid.setOpaque(false);
            installSelectionPopup(cardGrid);
            for (ServiceLauncherSettings.ServiceAppearance appearance : items) {
                ServiceCard card = new ServiceCard(appearance, available.get(appearance.configurationName));
                cards.put(appearance.itemId, card);
                if (appearance.configurationName != null && !appearance.configurationName.isBlank()) {
                    cardsByConfiguration.put(appearance.configurationName, card);
                }
                cardGrid.add(card);
            }
            add(cardGrid, BorderLayout.CENTER);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    updateColumns();
                }
            });
            SwingUtilities.invokeLater(this::updateColumns);
        }

        private void updateColumns() {
            int usableWidth = Math.max(MIN_CARD_WIDTH, getWidth());
            int desired = Math.max(1, (usableWidth + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP));
            if (desired != columns) {
                columns = desired;
                cardGrid.setLayout(new GridLayout(0, columns, CARD_GAP, CARD_GAP));
                revalidate();
            }
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private final class ServiceCard extends JLayeredPane {
        private final ServiceLauncherSettings.ServiceAppearance appearance;
        private final RunnerAndConfigurationSettings settings;
        private final JPanel content = new JPanel(new BorderLayout(5, 0));
        private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
        private final JBLabel linkWarning = new JBLabel(AllIcons.General.Warning);
        private final JBLabel serviceIcon;
        private final JBLabel nameLabel;
        private final Color normalTextColor;

        private ServiceCard(ServiceLauncherSettings.ServiceAppearance appearance,
                            RunnerAndConfigurationSettings settings) {
            this.appearance = appearance;
            this.settings = settings;
            setOpaque(true);
            setBackground(CARD_BACKGROUND);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(MIN_CARD_WIDTH, 48));
            setMinimumSize(new Dimension(140, 44));

            Icon appearanceIcon = LauncherIcons.get(appearance);
            serviceIcon = new JBLabel(appearanceIcon);
            serviceIcon.setDisabledIcon(IconLoader.getDisabledIcon(appearanceIcon));
            serviceIcon.setVerticalAlignment(SwingConstants.CENTER);
            serviceIcon.setBorder(BorderFactory.createEmptyBorder(0, 9, 0, 0));
            content.add(serviceIcon, BorderLayout.WEST);

            String linkedOrExpectedName = appearance.configurationName == null || appearance.configurationName.isBlank()
                ? appearance.expectedConfigurationName
                : appearance.configurationName;
            String cardName = appearance.displayName == null || appearance.displayName.isBlank()
                ? (linkedOrExpectedName == null || linkedOrExpectedName.isBlank() ? "Unlinked service" : linkedOrExpectedName)
                : appearance.displayName;
            nameLabel = new JBLabel(toHtml(cardName), SwingConstants.CENTER);
            nameLabel.setVerticalAlignment(SwingConstants.CENTER);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, nameLabel.getFont().getSize2D()));
            nameLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 5));
            normalTextColor = nameLabel.getForeground();
            content.add(nameLabel, BorderLayout.CENTER);
            content.setOpaque(false);
            add(content, JLayeredPane.DEFAULT_LAYER);

            actions.setOpaque(false);
            JButton restart = compactButton(AllIcons.Actions.Restart, "Restart " + cardName);
            restart.addActionListener(e -> restartService(appearance.configurationName));
            JButton stop = compactButton(AllIcons.Actions.StopRefresh, "Stop " + cardName);
            stop.addActionListener(e -> stopService(appearance.configurationName));
            actions.add(restart);
            actions.add(stop);
            add(actions, JLayeredPane.PALETTE_LAYER);
            String expectedName = appearance.expectedConfigurationName == null
                ? ""
                : appearance.expectedConfigurationName;
            linkWarning.setToolTipText(expectedName.isBlank()
                ? "This item is not linked to a run configuration. Right-click to connect it."
                : "Run configuration '" + expectedName + "' was not found. Right-click to reconnect it.");
            linkWarning.setVisible(settings == null);
            add(linkWarning, JLayeredPane.PALETTE_LAYER);

            MouseAdapter listener = new MouseAdapter() {
                private boolean selectionArmed;

                @Override
                public void mousePressed(MouseEvent event) {
                    maybeShowPopup(event);
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        selectionArmed = true;
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    maybeShowPopup(event);
                    if (selectionArmed && SwingUtilities.isLeftMouseButton(event)) {
                        selectionArmed = false;
                        Point releasePoint = SwingUtilities.convertPoint(
                            event.getComponent(), event.getPoint(), ServiceCard.this
                        );
                        if (contains(releasePoint)) {
                            toggleSelection();
                        }
                    } else {
                        selectionArmed = false;
                    }
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    Point pointer = SwingUtilities.convertPoint(
                        event.getComponent(), event.getPoint(), ServiceCard.this
                    );
                    if (!contains(pointer) && (event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0) {
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

        @Override
        public void doLayout() {
            content.setBounds(0, 0, getWidth(), getHeight());
            Dimension actionSize = actions.getPreferredSize();
            actions.setBounds(Math.max(2, getWidth() - actionSize.width - 2), 2, actionSize.width, actionSize.height);
            Dimension warningSize = linkWarning.getPreferredSize();
            linkWarning.setBounds(2, 2, warningSize.width, warningSize.height);
        }

        private void maybeShowPopup(MouseEvent event) {
            if (!event.isPopupTrigger()) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem edit = new JMenuItem("Edit appearance…", AllIcons.Actions.Edit);
            edit.addActionListener(e -> editAppearance(appearance));
            menu.add(edit);
            menu.addSeparator();

            JMenuItem moveUp = new JMenuItem("Move up");
            moveUp.addActionListener(e -> moveWithinGroup(appearance, -1));
            JMenuItem moveDown = new JMenuItem("Move down");
            moveDown.addActionListener(e -> moveWithinGroup(appearance, 1));
            menu.add(moveUp);
            menu.add(moveDown);

            JMenu groups = new JMenu("Move to group");
            appearances().stream().map(item -> ServiceLauncherSettings.normalizeGroup(item.group)).distinct()
                .filter(group -> !group.equals(ServiceLauncherSettings.normalizeGroup(appearance.group)))
                .forEach(group -> {
                    JMenuItem target = new JMenuItem(group);
                    target.addActionListener(e -> moveToGroup(appearance, group));
                    groups.add(target);
                });
            menu.add(groups);
            menu.addSeparator();
            JMenuItem selection = new JMenuItem(selected.contains(appearance.itemId) ? "Deselect" : "Select");
            selection.addActionListener(e -> toggleSelection());
            menu.add(selection);
            menu.show((Component) event.getSource(), event.getX(), event.getY());
        }

        private void toggleSelection() {
            if (!selected.remove(appearance.itemId)) {
                selected.add(appearance.itemId);
            }
            refreshState();
            refreshLaunchActionState();
        }

        private JButton compactButton(Icon icon, String tooltip) {
            return new HoverIconButton(LauncherIcons.scaleIcon(icon, 10), tooltip, 15, 15);
        }

        private void refreshBorder() {
            refreshState();
        }

        private void refreshState() {
            boolean selectedNow = selected.contains(appearance.itemId);
            boolean runningNow = isRunning(appearance.configurationName);
            boolean startingNow = pendingStarts.contains(appearance.configurationName);
            if (startingNow) {
                setBorder(startingBorder);
            } else {
                Color borderColor = runningNow
                    ? RUNNING_ACCENT
                    : (selectedNow ? MUTED_BORDER : UNSELECTED_BORDER);
                setBorder(BorderFactory.createLineBorder(borderColor, runningNow ? RUNNING_BORDER_WIDTH : 1));
            }
            setBackground(selectedNow ? CARD_BACKGROUND : UNSELECTED_CARD_BACKGROUND);
            serviceIcon.setEnabled(selectedNow);
            nameLabel.setForeground(selectedNow ? normalTextColor : UNSELECTED_TEXT);
            actions.setVisible(runningNow);
            repaint();
        }

        private String toHtml(String value) {
            String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            return "<html><div style='text-align:center'>" + escaped.replace("\n", "<br>") + "</div></html>";
        }
    }

    private final class StartingBorder implements Border {
        private static final int WIDTH = 2;

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            if (width <= WIDTH * 2 || height <= WIDTH * 2) {
                return;
            }
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float perimeter = Math.max(1, 2f * ((width - WIDTH * 2f) + (height - WIDTH * 2f)));
                float segmentLength = perimeter * 0.20f;
                float gapLength = perimeter * 0.30f;
                graphics2D.setStroke(new BasicStroke(
                    WIDTH,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    10,
                    new float[]{segmentLength, gapLength},
                    startingAnimationOffset % (segmentLength + gapLength)
                ));
                graphics2D.setColor(RUNNING_ACCENT);
                graphics2D.drawRoundRect(
                    x + 1,
                    y + 1,
                    width - 3,
                    height - 3,
                    6,
                    6
                );
            } finally {
                graphics2D.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(WIDTH, WIDTH, WIDTH, WIDTH);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    private record RunningSession(ProcessHandler handler, boolean debug) {
    }

    private static final class HoverIconButton extends JButton {
        private HoverIconButton(Icon icon, String tooltip, int width, int height) {
            super(icon);
            setDisabledIcon(IconLoader.getDisabledIcon(icon));
            setToolTipText(tooltip);
            setFocusable(false);
            setRolloverEnabled(true);
            setContentAreaFilled(false);
            setBorder(BorderFactory.createEmptyBorder());
            setOpaque(false);
            setPreferredSize(new Dimension(width, height));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            ButtonModel model = getModel();
            if (isEnabled() && (model.isPressed() || model.isRollover())) {
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                try {
                    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics2D.setColor(model.isPressed() ? PRESSED_BACKGROUND : HOVER_BACKGROUND);
                    graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                } finally {
                    graphics2D.dispose();
                }
            }
            super.paintComponent(graphics);
        }
    }

    private static final class VerticalScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(18, visibleRect.height - 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
