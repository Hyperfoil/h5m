package io.hyperfoil.tools.h5m.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;

import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;


import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;

@CommandDefinition(name = "upload", description = "Upload JSON files to a folder for processing through its computation node graph", generateHelp = true)
public class UploadCmd implements Command<H5mCommandInvocation> {

    @Inject
    FolderServiceInterface folderService;

    @Argument(description = "path to JSON file or directory")
    private String path;

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target folder name")
    private String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        Folder folder = folderService.byName(folderName);
        if (folder == null) {
            invocation.println("could not find folder " + folderName);
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
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (File f : todo) {
            if (Thread.interrupted()) throw new InterruptedException("Upload interrupted");
            try {
                if (todo.size() > 1) {
                    invocation.println(f.getName());
                }
                JqValue read = JqValues.parse(new String(java.nio.file.Files.readAllBytes(f.toPath())));
                if (read != null) {
                    try {
                        futures.add(folderService.upload(folderName, f.getPath(), read));
                    } catch (NoResultException e) {
                        invocation.println("could not find folder " + folderName);
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
        // Wait for all uploads to complete before returning
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .orTimeout(5, TimeUnit.MINUTES)
                        .join();
            } catch (Exception e) {
                invocation.println("Upload processing failed: " + e.getMessage());
                return CommandResult.FAILURE;
            }
        }
        return CommandResult.SUCCESS;
    }
}
