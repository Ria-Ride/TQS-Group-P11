# Pairwise testing (T7) for the role × endpoint access matrix

Story: US13 (KAN-65), sub-task KAN-78. Business rule: BR23.

## Question

Can pairwise testing (for example with Microsoft [PICT](https://github.com/microsoft/pict)) reduce the number of permission tests without losing coverage?

## The model

The access matrix (`proj/backend/src/test/resources/security/access-matrix.csv`) has two parameters:

| Parameter | Values | Count |
|---|---|---|
| Endpoint (method + path) | every row of the matrix | 41 |
| Caller | ANONYMOUS, END_USER, OPERATOR, CLIENT_MANAGER, ADMIN | 5 |

As a PICT model:

```
Endpoint: GET /hubs, POST /hubs, PATCH /hubs/{id}, ...   (41 values)
Caller:   ANONYMOUS, END_USER, OPERATOR, CLIENT_MANAGER, ADMIN
```

## Result: no reduction

Pairwise testing covers every combination of **two** parameter values. With only two parameters, "every pair" is exactly the full Cartesian product: 41 × 5 = **205 cases**, the same as exhaustive testing. PICT generates the same 205 combinations, so it reduces nothing here.

The exhaustive set is also cheap: `AccessMatrixApiIT` runs all 205 cases in about 7 seconds against the real API.

## Would more parameters change this?

Other factors could be added to the model:

| Extra parameter | Values |
|---|---|
| Token | missing, valid, invalid signature, expired |
| Account status | active, deactivated |
| Resource owner | own, someone else's |

Exhaustive: 41 × 5 × 4 × 2 × 2 = 3 280 cases. Pairwise needs at least 41 × 5 = 205 cases, because the two largest parameters must still be fully paired. That is a real reduction.

We did not adopt it, for two reasons:

1. **These factors do not depend on the endpoint.** Missing, invalid or expired tokens and deactivated accounts are handled before any endpoint rule runs. They are tested once, independently, in `SecurityErrorsApiIT` and `AccountStatusApiIT`. Splitting the model like this gives the same confidence with far fewer cases than either option above.
2. **Permissions are about specific combinations.** A pairwise set can leave out exactly the triple that matters, such as "END_USER, someone else's booking, valid token". For an access-control rule, the full role × endpoint product is the safer choice while it stays this small.

## Decision

T7 is evaluated and **not adopted** for the access matrix: with two parameters, pairwise equals exhaustive. We keep the exhaustive, matrix-driven `AccessMatrixApiIT`, and we test the endpoint-independent factors separately. Pairwise remains a candidate for business rules with three or more independent parameters, such as booking rules combining vehicle type, pass type, battery level and period.