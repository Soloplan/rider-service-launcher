package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ServiceLauncherSettingsDialog extends DialogWrapper {
    private final Project project;
    private final AppearanceTableModel model;

    ServiceLauncherSettingsDialog(Project project, List<ServiceLauncherSettings.ServiceAppearance> source) {
        super(project);
        this.project = project;
        model = new AppearanceTableModel(new ArrayList<>(
            source.stream().map(ServiceLauncherSettings.ServiceAppearance::copy).toList()
        ));
        setTitle("Customize Service Launcher");
        setOKButtonText("Apply");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBTable table = new JBTable(model);
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setStriped(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(190);
        table.getColumnModel().getColumn(2).setPreferredWidth(190);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);
        table.getColumnModel().getColumn(5).setPreferredWidth(55);

        JPanel decorated = ToolbarDecorator.createDecorator(table)
            .disableAddAction()
            .disableRemoveAction()
            .setMoveUpAction(button -> move(table, -1))
            .setMoveDownAction(button -> move(table, 1))
            .createPanel();
        decorated.setPreferredSize(new Dimension(900, 430));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton showAll = new JButton("Show all");
        showAll.addActionListener(e -> model.setAllVisible(true));
        JButton hideAll = new JButton("Hide all");
        hideAll.addActionListener(e -> model.setAllVisible(false));
        JButton editSelected = new JButton("Edit selected…");
        editSelected.addActionListener(e -> editSelected(table));
        JButton importButton = new JButton("Import…");
        importButton.addActionListener(e -> importConfiguration());
        JButton exportButton = new JButton("Export…");
        exportButton.addActionListener(e -> exportConfiguration());
        actions.add(showAll);
        actions.add(hideAll);
        actions.add(editSelected);
        actions.add(importButton);
        actions.add(exportButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(actions, BorderLayout.NORTH);
        panel.add(decorated, BorderLayout.CENTER);
        return panel;
    }

    private void editSelected(JBTable table) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            Messages.showInfoMessage(project, "Select a launcher item first.", "Service Launcher");
            return;
        }
        ServiceLauncherSettings.ServiceAppearance source = model.rows.get(selectedRow);
        Set<String> groups = new LinkedHashSet<>();
        model.rows.forEach(item -> groups.add(ServiceLauncherSettings.normalizeGroup(item.group)));
        List<String> configurations = RunManager.getInstance(project).getAllSettings().stream()
            .filter(settings -> !settings.isTemporary())
            .map(RunnerAndConfigurationSettings::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        ServiceAppearanceDialog dialog = new ServiceAppearanceDialog(
            project, source, groups, configurations, model.rows.size()
        );
        if (dialog.showAndGet()) {
            model.replaceItem(dialog.result());
        }
    }

    private void move(JBTable table, int delta) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        String group = ServiceLauncherSettings.normalizeGroup(model.rows.get(selectedRow).group);
        int target = selectedRow + delta;
        while (target >= 0 && target < model.rows.size()
            && !group.equals(ServiceLauncherSettings.normalizeGroup(model.rows.get(target).group))) {
            target += delta;
        }
        if (target < 0 || target >= model.rows.size()) {
            return;
        }
        ServiceLauncherSettings.ServiceAppearance item = model.rows.remove(selectedRow);
        model.rows.add(target, item);
        ServiceLauncherSettings.normalizeOrders(model.rows);
        model.fireTableDataChanged();
        table.getSelectionModel().setSelectionInterval(target, target);
    }

    List<ServiceLauncherSettings.ServiceAppearance> result() {
        ServiceLauncherSettings.normalizeOrders(model.rows);
        return model.rows;
    }

    private void exportConfiguration() {
        JFileChooser chooser = chooser("Export Service Launcher Configuration");
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), "services." + LauncherConfigurationIO.FILE_EXTENSION));
        if (chooser.showSaveDialog(getContentPanel()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            LauncherConfigurationIO.exportTo(ensureExtension(chooser.getSelectedFile()), model.rows);
        } catch (IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), "Could Not Export Configuration");
        }
    }

    private void importConfiguration() {
        JFileChooser chooser = chooser("Import Service Launcher Configuration");
        if (chooser.showOpenDialog(getContentPanel()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            List<ServiceLauncherSettings.ServiceAppearance> imported = LauncherConfigurationIO.importFrom(chooser.getSelectedFile());
            model.replaceWith(LauncherConfigurationIO.merge(model.rows, imported));
        } catch (IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), "Could Not Import Configuration");
        }
    }

    private JFileChooser chooser(String title) {
        JFileChooser chooser = new JFileChooser(project.getBasePath());
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Service Launcher export (*.service-launcher.xml)", "xml"
        ));
        return chooser;
    }

    private File ensureExtension(File file) {
        return file.getName().endsWith("." + LauncherConfigurationIO.FILE_EXTENSION)
            ? file
            : new File(file.getParentFile(), file.getName() + "." + LauncherConfigurationIO.FILE_EXTENSION);
    }

    private static final class AppearanceTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Show", "Run configuration", "Display name", "Group", "Icon", "Order"};
        private final List<ServiceLauncherSettings.ServiceAppearance> rows;

        private AppearanceTableModel(List<ServiceLauncherSettings.ServiceAppearance> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Boolean.class;
                case 5 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex != 1 && columnIndex != 4 && columnIndex != 5;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ServiceLauncherSettings.ServiceAppearance item = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> item.visible;
                case 1 -> {
                    if (item.configurationName != null && !item.configurationName.isBlank()) {
                        yield item.configurationName;
                    }
                    String expected = item.expectedConfigurationName == null ? "" : item.expectedConfigurationName;
                    yield expected.isBlank() ? "⚠ Not linked" : "⚠ Not linked (expected: " + expected + ")";
                }
                case 2 -> item.displayName;
                case 3 -> item.group;
                case 4 -> item.customIconData == null || item.customIconData.isBlank()
                    ? item.icon
                    : "Custom: " + item.customIconName;
                case 5 -> item.order + 1;
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ServiceLauncherSettings.ServiceAppearance item = rows.get(rowIndex);
            switch (columnIndex) {
                case 0 -> item.visible = Boolean.TRUE.equals(value);
                case 2 -> item.displayName = String.valueOf(value).trim();
                case 3 -> item.group = ServiceLauncherSettings.normalizeGroup(String.valueOf(value));
                default -> {
                }
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        private void setAllVisible(boolean visible) {
            rows.forEach(item -> item.visible = visible);
            fireTableDataChanged();
        }

        private void replaceWith(List<ServiceLauncherSettings.ServiceAppearance> replacement) {
            rows.clear();
            rows.addAll(replacement);
            fireTableDataChanged();
        }

        private void replaceItem(ServiceLauncherSettings.ServiceAppearance replacement) {
            if (replacement.configurationName != null && !replacement.configurationName.isBlank()) {
                for (ServiceLauncherSettings.ServiceAppearance item : rows) {
                    if (!item.itemId.equals(replacement.itemId)
                        && replacement.configurationName.equals(item.configurationName)) {
                        item.expectedConfigurationName = item.configurationName;
                        item.configurationName = "";
                    }
                }
            }
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).itemId.equals(replacement.itemId)) {
                    rows.set(i, replacement);
                    break;
                }
            }
            ServiceLauncherSettings.normalizeOrders(rows);
            fireTableDataChanged();
        }
    }
}
