package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandDefinition(name="list", aliases={"folders"}, description = "List all folders and their upload counts", generateHelp = true)
public class ListFolder implements Command<H5mCommandInvocation> {

    @Inject
    FolderServiceInterface folderService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        Map<String,Integer> folderCounts = folderService.getFolderUploadCount();
        List<String> names = new ArrayList<>(folderCounts.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        invocation.println(ListCmd.table(80,names,List.of("name","uploads"), List.of(Object::toString, folderCounts::get)));
        return CommandResult.SUCCESS;
    }
}
