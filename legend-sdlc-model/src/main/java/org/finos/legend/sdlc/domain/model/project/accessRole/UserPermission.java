package org.finos.legend.sdlc.domain.model.project.accessRole;

import org.finos.legend.sdlc.domain.model.user.User;

import java.util.Set;

public interface UserPermission {
    User getUser();
    Set<AuthorizableProjectAction> getAuhorizedProjectAction();
}
