/**
 * The test cards of the simulated gateway: each one decides the gateway's answer, for pass
 * payments (US03) and ride deposits (US04).
 */
export const TEST_CARDS = [
  { token: "tok_approve", label: "Payment approved" },
  { token: "tok_decline", label: "Payment declined" },
  { token: "tok_timeout", label: "No answer from the bank (times out)" },
] as const;