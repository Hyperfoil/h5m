package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.svc.FolderService;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "export", separator = " ",
    description = "export a folder's node graph to a JSON file",
    mixinStandardHelpOptions = true)
public class ExportFolder implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "1", description = "folder name to export")
    String folderName;

    @CommandLine.Option(names = {"to"}, description = "output JSON file path (default: <folderName>.json)")
    String outputPath;

    @Inject
    FolderService folderService;

    @Override
    public Integer call() throws Exception {
        Path path = outputPath != null
            ? Path.of(outputPath)
            : Path.of(folderName + ".json");

        try {
            folderService.export(folderName, path);
            System.out.println("Exported folder '" + folderName + "' to " + path);
            return 0;
        } catch (Exception e) {
            System.err.println("Export failed: " + e.getMessage());
            return 1;
        }
    }
}
