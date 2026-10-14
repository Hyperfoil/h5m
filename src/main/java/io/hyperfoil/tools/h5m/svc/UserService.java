package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.api.User;
import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import io.hyperfoil.tools.h5m.entity.UserEntity;
import io.hyperfoil.tools.h5m.entity.mapper.ApiMapper;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hibernate.Session;

import java.util.List;
import java.util.function.Supplier;

@ApplicationScoped
public class UserService implements UserServiceInterface {

    @Inject
    ApiMapper apiMapper;

    @Inject
    SecurityIdentity identity;

    @Inject
    Session session;

    @Inject
    WorkService workService;

    @Override
    @Transactional
    public User resolveUser() {
        return apiMapper.toUser(resolveUserEntity());
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private UserEntity resolveUserEntity() {
        return switch (identity.getPrincipal()) {
            case JsonWebToken jwt -> findBySubAndIss(jwt.getSubject(), jwt.getIssuer());
            default -> UserEntity.find("username", identity.getPrincipal().getName()).firstResult();
        };
    }

    private UserEntity findBySubAndIss(String sub, String iss) {
        return session.createQuery(
                        "from h5m_user where sub = :sub and iss = :iss", UserEntity.class)
                .setParameter("sub", sub)
                .setParameter("iss", iss)
                .setCacheable(true)
                .uniqueResult();
    }

    @Override
    public long create(String username, Role role) {
        return createIfAbsent(
                () -> new UserEntity(username, role),
                () -> UserEntity.find("username", username).firstResult());
    }

    @Override
    public long create(String sub, String iss, String username, Role role) {
        return createIfAbsent(
                () -> new UserEntity(sub, iss, username, role),
                () -> findBySubAndIss(sub, iss));
    }

    /** Adopts the existing row if a concurrent provisioning won the race */
    private long createIfAbsent(Supplier<UserEntity> factory, Supplier<UserEntity> lookup) {
        Supplier<Long> createIfAbsent = () -> {
            UserEntity existing = lookup.get();
            if (existing != null) {
                return existing.id;
            }
            UserEntity user = factory.get();
            user.persist();
            return user.id;
        };
        if (QuarkusTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
            // join the caller's transaction: a nested insert would deadlock on SQLite, where the caller already holds the single writer lock
            return createIfAbsent.get();
        }
        try {
            return workService.callInNewTransaction(createIfAbsent::get);
        } catch (RuntimeException e) {
            // the failed insert left its transaction rollback-only
            UserEntity existing = workService.callInNewTransaction(lookup::get);
            if (existing == null) {
                throw e;
            }
            Log.debugf("Adopting concurrently provisioned %s", existing);
            return existing.id;
        }
    }

    @Override
    @Transactional
    public User byUsername(String username) {
        UserEntity entity = UserEntity.find("username", username).firstResult();
        return apiMapper.toUser(entity);
    }

    @Transactional
    public User bySub(String sub, String iss) {
        return apiMapper.toUser(findBySubAndIss(sub, iss));
    }

    @Override
    @Transactional
    public List<User> list() {
        List<UserEntity> entities = UserEntity.listAll();
        return entities.stream().map(apiMapper::toUser).toList();
    }

    @Override
    @Transactional
    public void setRole(long userId, Role role) {
        UserEntity user = UserEntity.findById(userId);
        if (user != null) {
            user.role = role;
        }
    }

    @Override
    @Transactional
    public long count() {
        return UserEntity.count();
    }
}
