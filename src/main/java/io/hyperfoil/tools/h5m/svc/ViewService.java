package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.h5m.api.EphemeralMode;
import io.hyperfoil.tools.h5m.api.ReservedNamespace;
import io.hyperfoil.tools.h5m.api.View;
import io.hyperfoil.tools.h5m.api.ViewComponent;
import io.hyperfoil.tools.h5m.api.svc.ViewServiceInterface;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.ViewComponentEntity;
import io.hyperfoil.tools.h5m.entity.ViewEntity;
import io.hyperfoil.tools.h5m.entity.mapper.ApiMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ViewService implements ViewServiceInterface {

    @Inject
    EntityManager em;

    @Inject
    ApiMapper apiMapper;

    @Inject
    ValueService valueService;

    @Override
    @Transactional
    public List<View> getViews(long folderId) {
        FolderEntity folder = findFolder(folderId);
        return folder.views.stream().map(apiMapper::toView).toList();
    }

    @Override
    @Transactional
    public View getView(Long viewId) {
        ViewEntity view = ViewEntity.findById(viewId);
        if (view == null) {
            throw new NotFoundException("View not found: " + viewId);
        }
        return apiMapper.toView(view);
    }

    @Override
    @Transactional
    public View createView(long folderId, View view) {
        FolderEntity folder = findFolder(folderId);

        ViewEntity entity = new ViewEntity(view.name(), folder);
        entity.components = new ArrayList<>();

        if (view.components() != null) {
            for (int i = 0; i < view.components().size(); i++) {
                ViewComponent vc = view.components().get(i);
                NodeEntity node = NodeEntity.findById(vc.nodeId());
                if (node == null) {
                    throw new NotFoundException("Node not found: " + vc.nodeId());
                }
                // Nodes referenced by views must keep their data.
                // Only upgrade AUTO → KEEP. Don't override explicit DISCARD.
                if (node.ephemeral == EphemeralMode.AUTO) {
                    node.ephemeral = EphemeralMode.KEEP;
                }
                ViewComponentEntity component = new ViewComponentEntity(
                    entity, node,
                    vc.headerName() != null ? vc.headerName() : node.name,
                    vc.headerOrder() > 0 ? vc.headerOrder() : i
                );
                entity.components.add(component);
            }
        }

        entity.persist();
        folder.views.add(entity);
        return apiMapper.toView(entity);
    }

    @Override
    @Transactional
    public View updateView(Long viewId, View view) {
        ViewEntity entity = ViewEntity.findById(viewId);
        if (entity == null) {
            throw new NotFoundException("View not found: " + viewId);
        }

        entity.name = view.name();
        entity.components.clear();
        // Flush the deletes before inserting new components to avoid
        // unique constraint violations on (view_id, header_name)
        entity.flush();

        if (view.components() != null) {
            for (int i = 0; i < view.components().size(); i++) {
                ViewComponent vc = view.components().get(i);
                NodeEntity node = NodeEntity.findById(vc.nodeId());
                if (node == null) {
                    throw new NotFoundException("Node not found: " + vc.nodeId());
                }
                // Nodes referenced by views must keep their data.
                // Only upgrade AUTO → KEEP. Don't override explicit DISCARD.
                if (node.ephemeral == EphemeralMode.AUTO) {
                    node.ephemeral = EphemeralMode.KEEP;
                }
                ViewComponentEntity component = new ViewComponentEntity(
                    entity, node,
                    vc.headerName() != null ? vc.headerName() : node.name,
                    vc.headerOrder() > 0 ? vc.headerOrder() : i
                );
                entity.components.add(component);
            }
        }

        entity.persist();
        return apiMapper.toView(entity);
    }

    @Override
    @Transactional
    public void deleteView(Long viewId) {
        ViewEntity entity = ViewEntity.findById(viewId);
        if (entity == null) {
            throw new NotFoundException("View not found: " + viewId);
        }
        if (ReservedNamespace.isReserved(entity.name)) {
            throw new ForbiddenException("Cannot delete system view: " + entity.name);
        }
        entity.delete();
    }

    @Override
    @Transactional
    public List<JqValue> getViewData(long folderId, Long viewId) {
        ViewEntity view = em.createQuery(
            "SELECT v FROM folder_view v LEFT JOIN FETCH v.components c LEFT JOIN FETCH c.node WHERE v.id = :id",
            ViewEntity.class
        ).setParameter("id", viewId).getSingleResult();
        if (view == null) {
            throw new NotFoundException("View not found: " + viewId);
        }

        FolderEntity folder = findFolder(folderId);
        Long rootNodeId = folder.group.root.id;

        List<Long> nodeIds = view.components.stream()
            .map(c -> c.node.id)
            .toList();

        if (nodeIds.isEmpty()) {
            return List.of();
        }

        return valueService.getGroupedValues(rootNodeId, nodeIds);
    }

    private FolderEntity findFolder(long folderId) {
        FolderEntity folder = em.createQuery(
            "SELECT f FROM folder f JOIN FETCH f.group g LEFT JOIN FETCH g.root WHERE f.id = :id",
            FolderEntity.class
        ).setParameter("id", folderId).getSingleResult();
        if (folder == null) {
            throw new NotFoundException("Folder not found: " + folderId);
        }
        // Initialize views and their components (avoids MultipleBagFetchException)
        folder.views.size();
        folder.views.forEach(v -> v.components.size());
        return folder;
    }
}
