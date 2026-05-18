package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "import", separator = " ",
    description = "import a folder's node graph from a JSON file",
    mixinStandardHelpOptions = true)
public class ImportFolder implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "1", description = "path to the folder JSON file to import")
    String inputPath;

    @CommandLine.Option(names = {"--overwrite"}, description = "delete and recreate the folder if it already exists")
    boolean overwrite = false;

    @Inject
    FolderServiceInterface folderService;

    @Override
    public Integer call() throws Exception {
        Path path = Path.of(inputPath);

        if (!path.toFile().exists()) {
            System.err.println("File not found: " + inputPath);
            return 1;
        }

        try {
            String folderName = folderService.importFolder(path, overwrite);
            System.out.println("Imported folder '" + folderName + "' from " + path);
            return 0;
        } catch (Exception e) {
            System.err.println("Import failed: " + e.getMessage());
            return 1;
        }
    }
}
