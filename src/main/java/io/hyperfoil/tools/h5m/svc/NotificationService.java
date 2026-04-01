package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.node.NotificationNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class NotificationService {

    public void notify(FolderEntity folder, NotificationNode node, List<ValueEntity> detectionValues) {
        System.out.println("Notification: " + detectionValues.size() + " change(s) detected in " + folder.name);
    }
}
