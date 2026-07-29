package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.api.User;
import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import io.hyperfoil.tools.h5m.entity.UserEntity;
import io.hyperfoil.tools.h5m.entity.mapper.ApiMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class UserService implements UserServiceInterface {

    @Inject
    ApiMapper apiMapper;

    @Override
    @Transactional
    public long create(String username, Role role) {
        UserEntity user = new UserEntity(username, role);
        user.persist();
        return user.id;
    }

    @Override
    @Transactional
    public User byUsername(String username) {
        UserEntity entity = UserEntity.find("username", username).firstResult();
        return apiMapper.toUser(entity);
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
