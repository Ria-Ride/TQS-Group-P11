package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.pass.PassPurchaseUseCase;

/**
 * Pass types and passes (US03). Every role reads the pass types; tourists (END_USER) buy passes and
 * list their own (SecurityConfiguration and the access matrix, BR23). The account is always the
 * caller's, so nobody can buy for, or read the passes of, someone else.
 */
@RestController
@Tag(name = "Passes", description = "Day and multi-day passes (US03)")
class PassController {

  private final PassPurchaseUseCase passes;

  PassController(PassPurchaseUseCase passes) {
    this.passes = passes;
  }

  @GetMapping("/pass-types")
  @Operation(summary = "The pass types, shortest validity first, with base price and validity")
  List<PassTypeResponse> passTypes() {
    return passes.passTypes().stream().map(PassTypeResponse::from).toList();
  }

  @GetMapping("/pass-types/{id}/price")
  @Operation(
      summary = "What the pass would cost the caller now",
      description =
          "With a stay code, the price after the hotel's discount if the code is valid for the"
              + " caller today (BR9). Nothing is charged or linked; the purchase checks again.")
  @ApiResponse(responseCode = "200", description = "The price, and the discount applied")
  @ApiResponse(
      responseCode = "400",
      description =
          "passTypeId unknown, or stayCode unknown, not valid today or of another account")
  PriceQuoteResponse price(
      @PathVariable UUID id,
      @RequestParam(required = false) String stayCode,
      Authentication caller) {
    return PriceQuoteResponse.from(passes.quote(accountOf(caller), id, stayCode));
  }

  @PostMapping("/passes")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Buy a pass for the caller",
      description =
          "Charges the price, minus the hotel's discount when a valid stay code is given (BR9),"
              + " and issues a pass valid from now for validityDays x 24 h (BR8). No pass is"
              + " issued unless the payment is approved (BR10).")
  @ApiResponse(responseCode = "201", description = "Paid; the pass is issued")
  @ApiResponse(
      responseCode = "400",
      description =
          "Invalid field: passTypeId unknown, or stayCode unknown, not valid today or linked to"
              + " another account. Nothing was charged; buy again without the code for the base"
              + " price")
  @ApiResponse(responseCode = "402", description = "The payment was declined; no pass")
  @ApiResponse(
      responseCode = "504",
      description = "The payment got no answer in time; no pass. Safe to try again")
  PassResponse buy(@Valid @RequestBody BuyPassRequest request, Authentication caller) {
    return PassResponse.from(passes.buy(request.toCommand(accountOf(caller))));
  }

  @GetMapping("/passes")
  @Operation(summary = "The caller's passes, newest first")
  List<PassResponse> mine(Authentication caller) {
    return passes.passesOf(accountOf(caller)).stream().map(PassResponse::from).toList();
  }

  /** The access token's subject is the account id (ActiveAccountAuthenticationConverter). */
  private static UUID accountOf(Authentication caller) {
    return UUID.fromString(caller.getName());
  }
}
