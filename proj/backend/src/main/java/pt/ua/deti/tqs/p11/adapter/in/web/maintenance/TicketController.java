package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
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
import pt.ua.deti.tqs.p11.application.maintenance.CloseTicketCommand;
import pt.ua.deti.tqs.p11.application.maintenance.TechnicianTicketsUseCase;
import pt.ua.deti.tqs.p11.application.maintenance.TicketManagementUseCase;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;

/**
 * Maintenance tickets (US10, US11), for operators and field technicians (SecurityConfiguration and
 * the access matrix, BR23); who may start or close a ticket is checked by the domain. The rules are
 * in the use case and the domain; this class only maps HTTP to them.
 */
@RestController
@Tag(name = "Maintenance", description = "Maintenance tickets and field technicians (US10)")
class TicketController {

  private final TicketManagementUseCase tickets;
  private final TechnicianTicketsUseCase technicianTickets;
  private final Clock clock;

  TicketController(
      TicketManagementUseCase tickets, TechnicianTicketsUseCase technicianTickets, Clock clock) {
    this.tickets = tickets;
    this.technicianTickets = technicianTickets;
    this.clock = clock;
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
    return response(tickets.open(request.toCommand(accountOf(caller))));
  }

  @GetMapping("/tickets")
  @Operation(summary = "The open tickets, nearest SLA deadline first")
  List<TicketResponse> openTickets() {
    Instant now = clock.instant();
    return tickets.openTickets().stream().map(ticket -> TicketResponse.from(ticket, now)).toList();
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
    return response(tickets.assign(id, request.technicianId()));
  }

  @GetMapping("/technicians")
  @Operation(summary = "The active field technicians a ticket can be assigned to, by name")
  List<TechnicianResponse> technicians() {
    return tickets.technicians().stream().map(TechnicianResponse::from).toList();
  }

  // ------------------------------------------------------------- field technicians (US11)

  @GetMapping("/tickets/mine")
  @Operation(
      summary = "The caller's open tickets, nearest SLA deadline first",
      description =
          "Each with the vehicle, the hub it is at (or its last position when it is at no hub)"
              + " and whether the deadline has passed (BR19).")
  List<TechnicianTicketResponse> myTickets(Authentication caller) {
    return technicianTickets.myTickets(accountOf(caller)).stream()
        .map(TechnicianTicketResponse::from)
        .toList();
  }

  @PostMapping("/tickets/{id}/start")
  @Operation(summary = "Start the work on one of the caller's Assigned tickets")
  @ApiResponse(responseCode = "200", description = "InProgress")
  @ApiResponse(responseCode = "403", description = "The ticket is assigned to someone else")
  @ApiResponse(responseCode = "404", description = "Unknown ticket")
  @ApiResponse(responseCode = "409", description = "The ticket is not Assigned")
  TicketResponse start(@PathVariable UUID id, Authentication caller) {
    return response(technicianTickets.start(id, accountOf(caller)));
  }

  @PostMapping("/tickets/{id}/close")
  @Operation(
      summary = "Close one of the caller's InProgress tickets, recording the work done",
      description =
          "Records the closing time and the time to repair (BR19). The vehicle returns to"
              + " Available if it was in Maintenance and no other ticket is open for it (BR20).")
  @ApiResponse(responseCode = "200", description = "Closed")
  @ApiResponse(responseCode = "400", description = "workDone blank or too long")
  @ApiResponse(responseCode = "403", description = "The ticket is assigned to someone else")
  @ApiResponse(responseCode = "404", description = "Unknown ticket")
  @ApiResponse(responseCode = "409", description = "The work on the ticket has not started")
  TicketResponse close(
      @PathVariable UUID id,
      @Valid @RequestBody CloseTicketRequest request,
      Authentication caller) {
    return response(
        technicianTickets.close(new CloseTicketCommand(id, accountOf(caller), request.workDone())));
  }

  private TicketResponse response(MaintenanceTicket ticket) {
    return TicketResponse.from(ticket, clock.instant());
  }

  private static UUID accountOf(Authentication caller) {
    return UUID.fromString(caller.getName());
  }
}
