package pt.ua.deti.tqs.p11.application.pass;

/** Makes the code of a new stay. It need not be unique: the caller asks again if it is taken. */
@FunctionalInterface
public interface StayCodeGenerator {

  String next();
}
