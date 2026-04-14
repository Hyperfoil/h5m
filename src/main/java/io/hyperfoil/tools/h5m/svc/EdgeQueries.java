package io.hyperfoil.tools.h5m.svc;

import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared edge table queries for closure tables (node_edge, value_edge).
 */
class EdgeQueries {

    static long getParentCount(EntityManager em, String edgeTable, Long childId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + edgeTable + " WHERE child_id = :childId"
        ).setParameter("childId", childId).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    static Map<Long, Long> getParentCounts(EntityManager em, String edgeTable, List<Long> childIds) {
        if (childIds.isEmpty()) return Map.of();
        List<Object[]> rows = em.createNativeQuery(
                "SELECT child_id, COUNT(*) FROM " + edgeTable + " WHERE child_id IN (:childIds) GROUP BY child_id"
        ).setParameter("childIds", childIds).getResultList();
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return result;
    }

    static void deleteParentEdges(EntityManager em, String edgeTable, Long parentId) {
        em.createNativeQuery("DELETE FROM " + edgeTable + " WHERE parent_id = :parentId")
                .setParameter("parentId", parentId)
                .executeUpdate();
    }

    static void deleteChildEdges(EntityManager em, String edgeTable, Long childId) {
        em.createNativeQuery("DELETE FROM " + edgeTable + " WHERE child_id = :childId")
                .setParameter("childId", childId)
                .executeUpdate();
    }
}
