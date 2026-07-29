package io.hyperfoil.tools.h5m.api.svc;

import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.api.User;

import java.util.List;

public interface UserServiceInterface {

    long create(String username, Role role);

    User byUsername(String username);

    List<User> list();

    void setRole(long userId, Role role);

    long count();
}
