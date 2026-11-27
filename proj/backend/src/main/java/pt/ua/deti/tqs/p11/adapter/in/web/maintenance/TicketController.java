package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.maintenance.TicketManagementUseCase;

/**
 * Maintenance tickets (US10), for operators (SecurityConfiguration and the access matrix, BR23).
 * The rules are in the use case and the domain; this class only maps HTTP to them.
 */
@RestController
@Tag(name = "Maintenance", description = "Maintenance tickets and field technicians (US10)")
class TicketController {

  private final TicketManagementUseCase tickets;

  TicketController(TicketManagementUseCase tickets) {
    this.tickets = tickets;
  }

  @PostMapping("/tickets")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Open a ticket for a fault the simulator cannot detect",
      description =
          "Due its opening time plus the SLA (BR19). An Available or Reserved vehicle goes to"
              + " Maintenance, and a Reserved booking is cancelled; an InUse ride is not"
              + " interrupted (BR18).")
  @ApiResponse(responseCode = "201", description = "The ticket is open")
  @ApiResponse(responseCode = "400", description = "Invalid field")
  @ApiResponse(responseCode = "404", description = "Unknown vehicle")
  @ApiResponse(
      responseCode = "409",
      description =
          "The fault already has an open ticket, given in ticketId (BR20), or the vehicle is"
              + " retired")
  TicketResponse open(@Valid @RequestBody OpenTicketRequest request, Authentication caller) {
    return TicketResponse.from(tickets.open(request.toCommand(accountOf(caller))));
  }

  @GetMapping("/tickets")
  @Operation(summary = "The open tickets, nearest SLA deadline first")
  List<TicketResponse> openTickets() {
    return tickets.openTickets().stream().map(TicketResponse::from).toList();
  }

  @PatchMapping("/tickets/{id}/assignee")
  @Operation(summary = "Assign the ticket to a field technician (BR20)")
  @ApiResponse(responseCode = "200", description = "Assigned")
  @ApiResponse(
      responseCode = "400",
      description = "technicianId missing, or not an active field technician")
  @ApiResponse(responseCode = "404", description = "Unknown ticket")
  @ApiResponse(responseCode = "409", description = "The work has started or the ticket is done")
  TicketResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignTicketRequest request) {
    return TicketResponse.from(tickets.assign(id, request.technicianId()));
  }

  @GetMapping("/technicians")
  @Operation(summary = "The active field technicians a ticket can be assigned to, by name")
  List<TechnicianResponse> technicians() {
    return tickets.technicians().stream().map(TechnicianResponse::from).toList();
  }

  private static UUID accountOf(Authentication caller) {
    return UUID.fromString(caller.getName());
  }
}
