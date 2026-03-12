package io.hyperfoil.tools.h5m.api.svc;

import io.hyperfoil.tools.h5m.api.NodeGroup;

public interface NodeGroupServiceInterface {

    NodeGroup byName(String groupName);

    void delete(Long groupId);

}
