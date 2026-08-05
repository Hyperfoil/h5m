package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.Team;
import io.hyperfoil.tools.h5m.api.svc.TeamServiceInterface;
import io.hyperfoil.tools.h5m.entity.TeamEntity;
import io.hyperfoil.tools.h5m.entity.UserEntity;
import io.hyperfoil.tools.h5m.entity.mapper.ApiMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class TeamService implements TeamServiceInterface {

    @Inject
    ApiMapper apiMapper;

    @Override
    @Transactional
    public long create(String name) {
        TeamEntity team = new TeamEntity(name);
        team.persist();
        return team.id;
    }

    @Override
    @Transactional
    public void delete(long teamId) {
        TeamEntity.deleteById(teamId);
    }

    @Override
    @Transactional
    public Team find(String name) {
        TeamEntity entity = TeamEntity.find("name", name).firstResult();
        return entity != null ? apiMapper.toTeam(entity) : null;
    }

    @Override
    @Transactional
    public List<Team> list() {
        List<TeamEntity> entities = TeamEntity.listAll();
        return entities.stream().map(apiMapper::toTeam).toList();
    }

    @Override
    @Transactional
    public void addMember(long teamId, long userId) {
        TeamEntity team = TeamEntity.findById(teamId);
        UserEntity user = UserEntity.findById(userId);
        if (team != null && user != null && !team.members.contains(user)) {
            team.members.add(user);
        }
    }

    @Override
    @Transactional
    public void removeMember(long teamId, long userId) {
        TeamEntity team = TeamEntity.findById(teamId);
        UserEntity user = UserEntity.findById(userId);
        if (team != null && user != null) {
            team.members.remove(user);
        }
    }
}
