package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collection;

final class ServiceAppearanceDialog extends DialogWrapper {
    private static final long MAX_ICON_BYTES = 5 * 1024 * 1024;
    private static final String NOT_LINKED = "<Not linked>";

    private final Project project;
    private final ServiceLauncherSettings.ServiceAppearance edited;
    private final JBTextField displayName = new JBTextField();
    private final JComboBox<String> runConfiguration;
    private final JComboBox<String> group;
    private final JComboBox<String> builtInIcon = new JComboBox<>(LauncherIcons.keys());
    private final JSpinner order;
    private final JBLabel preview = new JBLabel();
    private final JBLabel customIconName = new JBLabel();

    ServiceAppearanceDialog(Project project, ServiceLauncherSettings.ServiceAppearance source,
                            Collection<String> knownGroups, Collection<String> availableConfigurations,
                            int maxOrder) {
        super(project);
        this.project = project;
        this.edited = source.copy();
        DefaultComboBoxModel<String> groupModel = new DefaultComboBoxModel<>();
        knownGroups.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(groupModel::addElement);
        if (groupModel.getIndexOf(ServiceLauncherSettings.normalizeGroup(edited.group)) < 0) {
            groupModel.addElement(ServiceLauncherSettings.normalizeGroup(edited.group));
        }
        group = new JComboBox<>(groupModel);
        group.setEditable(true);
        DefaultComboBoxModel<String> configurationModel = new DefaultComboBoxModel<>();
        configurationModel.addElement(NOT_LINKED);
        availableConfigurations.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(configurationModel::addElement);
        runConfiguration = new JComboBox<>(configurationModel);
        runConfiguration.setSelectedItem(
            edited.configurationName == null || edited.configurationName.isBlank()
                ? NOT_LINKED
                : edited.configurationName
        );
        order = new JSpinner(new SpinnerNumberModel(Math.max(0, edited.order + 1), 1, Math.max(1, maxOrder + 1), 1));
        String titleName = source.displayName == null || source.displayName.isBlank()
            ? (source.configurationName == null || source.configurationName.isBlank()
                ? source.expectedConfigurationName
                : source.configurationName)
            : source.displayName;
        setTitle("Edit " + (titleName == null || titleName.isBlank() ? "unlinked service" : titleName));
        setOKButtonText("Apply");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        displayName.setText(edited.displayName);
        group.setSelectedItem(ServiceLauncherSettings.normalizeGroup(edited.group));
        builtInIcon.setSelectedItem(edited.icon);
        builtInIcon.addActionListener(e -> {
            edited.icon = String.valueOf(builtInIcon.getSelectedItem());
            edited.customIconName = "";
            edited.customIconData = "";
            refreshPreview();
        });

        JButton chooseIcon = new JButton("Choose image…");
        chooseIcon.addActionListener(e -> chooseCustomIcon());
        JButton clearIcon = new JButton("Use built-in");
        clearIcon.addActionListener(e -> {
            edited.customIconName = "";
            edited.customIconData = "";
            refreshPreview();
        });

        JPanel iconActions = new JPanel();
        iconActions.add(builtInIcon);
        iconActions.add(chooseIcon);
        iconActions.add(clearIcon);

        JPanel previewPanel = new JPanel(new BorderLayout(8, 0));
        previewPanel.add(preview, BorderLayout.WEST);
        previewPanel.add(customIconName, BorderLayout.CENTER);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        addRow(panel, 0, "Run configuration", runConfiguration);
        addRow(panel, 1, "Display name", displayName);
        addRow(panel, 2, "Group", group);
        addRow(panel, 3, "Order in group", order);
        addRow(panel, 4, "Icon", iconActions);
        addRow(panel, 5, "Preview", previewPanel);
        panel.setPreferredSize(new Dimension(620, 260));
        refreshPreview();
        return panel;
    }

    private void addRow(JPanel panel, int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(6, 4, 6, 12);
        panel.add(new JBLabel(label), labelConstraints);

        GridBagConstraints componentConstraints = new GridBagConstraints();
        componentConstraints.gridx = 1;
        componentConstraints.gridy = row;
        componentConstraints.weightx = 1;
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.insets = new Insets(6, 4, 6, 4);
        panel.add(component, componentConstraints);
    }

    private void chooseCustomIcon() {
        JFileChooser chooser = new JFileChooser(project.getBasePath());
        chooser.setDialogTitle("Choose service icon");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Images (SVG, PNG, JPEG, GIF)", "svg", "png", "jpg", "jpeg", "gif"
        ));
        if (chooser.showOpenDialog(getContentPanel()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length == 0 || bytes.length > MAX_ICON_BYTES) {
                throw new IOException("Icons must be between 1 byte and 5 MB.");
            }
            edited.customIconName = file.getName();
            edited.customIconData = Base64.getEncoder().encodeToString(bytes);
            refreshPreview();
        } catch (IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), "Could Not Load Icon");
        }
    }

    private void refreshPreview() {
        preview.setIcon(LauncherIcons.get(edited));
        customIconName.setText(edited.customIconData == null || edited.customIconData.isBlank()
            ? "Built-in: " + edited.icon
            : edited.customIconName + " (embedded)");
    }

    ServiceLauncherSettings.ServiceAppearance result() {
        String selectedConfiguration = String.valueOf(runConfiguration.getSelectedItem());
        if (NOT_LINKED.equals(selectedConfiguration)) {
            if (edited.configurationName != null && !edited.configurationName.isBlank()) {
                edited.expectedConfigurationName = edited.configurationName;
            }
            edited.configurationName = "";
        } else {
            edited.configurationName = selectedConfiguration;
            edited.expectedConfigurationName = "";
        }
        edited.displayName = displayName.getText().trim();
        edited.group = ServiceLauncherSettings.normalizeGroup(String.valueOf(group.getEditor().getItem()));
        edited.order = Math.max(0, ((Number) order.getValue()).intValue() - 1);
        return edited;
    }
}
