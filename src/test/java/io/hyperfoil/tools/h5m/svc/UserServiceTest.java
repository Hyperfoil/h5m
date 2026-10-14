package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.api.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class UserServiceTest extends FreshDb {

    @Inject
    UserService userService;

    @Inject
    SessionFactory sessionFactory;

    @Inject
    TeamService teamService;

    @Test
    void create_user() {
        long id = userService.create("stalep", Role.ADMIN);
        assertTrue(id > 0);
        User user = userService.byUsername("stalep");
        assertNotNull(user);
        assertEquals("stalep", user.username());
        assertEquals(Role.ADMIN, user.role());
    }

    @Test
    void list_users() {
        userService.create("alice", Role.USER);
        userService.create("bob", Role.ADMIN);
        List<User> users = userService.list();
        assertEquals(2, users.size());
    }

    @Test
    void set_role() {
        long id = userService.create("carol", Role.USER);
        userService.setRole(id, Role.ADMIN);
        User user = userService.byUsername("carol");
        assertEquals(Role.ADMIN, user.role());
    }

    @Test
    void count() {
        assertEquals(0, userService.count());
        userService.create("one", Role.USER);
        userService.create("two", Role.USER);
        assertEquals(2, userService.count());
    }

    @Test
    void byUsername_returns_null_for_missing() {
        assertNull(userService.byUsername("nonexistent"));
    }

    @Test
    void bySub_second_lookup_is_query_cache_hit() {
        String sub = "sub-123";
        String iss = "https://issuer.example";
        userService.create(sub, iss, "oidc-user", Role.USER);

        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        // First lookup: nothing cached yet -> query-cache miss, result cached.
        User first = userService.bySub(sub, iss);
        assertNotNull(first, "first lookup should resolve the user");
        assertEquals("oidc-user", first.username());

        long hitsAfterFirst = stats.getQueryCacheHitCount();
        long missesAfterFirst = stats.getQueryCacheMissCount();
        assertEquals(0, hitsAfterFirst, "first identical lookup should not be a cache hit");
        assertTrue(missesAfterFirst >= 1,
                "first lookup should register a query-cache miss (setCacheable is active), was " + missesAfterFirst);

        // Second identical lookup: served from the query cache.
        User second = userService.bySub(sub, iss);
        assertNotNull(second, "second lookup should resolve the user");
        assertEquals("oidc-user", second.username());

        assertEquals(hitsAfterFirst + 1, stats.getQueryCacheHitCount(),
                "second identical lookup should be a query-cache hit, not another miss");
    }

    @Test
    void create_by_username_is_idempotent() throws Exception {
        long first = userService.create("alice", Role.USER);
        long second = userService.create("alice", Role.ADMIN);
        assertEquals(first, second, "re-creating an existing user should adopt the existing row");
        assertEquals(1, countRows("h5m_user"));
        assertEquals(Role.USER, userService.byUsername("alice").role(),
                "adopting an existing user must not overwrite the role it was created with");
    }

    @Test
    void create_by_sub_is_idempotent() throws Exception {
        String sub = "sub-idempotent";
        String iss = "https://issuer.example";
        long first = userService.create(sub, iss, "charles", Role.USER);
        long second = userService.create(sub, iss, "charles", Role.USER);
        assertEquals(first, second, "re-creating an existing OIDC user should adopt the existing row");
        assertEquals(1, countRows("h5m_user"));
    }

    /**
     * Creating from inside a caller's transaction that already wrote must join it: a nested
     * transaction would never get SQLite's single writer lock, which the caller still holds.
     */
    @Test
    @Timeout(60)
    @Transactional
    void create_joins_an_active_transaction_that_already_wrote() {
        teamService.create("writes-first");

        long id = userService.create("erin", Role.USER);
        assertTrue(id > 0);
        assertEquals(id, userService.create("erin", Role.USER),
                "a second create in the same transaction should see the row it just inserted");
    }

    /** A username clash between two different identities is a real conflict, not a lost race. */
    @Test
    void create_by_sub_does_not_adopt_a_different_identity_with_the_same_username() throws Exception {
        userService.create("dennis", Role.ADMIN);

        assertThrows(RuntimeException.class,
                () -> userService.create("dennis", "https://issuer.example", "dennis", Role.USER),
                "an OIDC identity must not silently take over an existing account with that username");
        assertEquals(1, countRows("h5m_user"));
    }

    /** Parallel provisioning of the same unknown user must insert one row and throw at no caller. */
    @Test
    @Timeout(60)
    void concurrent_create_by_sub_provisions_once() throws Exception {
        String sub = "alice";
        String iss = "http://localhost:34561";
        int threads = 4;

        List<Long> ids = inParallel(threads, () -> userService.create(sub, iss, "alice", Role.USER));

        assertEquals(1, countRows("h5m_user"), "concurrent provisioning must insert exactly one row");
        assertEquals(1, Set.copyOf(ids).size(), "every caller should get the id of the single row, was " + ids);
    }

    @Test
    @Timeout(60)
    void concurrent_create_by_username_provisions_once() throws Exception {
        int threads = 4;

        List<Long> ids = inParallel(threads, () -> userService.create("bob", Role.USER));

        assertEquals(1, countRows("h5m_user"), "concurrent provisioning must insert exactly one row");
        assertEquals(1, Set.copyOf(ids).size(), "every caller should get the id of the single row, was " + ids);
    }

    /** Runs {@code action} on {@code threads} threads released simultaneously, and returns the results. */
    private <T> List<T> inParallel(int threads, Callable<T> action) throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = IntStream.range(0, threads)
                    .mapToObj(_ -> pool.submit(() -> {
                        startLine.await(10, TimeUnit.SECONDS);
                        return action.call();
                    })).toList();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            // shutdownNow, not close(): close() waits forever on a task that is already stuck
            pool.shutdownNow();
        }
    }
}
