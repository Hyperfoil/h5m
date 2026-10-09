package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.entity.TeamEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(SecurityEnabledProfile.class)
public class AuthorizationServiceTest extends FreshDb {

    @Inject
    AuthorizationService authService;

    @Inject
    TeamService teamService;

    @Inject
    UserService userService;

    @Inject
    FolderService folderService;

    @Test
    @TestSecurity(user = "admin-user", roles = {Role.ADMIN_ROLE})
    void isAdmin_returns_true_for_admin_user() {
        assertTrue(authService.isAdmin());
    }

    @Test
    @TestSecurity(user = "regular", roles = {Role.USER_ROLE})
    void isAdmin_returns_false_for_regular_user() {
        assertFalse(authService.isAdmin());
    }

    @Test
    void isAdmin_returns_false_for_unknown_user() {
        assertFalse(authService.isAdmin());
    }

    @Test
    @TestSecurity(user = "alice", roles = {"H5M.TEAM.1"})
    @Transactional
    void canModifyFolder_returns_true_for_team_member() {
        TeamEntity team = new TeamEntity("dev");
        team.id = 1L;

        FolderEntity folder = new FolderEntity();
        folder.name = "test-folder";
        folder.team = team;
        assertTrue(authService.isMemberOfTeam(1L));
        assertTrue(authService.canModifyFolder(folder));
    }

    @Test
    @TestSecurity(user = "outsider", roles = {Role.USER_ROLE})
    @Transactional
    void canModifyFolder_returns_false_for_non_member() {
        long teamId = teamService.create("dev").id();

        FolderEntity folder = new FolderEntity();
        folder.name = "test-folder";
        folder.team = TeamEntity.findById(teamId);
        folder.persist();

        assertFalse(authService.canModifyFolder(folder));
    }

    @Test
    @TestSecurity(user = "boss", roles = {Role.ADMIN_ROLE})
    @Transactional
    void canModifyFolder_returns_true_for_admin() {
        long teamId = teamService.create("dev").id();

        FolderEntity folder = new FolderEntity();
        folder.name = "test-folder";
        folder.team = TeamEntity.findById(teamId);
        folder.persist();

        assertTrue(authService.canModifyFolder(folder));
    }

    @Test
    @TestSecurity(user = "anyone", roles = {Role.USER_ROLE})
    @Transactional
    void canModifyFolder_returns_true_when_folder_has_no_team() {
        FolderEntity folder = new FolderEntity();
        folder.name = "legacy-folder";
        folder.persist();

        assertTrue(authService.canModifyFolder(folder));
    }

    @Test
    @TestSecurity(user = "outsider", roles = {Role.USER_ROLE})
    @Transactional
    void requireFolderModify_throws_for_non_member() {
        long teamId = teamService.create("dev").id();

        FolderEntity folder = new FolderEntity();
        folder.name = "test-folder";
        folder.team = TeamEntity.findById(teamId);
        folder.persist();

        assertThrows(ForbiddenException.class,
                () -> authService.requireFolderModify(folder));
    }

    @Test
    @TestSecurity(user = "regular", roles = {Role.USER_ROLE})
    void requireAdmin_throws_for_non_admin() {
        assertThrows(ForbiddenException.class,
                () -> authService.requireAdmin());
    }
}
