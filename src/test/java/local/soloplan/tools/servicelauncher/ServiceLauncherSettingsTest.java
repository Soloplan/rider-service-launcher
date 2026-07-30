package local.soloplan.tools.servicelauncher;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServiceLauncherSettingsTest {
    @Test
    public void emptyGroupsAreStoredAndCanBeReordered() {
        ServiceLauncherSettings settings = new ServiceLauncherSettings();

        assertTrue(settings.addGroup("Backend"));
        assertTrue(settings.addGroup("Frontend"));
        assertTrue(settings.moveGroup("Frontend", -1));

        assertEquals(List.of("Frontend", "Backend"), settings.groupNames());
        assertEquals(List.of("Frontend", "Backend"), settings.getState().groups);
    }

    @Test
    public void groupsAreMigratedFromExistingServices() {
        ServiceLauncherSettings settings = new ServiceLauncherSettings();
        ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
        ServiceLauncherSettings.ServiceAppearance service =
            new ServiceLauncherSettings.ServiceAppearance("Orders API", 0);
        service.group = "Backend";
        state.services.add(service);

        settings.loadState(state);

        assertEquals(List.of("Backend"), settings.groupNames());
    }

    @Test
    public void renamingAGroupAlsoMovesItsServices() {
        ServiceLauncherSettings settings = new ServiceLauncherSettings();
        ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
        ServiceLauncherSettings.ServiceAppearance service =
            new ServiceLauncherSettings.ServiceAppearance("", 0);
        service.group = "Backend";
        service.visible = true;
        state.services.add(service);
        settings.loadState(state);

        assertTrue(settings.renameGroup("Backend", "APIs"));

        assertEquals(List.of("APIs"), settings.groupNames());
        assertEquals("APIs", service.group);
    }

    @Test
    public void standaloneServicesRemainUnlinkedDuringRunConfigurationDiscovery() {
        ServiceLauncherSettings settings = new ServiceLauncherSettings();
        ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
        ServiceLauncherSettings.ServiceAppearance standalone =
            new ServiceLauncherSettings.ServiceAppearance("", 0);
        standalone.displayName = "Orders API";
        standalone.visible = true;
        state.services.add(standalone);
        settings.loadState(state);

        List<ServiceLauncherSettings.ServiceAppearance> services =
            settings.reconciledWith(List.of("Generated Profile"));

        assertEquals(1, services.size());
        assertEquals("", standalone.configurationName);
        assertEquals("", standalone.expectedConfigurationName);
        assertTrue(standalone.visible);
    }

    @Test
    public void groupNamesAreUniqueIgnoringCase() {
        ServiceLauncherSettings settings = new ServiceLauncherSettings();

        assertTrue(settings.addGroup("Backend"));
        assertFalse(settings.addGroup("backend"));

        assertEquals(List.of("Backend"), settings.groupNames());
    }

    @Test
    public void onlyEmptyGroupsCanBeRemoved() {
        ServiceLauncherSettings settings = new ServiceLauncherSettings();
        ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
        state.groups.addAll(List.of("Backend", "Frontend"));
        ServiceLauncherSettings.ServiceAppearance service =
            new ServiceLauncherSettings.ServiceAppearance("Orders API", 0);
        service.group = "Backend";
        state.services.add(service);
        settings.loadState(state);

        assertFalse(settings.removeGroup("Backend"));
        assertTrue(settings.removeGroup("Frontend"));

        assertEquals(List.of("Backend"), settings.groupNames());
    }

    @Test
    public void removedLauncherConfigurationIsNotRecreatedFromItsRunConfiguration() {
        ServiceLauncherSettings settings = new ServiceLauncherSettings();
        ServiceLauncherSettings.StateData state = new ServiceLauncherSettings.StateData();
        ServiceLauncherSettings.ServiceAppearance service =
            new ServiceLauncherSettings.ServiceAppearance("Orders API", 0);
        state.services.add(service);
        settings.loadState(state);

        assertTrue(settings.removeService(service.itemId));
        List<ServiceLauncherSettings.ServiceAppearance> services =
            settings.reconciledWith(List.of("Orders API"));

        assertTrue(services.isEmpty());
    }
}
