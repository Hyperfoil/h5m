package io.hyperfoil.tools.h5m.entity.mapper;

import io.hyperfoil.tools.h5m.api.*;
import io.hyperfoil.tools.h5m.entity.ApiKeyEntity;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.NodeGroupEntity;
import io.hyperfoil.tools.h5m.entity.NotificationChannelEntity;
import io.hyperfoil.tools.h5m.entity.NotificationEntity;
import io.hyperfoil.tools.h5m.entity.TeamEntity;
import io.hyperfoil.tools.h5m.entity.UserEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.ViewEntity;
import io.hyperfoil.tools.h5m.entity.ViewComponentEntity;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface ApiMapper {

    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "teamId" ,  source = "team.id")
    @Mapping(target = "teamName", source = "team.name")
    Folder toFolder(FolderEntity folder);

    @Mapping(target = "type", expression = "java(node.type())")
    @Mapping(target = "groupId", source = "group.id")
    Node toNode(NodeEntity node, @Context CycleAvoidingContext context);

    NodeGroup toNodeGroup(NodeGroupEntity nodeGroup, @Context CycleAvoidingContext context);

    Value toValue(ValueEntity value, @Context CycleAvoidingContext context);

    @Mapping(target = "folderId", source = "folder.id")
    View toView(ViewEntity view);

    @Mapping(target = "nodeId", source = "node.id")
    @Mapping(target = "nodeName", source = "node.name")
    @Mapping(target = "nodeType", expression = "java(component.node != null ? component.node.type().name() : null)")
    ViewComponent toViewComponent(ViewComponentEntity component);

    default ApiKey toApiKey(ApiKeyEntity entity) {
        return toApiKey(entity, null);
    }

    default ApiKey toApiKey(ApiKeyEntity entity, String rawKey) {
        if (entity == null) return null;
        Instant now = Instant.now();
        return new ApiKey(
                entity.id,
                entity.user != null ? entity.user.username : null,
                entity.description,
                entity.createdAt,
                entity.lastUsedAt,
                entity.revoked,
                entity.isExpired(now),
                rawKey
        );
    }

    /**
     * Map a notification channel entity to its API representation.
     * <p>
     * {@code secret} is deliberately ignored — this {@code @Mapping(ignore = true)}
     * is the single rule that guarantees the secret is never returned from the server.
     * {@code config} is read as a typed object through the entity's {@code getConfig()} accessor.
     */
    @Mapping(target = "folderId", source = "folder.id")
    @Mapping(target = "folderName", source = "folder.name")
    @Mapping(target = "secret", ignore = true)
    NotificationChannel toNotificationChannel(NotificationChannelEntity entity);

    /**
     * Map a sent-notification entity to its API/log representation.
     * The channel may have been deleted, so {@code channelId}/{@code channelName} are null-safe.
     */
    @Mapping(target = "folderId", source = "folder.id")
    @Mapping(target = "folderName", source = "folder.name")
    @Mapping(target = "channelId", source = "channel.id")
    @Mapping(target = "channelName", source = "channel.name")
    Notification toNotification(NotificationEntity entity);

    default User toUser(UserEntity entity) {
        if (entity == null) return null;
        Set<Long> teamIds = entity.teams.stream().map(team -> team.id).collect(Collectors.toSet());
        return new User(entity.id, entity.username, entity.role, teamIds);
    }

    default Team toTeam(TeamEntity entity) {
        if (entity == null) return null;
        return new Team(entity.id, entity.name, entity.members != null ? entity.members.size() : 0);
    }
}
