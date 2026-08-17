package io.hyperfoil.tools.h5m.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ProcessingServiceInterface;
import io.hyperfoil.tools.h5m.svc.ValueService;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

@CommandDefinition(name = "upload", description = "Upload JSON files to a folder for processing through its computation node graph", generateHelp = true)
public class UploadCmd implements Command<H5mCommandInvocation> {

    private static final long TIMEOUT_MINUTES = 5;

    @Inject
    FolderServiceInterface folderService;

    @Inject
    ProcessingServiceInterface processingService;

    @Inject
    ValueService valueService;

    @Option(name = "async", hasValue = false, acceptNameWithoutDashes = true,
            description = "return immediately without waiting for processing to complete")
    boolean async;

    @Argument(description = "path to JSON file or directory")
    String path;

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target folder name",
            completer = FolderCompleter.class)
    String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required (use --to)");
            return CommandResult.FAILURE;
        }
        if (path == null) {
            invocation.println("path to JSON file or directory is required");
            return CommandResult.FAILURE;
        }
        Folder folder = folderService.find(folderName);
        if (folder == null) {
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }
        File pathFile = new File(path);
        if (!pathFile.exists()) {
            invocation.println("upload path does not exist: " + path);
            return CommandResult.FAILURE;
        }
        List<File> todo = pathFile.isDirectory()
                ? List.of(pathFile.listFiles(s -> s.toString().endsWith(".json") && !s.getName().startsWith(".")))
                : List.of(pathFile);
        List<Long> uploadIds = new ArrayList<>();
        for (File f : todo) {
            if (Thread.interrupted()) throw new InterruptedException("Upload interrupted");
            try {
                JqValue read = JqValues.parse(new String(java.nio.file.Files.readAllBytes(f.toPath())));
                if (read != null) {
                    try {
                        long uploadId = valueService.createRootValue(folder.id(), read);
                        uploadIds.add(uploadId);
                        if (todo.size() > 1) {
                            invocation.println(f.getName() + " -> processing id: " + uploadId);
                        } else {
                            invocation.println("Processing id: " + uploadId);
                        }
                    } catch (NoResultException e) {
                        invocation.println("Folder '" + folderName + "' not found");
                        return CommandResult.FAILURE;
                    }
                } else {
                    invocation.println(f.getPath() + " could not be loaded as json");
                }
            } catch (IOException e) {
                invocation.println("failure trying to read " + f.getPath() + "\n" + e.getMessage());
                return CommandResult.FAILURE;
            }
        }

        if (async) {
            // Async mode — return immediately, user can poll with 'status' command
            return CommandResult.SUCCESS;
        }

        // Synchronous mode — wait for all uploads to complete, then show detection results
        for (long uploadId : uploadIds) {
            if (!processingService.awaitIngestion(uploadId, TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                invocation.println("Upload processing timed out for: " + uploadId);
                return CommandResult.FAILURE;
            }
        }

        // Query for detection results across all uploads
        List<Value> allChanges = new ArrayList<>();
        for (long uploadId : uploadIds) {
            allChanges.addAll(valueService.getDetectionDescendants(uploadId));
        }
        invocation.println("Processing complete. " + ChangeFormatter.formatSummary(allChanges));
        return CommandResult.SUCCESS;
    }
}
