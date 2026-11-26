package pt.ua.deti.tqs.p11.adapter.in.web.booking;

/** A vehicle type and the deposit a booking of it holds (BR11), as a string such as "30.00". */
record VehicleTypeResponse(String type, String deposit, String currency) {}
