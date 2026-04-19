package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import io.hyperfoil.tools.h5m.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class UserService implements UserServiceInterface {

    @Override
    @Transactional
    public long create(String username, String role) {
        User user = new User(username, role);
        user.persist();
        return user.id;
    }

    @Override
    @Transactional
    public User byUsername(String username) {
        return User.find("username", username).firstResult();
    }

    @Override
    @Transactional
    public List<User> list() {
        return User.listAll();
    }

    @Override
    @Transactional
    public void setRole(long userId, String role) {
        User user = User.findById(userId);
        if (user != null) {
            user.role = role;
        }
    }

    @Override
    @Transactional
    public long count() {
        return User.count();
    }
}
