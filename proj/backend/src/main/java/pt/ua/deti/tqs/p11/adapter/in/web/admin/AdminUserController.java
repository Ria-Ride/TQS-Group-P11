package pt.ua.deti.tqs.p11.adapter.in.web.admin;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.user.AccountAdministrationUseCase;

/**
 * Account management for platform admins (US13). Only ADMIN reaches it: /admin/** is restricted in
 * SecurityConfiguration (BR23). The rules live in the use case and the domain.
 */
@RestController
@RequestMapping("/admin/users")
class AdminUserController {

  private final AccountAdministrationUseCase accounts;

  AdminUserController(AccountAdministrationUseCase accounts) {
    this.accounts = accounts;
  }

  @GetMapping
  List<AdminUserResponse> list() {
    return accounts.list().stream().map(AdminUserResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  AdminUserResponse create(@Valid @RequestBody CreateAccountRequest request) {
    return AdminUserResponse.from(accounts.create(request.toCommand()));
  }

  @PatchMapping("/{id}/role")
  AdminUserResponse changeRole(
      @PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
    return AdminUserResponse.from(
        accounts.changeRole(id, request.role(), request.fieldTechnician()));
  }

  @PatchMapping("/{id}/status")
  AdminUserResponse changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
    return AdminUserResponse.from(accounts.setActive(id, request.active()));
  }
}
