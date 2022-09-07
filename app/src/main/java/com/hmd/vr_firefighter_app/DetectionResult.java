package com.hmd.vr_firefighter_app;

public final class DetectionResult
        implements Comparable<DetectionResult> {
    private final Integer id;
    private final String title;
    private final Float confidence;
    private final Integer left;
    private final Integer top;
    private final Integer right;
    private final Integer bottom;

    public DetectionResult(final Integer id,
                           final String title,
                           final Float confidence,
                           final Integer left,
                           final Integer top,
                           final Integer right,
                           final Integer bottom) {
        this.id = id;
        this.title = title;
        this.confidence = confidence;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;

    }

    public Integer getId() {
        return id;
    }

    public String getTitle() { return title; }

    public Float getConfidence() { return confidence; }

    public Integer getLeft(){ return left;}

    public Integer getTop(){ return top;}

    public Integer getRight(){return right;}

    public Integer getBottom(){return bottom;}

    @Override
    public int compareTo (DetectionResult other) {
        return Float.compare(other.getConfidence(), this.getConfidence());
    }

    @Override
    public String toString() {
        return "DetectionResult{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", confidence=" + confidence +
                ", location =[" + left + ", " + top + ", " + right + ", " + bottom + "]" +
                '}';
    }
}
