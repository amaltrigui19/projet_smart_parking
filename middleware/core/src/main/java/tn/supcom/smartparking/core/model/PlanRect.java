package tn.supcom.smartparking.core.model;

/** Rectangle on the scale-model floor plan, in cm (contract §4.3): top-left corner, width, height. */
public record PlanRect(double x, double y, double width, double height) {
    public PlanRect {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("rect_cm: width and height must be > 0");
        }
    }
}
