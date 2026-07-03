package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import java.util.Scanner;

@CommandDefinition(name="add", description = "Create a new folder for organizing uploaded data and computation nodes", generateHelp = true)
public class AddFolder implements Command<H5mCommandInvocation> {

    @Inject
    FolderServiceInterface folderService;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Argument(description = "folder name")
    public String name;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if(name == null){
            Scanner sc = new Scanner(System.in);
            invocation.print("Enter name: ");
            name = sc.nextLine();
        }
        NodeGroup existingGroup =  nodeGroupService.byName(name);
        if(existingGroup != null){
            invocation.println(name+" conflicts with an existing node group");
            return CommandResult.FAILURE;
        }
        folderService.create(name);
        return CommandResult.SUCCESS;
    }
}
