package com.judge.judge.model;

/**
 * Outcome of a custom checker run.
 * Protocol (testlib-ish): exit 0 = AC (ratio 1.0), exit 1 = WA (ratio 0),
 * exit 7 = PC with the first stdout line parsed as a 0..1 ratio, else SE.
 */
public record CheckerResult(String verdict, double ratio) {
    public static CheckerResult ac()  { return new CheckerResult("AC", 1.0); }
    public static CheckerResult wa()  { return new CheckerResult("WA", 0.0); }
    public static CheckerResult se()  { return new CheckerResult("SE", 0.0); }
    public static CheckerResult pc(double ratio) {
        double r = Math.max(0.0, Math.min(1.0, ratio));
        return new CheckerResult(r >= 1.0 ? "AC" : "PC", r);
    }
}
