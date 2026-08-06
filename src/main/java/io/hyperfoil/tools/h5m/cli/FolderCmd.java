package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;


@CommandDefinition(
    name = "folder",
    description = "Folder management: create, list, remove, upload, export, import, and more",
    groupCommands = {
        AddFolder.class,
        ListFolder.class,
        RemoveFolder.class,
        UploadCmd.class,
        ExportFolder.class,
        ImportFolder.class,
        StructureCmd.class,
        RecalculateCmd.class,
        PurgeValuesCmd.class,
        ListValue.class,
    },
    generateHelp = true
)
public class FolderCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        invocation.println("Use 'folder <subcommand>'. Try 'folder --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
