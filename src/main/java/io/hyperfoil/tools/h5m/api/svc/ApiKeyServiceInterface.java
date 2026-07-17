package io.hyperfoil.tools.h5m.api.svc;

import io.hyperfoil.tools.h5m.entity.ApiKeyEntity;

import java.util.List;

public interface ApiKeyServiceInterface {

    String create(String username, String description);

    List<ApiKeyEntity> listByUser(String username);

    void revoke(long keyId);
}
