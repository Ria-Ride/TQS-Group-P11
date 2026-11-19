import type { Role } from "../api/admin";

export const ROLE_LABELS: Record<Role, string> = {
  END_USER: "Tourist",
  OPERATOR: "Operator",
  CLIENT_MANAGER: "Client manager",
  ADMIN: "Admin",
};

/** Roles an admin can give through the UI; admins are made in the database only. */
export const ASSIGNABLE_ROLES: Role[] = ["OPERATOR", "CLIENT_MANAGER", "END_USER"];