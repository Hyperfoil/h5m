package io.hyperfoil.tools.h5m.cli;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FolderContext {

    private String folderName;

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String name) {
        this.folderName = name;
    }

    public boolean isSet() {
        return folderName != null;
    }

    public void clear() {
        this.folderName = null;
    }
}
