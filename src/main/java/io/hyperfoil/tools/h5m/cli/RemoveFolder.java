package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name="folder",description = "remove a folder", mixinStandardHelpOptions = true)
public class RemoveFolder implements Callable<Integer> {

    @Inject
    FolderServiceInterface folderService;

    @CommandLine.Parameters
    String name;

    @Override
    public Integer call() throws Exception {
        if(folderService.delete(name) == 0){
            System.err.println("FolderEntity "+name+" not found");
        }
        return 0;
    }
}
