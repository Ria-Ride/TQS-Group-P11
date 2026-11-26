package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.p11.application.pass.PassTypeManagementUseCase;

/**
 * Creating and changing pass types (US14, BR8). Every role reads them (PassController); only the
 * client manager and the admin edit them (SecurityConfiguration and the access matrix, BR23). A
 * pass already bought keeps the price and dates it was bought with.
 */
@RestController
@Tag(name = "Pass types", description = "Pass types edited by the client manager (US14)")
class PassTypeManagementController {

  private final PassTypeManagementUseCase passTypes;

  PassTypeManagementController(PassTypeManagementUseCase passTypes) {
    this.passTypes = passTypes;
  }

  @PostMapping("/pass-types")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a pass type")
  @ApiResponse(responseCode = "201", description = "Created")
  @ApiResponse(responseCode = "400", description = "A field is invalid")
  @ApiResponse(responseCode = "409", description = "Another pass type has this code")
  PassTypeResponse create(@Valid @RequestBody CreatePassTypeRequest request) {
    return PassTypeResponse.from(passTypes.create(request.toCommand()));
  }

  @PatchMapping("/pass-types/{id}")
  @Operation(
      summary = "Change a pass type",
      description =
          "Changes only the fields given: name, validity, price, allowed vehicle types. The code"
              + " never changes. Passes already bought are not touched.")
  @ApiResponse(responseCode = "400", description = "A field is invalid")
  @ApiResponse(responseCode = "404", description = "No such pass type")
  PassTypeResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdatePassTypeRequest request) {
    return PassTypeResponse.from(passTypes.update(id, request.toCommand()));
  }
}
