package com.yn.sodiumeditor.state;

/**
 * State class for loading circle functionality.
 * Stores loading circle state including visibility, radius, color, and rotation.
 */
public class LoadingCircleState {

    private static final float DEFAULT_RADIUS = 40f;
    private static final int DEFAULT_COLOR = 0xFF3F51B5;

    private boolean show = false;
    private float radius = DEFAULT_RADIUS;
    private int color = DEFAULT_COLOR;
    private float rotation = 0f;

    public LoadingCircleState() {
    }

    public boolean isShow() {
        return show;
    }

    public void setShow(boolean show) {
        this.show = show;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public void reset() {
        show = false;
        rotation = 0f;
    }
}
