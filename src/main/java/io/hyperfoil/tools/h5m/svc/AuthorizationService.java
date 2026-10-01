package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.hyperfoil.tools.h5m.api.Role.ADMIN_ROLE;

@ApplicationScoped
public class AuthorizationService {

    @ConfigProperty(name = "h5m.security.enabled", defaultValue = "false")
    boolean securityEnabled;

    @Inject
    SecurityIdentity identity;

    public boolean isLocalMode() {
        return !securityEnabled;
    }

    public boolean isAdmin() {
        return isLocalMode() || identity.getRoles().contains(ADMIN_ROLE);
    }

    public boolean isMemberOfTeam(long teamId) {
        return isLocalMode() || identity.getRoles().contains(Role.teamRole(teamId));
    }

    @Transactional
    public boolean canModifyFolder(FolderEntity folder) {
        if (isLocalMode()) {
            return true;
        }
        if (isAdmin()) {
            return true;
        }
        if (folder.team == null) {
            return true; // legacy folder with no team — unrestricted
        }
        return isMemberOfTeam(folder.team.id);
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException("Admin access required");
        }
    }

    public void requireFolderModify(FolderEntity folder) {
        if (!canModifyFolder(folder)) {
            throw new ForbiddenException(
                    "User " + identity.getPrincipal().getName() + " is not a member of team " + folder.team.name);
        }
    }
    
    @Transactional
    public void requireGroupModify(Long groupId) {
        if (isLocalMode() || isAdmin()) {
            return;
        }
        FolderEntity folder = FolderEntity.find("group.id", groupId).firstResult();
        if (folder == null) {
            throw new NotFoundException("No folder found for group: " + groupId);
        }
        requireFolderModify(folder);
    }

    @Transactional
    public void requireFolderModify(long folderId) {
        if (isLocalMode() || isAdmin()) {
            return;
        }
        FolderEntity folder = FolderEntity.findById(folderId);
        if (folder == null) {
            throw new NotFoundException("Folder not found: " + folderId);
        }
        requireFolderModify(folder);
    }

    @Transactional
    public void requireTeamMember(long teamId) {
        if (!isAdmin() && !isMemberOfTeam(teamId)) {
            throw new ForbiddenException("User " + identity.getPrincipal().getName() + " is not a member of team " + teamId);
        }
    }

}
