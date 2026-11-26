package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.User;

/** A field technician a ticket can be assigned to: only what the operator needs to choose. */
record TechnicianResponse(UUID id, String name) {

  static TechnicianResponse from(User user) {
    return new TechnicianResponse(user.id(), user.name());
  }
}
