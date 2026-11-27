package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.pass.StayManagementUseCase;

/**
 * Partner hotels and their stay codes (US14, BR9). Only the client manager and the admin use them
 * (SecurityConfiguration and the access matrix, BR23). A tourist redeems a code when buying a pass
 * (PassController).
 */
@RestController
@Tag(name = "Stay codes", description = "Partner hotels and stay codes (US14)")
class StayCodeController {

  private final StayManagementUseCase stays;

  StayCodeController(StayManagementUseCase stays) {
    this.stays = stays;
  }

  @PostMapping("/partner-accommodations")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a partner hotel with the discount of its guests")
  @ApiResponse(
      responseCode = "400",
      description = "The name is blank or the discount is not 0 to 100")
  @ApiResponse(responseCode = "409", description = "Another hotel has this name")
  PartnerAccommodationResponse registerHotel(
      @Valid @RequestBody PartnerAccommodationRequest request) {
    return PartnerAccommodationResponse.from(
        stays.registerAccommodation(request.name().strip(), request.discountPercent()));
  }

  @GetMapping("/partner-accommodations")
  @Operation(summary = "The partner hotels, by name")
  List<PartnerAccommodationResponse> hotels() {
    return stays.accommodations().stream().map(PartnerAccommodationResponse::from).toList();
  }

  @PostMapping("/stay-codes")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Issue a stay code for a partner hotel",
      description =
          "The platform makes the code. It is valid from check-in to check-out, both days"
              + " included, and gives the discount of the hotel (BR9).")
  @ApiResponse(
      responseCode = "400",
      description = "Unknown hotel, a missing or badly written date, or check-out before check-in")
  StayCodeResponse issue(@Valid @RequestBody StayCodeRequest request) {
    request.checkDates();
    return StayCodeResponse.from(
        stays.issue(request.accommodationId(), request.checkIn(), request.checkOut()));
  }

  @GetMapping("/stay-codes")
  @Operation(summary = "Every stay code, newest check-in first, with whether it was redeemed")
  List<StayCodeResponse> codes() {
    return stays.stays().stream().map(StayCodeResponse::from).toList();
  }
}
