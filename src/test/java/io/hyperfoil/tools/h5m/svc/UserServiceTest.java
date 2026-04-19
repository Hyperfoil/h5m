package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class UserServiceTest extends FreshDb {

    @Inject
    UserService userService;

    @Test
    void create_user() {
        long id = userService.create("stalep", "admin");
        assertTrue(id > 0);
        User user = userService.byUsername("stalep");
        assertNotNull(user);
        assertEquals("stalep", user.username);
        assertEquals("admin", user.role);
    }

    @Test
    void list_users() {
        userService.create("alice", "user");
        userService.create("bob", "admin");
        List<User> users = userService.list();
        assertEquals(2, users.size());
    }

    @Test
    void set_role() {
        long id = userService.create("carol", "user");
        userService.setRole(id, "admin");
        User user = userService.byUsername("carol");
        assertEquals("admin", user.role);
    }

    @Test
    void count() {
        assertEquals(0, userService.count());
        userService.create("one", "user");
        userService.create("two", "user");
        assertEquals(2, userService.count());
    }

    @Test
    void byUsername_returns_null_for_missing() {
        assertNull(userService.byUsername("nonexistent"));
    }
}
