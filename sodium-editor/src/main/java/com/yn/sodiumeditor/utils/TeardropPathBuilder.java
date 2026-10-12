package com.yn.sodiumeditor.utils;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * Builds a teardrop (water-drop) handle path. The drop has a pointed tip at the top and a circular
 * body at the bottom, sized to fit inside the supplied bounds.
 *
 * <p>The shape is a circle plus the two tangent lines drawn from the tip point, which keeps the
 * silhouette smooth (C1 continuous) without heavy computation or allocation.
 */
public final class TeardropPathBuilder {

  /** Fraction of the smallest bounds side used as the circular body radius. */
  private static final float BODY_RADIUS_RATIO = 0.4f;

  private TeardropPathBuilder() {}

  /**
   * Build or reuse a teardrop path inside the given bounds.
   *
   * @param path path to mutate (reset before use)
   * @param bounds bounding box the drop should fit into
   */
  public static void build(Path path, RectF bounds) {
    build(path, bounds.centerX(), bounds.top, bounds.bottom, bounds.width(), bounds.height());
  }

  /**
   * Build or reuse a teardrop path.
   *
   * @param path path to mutate (reset before use)
   * @param centerX horizontal center of the drop (also the tip x)
   * @param top y of the pointed tip
   * @param bottom y of the bottom of the circular body
   * @param width width of the bounds the drop should fit into
   * @param height height of the bounds the drop should fit into
   */
  public static void build(Path path, float centerX, float top, float bottom, float width, float height) {
    path.reset();

    float radius = Math.min(width, height) * BODY_RADIUS_RATIO;
    float bodyCenterY = bottom - radius;
    float tipToCenter = bodyCenterY - top;

    if (radius <= 0f || tipToCenter <= radius) {
      // Degenerate metrics: fall back to a plain circle so the path never breaks.
      float fallbackRadius = Math.max(Math.min(width, height) / 2f, 1f);
      path.addCircle(centerX, top + fallbackRadius, fallbackRadius, Path.Direction.CW);
      return;
    }

    // Tangent points from the tip to the body circle sit at +/- beta from the upward direction.
    float betaDeg = (float) Math.toDegrees(Math.acos(radius / tipToCenter));

    path.moveTo(centerX, top);
    // arcTo starts by drawing the (tangent) line from the tip to the arc start, then sweeps the
    // long way around the body; close() connects the trailing tangent line back to the tip.
    path.arcTo(
        centerX - radius,
        bodyCenterY - radius,
        centerX + radius,
        bodyCenterY + radius,
        -90f + betaDeg,
        360f - 2f * betaDeg,
        false);
    path.close();
  }
}
