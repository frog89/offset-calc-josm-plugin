package de.example.offsetcalc;

public class OffsetCalculationConfig {

    public double testOffsetX;
    public double testOffsetY;
    public double cannyContourApproxEpsilon;
    public int cannyMaxContourCorners;

    public OffsetCalculationConfig(
            double testOffsetX,
            double testOffsetY,
            double cannyContourApproxEpsilon,
            int cannyMaxContourCorners) {

        this.testOffsetX = testOffsetX;
        this.testOffsetY = testOffsetY;
        this.cannyContourApproxEpsilon =
                cannyContourApproxEpsilon;
        this.cannyMaxContourCorners =
                cannyMaxContourCorners;
    }
}