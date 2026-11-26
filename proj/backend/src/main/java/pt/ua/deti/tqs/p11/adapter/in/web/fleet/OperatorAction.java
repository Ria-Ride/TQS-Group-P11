package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

/**
 * The actions an operator asks for through POST /vehicles/{id}/transitions (US08): the lifecycle
 * actions of the same name, minus those only other flows may cause. Starting a use is not here: it
 * happens only through a booking, after its deposit is held (BR11). Any other value in the request
 * is a 400.
 */
enum OperatorAction {
  ACTIVATE,
  DEACTIVATE,
  START_MOVE,
  CONFIRM_ARRIVAL,
  RETIRE
}
