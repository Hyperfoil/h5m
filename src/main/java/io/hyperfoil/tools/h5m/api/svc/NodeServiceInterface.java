package io.hyperfoil.tools.h5m.api.svc;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface NodeServiceInterface {

    Long create(@NotEmpty String name, @NotNull Long groupId, @NotNull NodeType type, String operation);

    Long create(@NotEmpty String name, @NotNull Long groupId, @NotNull NodeType type, List<Long> sources, Object configuration) throws JsonProcessingException;

    void delete(Long modeId);

    List<Node> findNodeByFqdn(String name);

    List<Node> findNodeByFqdn(String name, Long groupId);

}
