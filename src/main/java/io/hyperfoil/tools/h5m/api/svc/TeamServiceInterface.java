package io.hyperfoil.tools.h5m.api.svc;

import io.hyperfoil.tools.h5m.api.Team;

import java.util.List;

public interface TeamServiceInterface {

    long create(String name);

    void delete(long teamId);

    Team find(String name);

    List<Team> list();

    void addMember(long teamId, long userId);

    void removeMember(long teamId, long userId);
}
