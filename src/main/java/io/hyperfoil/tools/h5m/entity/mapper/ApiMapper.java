package io.hyperfoil.tools.h5m.entity.mapper;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.NodeGroupEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface ApiMapper {

    Folder toFolder(FolderEntity folder);

    Node toNode(NodeEntity node);

    NodeGroup toNodeGroup(NodeGroupEntity nodeGroup);

    Value toValue(ValueEntity value);

}
