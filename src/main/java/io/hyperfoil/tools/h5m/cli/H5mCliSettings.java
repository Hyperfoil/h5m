package io.hyperfoil.tools.h5m.cli;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.aesh.command.settings.SettingsBuilder;

import io.quarkus.aesh.runtime.CliSettings;

@ApplicationScoped
public class H5mCliSettings implements CliSettings {

    @Inject
    FolderContext folderContext;

    @Override
    @SuppressWarnings("unchecked")
    public void customize(SettingsBuilder<?> builder) {
        ((SettingsBuilder) builder).commandInvocationProvider(new H5mCommandInvocationProvider(folderContext));
    }
}
