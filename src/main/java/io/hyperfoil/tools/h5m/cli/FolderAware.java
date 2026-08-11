package io.hyperfoil.tools.h5m.cli;

/**
 * Interface for CLI commands that operate within a folder context.
 * Used by completers to resolve the folder name for tab completion
 * without checking each command type individually.
 */
public interface FolderAware {
    String getFolderName();
}
