package io.hyperfoil.tools.h5m.cli;

import java.io.File;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.aesh.command.settings.SettingsBuilder;
import org.aesh.readline.alias.AliasManager;
import org.aesh.terminal.utils.Config;

import io.quarkus.aesh.runtime.CliSettings;

@ApplicationScoped
public class H5mCliSettings implements CliSettings {

    @Inject
    FolderContext folderContext;

    @Override
    @SuppressWarnings("unchecked")
    public void customize(SettingsBuilder<?> builder) {
        ((SettingsBuilder) builder).commandInvocationProvider(new H5mCommandInvocationProvider(folderContext));

        // Configure alias support with h5m-specific alias file and built-in aliases
        File aliasFile = new File(Config.getHomeDir() + Config.getPathSeparator() + ".h5m_aliases");
        AliasManager aliasManager = new AliasManager(aliasFile, true);
        // Add built-in aliases (user can override via the alias command)
        aliasManager.addAlias("alias ls='node list'");
        ((SettingsBuilder) builder).aliasManager(aliasManager);
    }
}
