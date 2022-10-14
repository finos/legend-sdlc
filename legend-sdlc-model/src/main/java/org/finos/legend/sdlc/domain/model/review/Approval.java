package org.finos.legend.sdlc.domain.model.review;

import org.finos.legend.sdlc.domain.model.user.User;

import java.util.List;

public interface Approval
{
  List<User> getApprovedBy();
}
