package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationProvider;

public class H5mCommandInvocationProvider implements CommandInvocationProvider<H5mCommandInvocation> {

    private final FolderContext folderContext;

    public H5mCommandInvocationProvider(FolderContext folderContext) {
        this.folderContext = folderContext;
    }

    @Override
    public H5mCommandInvocation enhanceCommandInvocation(CommandInvocation commandInvocation) {
        return new H5mCommandInvocation(commandInvocation, folderContext);
    }
}
