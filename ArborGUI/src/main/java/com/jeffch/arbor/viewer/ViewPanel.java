/*
 *     Arbor: Procedural Voxel Tree Generation.
 *     Copyright (C) 2026  Jeff Chen
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jeffch.arbor.viewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;

/**
 * Shows frames rendered by OpenGL and turns mouse drags into camera moves.
 */
final class ViewPanel extends JPanel {
  static final String CAMERA_HELP = "Drag to orbit. Right, middle or Shift drag to pan. Scroll or pinch to zoom; "
          + "scroll sideways to turn.";
  private static final double WHEEL_ORBIT_PIXELS = 12;
  private BufferedImage image;
  private int lastX;
  private int lastY;

  /**
   * Sends camera input and view size to the viewer, in physical pixels.
   *
   * @param app Viewer that receives the input.
   */
  ViewPanel(ViewerApp app) {
    setBackground(new Color(23, 26, 31));
    setPreferredSize(new Dimension(800, 760));
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        lastX = event.getX();
        lastY = event.getY();
      }

      @Override
      public void mouseDragged(MouseEvent event) {
        double scale = scale();
        double dx = (event.getX() - lastX) * scale;
        double dy = (event.getY() - lastY) * scale;
        lastX = event.getX();
        lastY = event.getY();
        // Shift with a left drag pans too, for touchpads without a middle or easy right drag.
        if (SwingUtilities.isLeftMouseButton(event) && !event.isShiftDown()) {
          app.orbitCamera(dx, dy);
        } else {
          app.panCamera(dx, dy);
        }
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent event) {
        // Touchpads send a sideways two-finger swipe as a wheel turn with Shift held, and a pinch as one with Ctrl.
        if (event.isShiftDown()) {
          app.orbitCamera(-event.getPreciseWheelRotation() * WHEEL_ORBIT_PIXELS, 0);
        } else {
          app.zoomCamera(-event.getPreciseWheelRotation());
        }
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addMouseWheelListener(mouse);
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        double scale = scale();
        app.viewResized((int) Math.round(getWidth() * scale), (int) Math.round(getHeight() * scale));
      }
    });
  }

  /**
   * The screen's scaling, so the view renders at full resolution on high-DPI displays.
   */
  private double scale() {
    return getGraphicsConfiguration() == null ? 1 : getGraphicsConfiguration().getDefaultTransform().getScaleX();
  }

  /**
   * Copies a bottom-up RGBA frame. Called from the render thread.
   *
   * @param pixels Frame pixels, bottom row first.
   * @param width  Frame width.
   * @param height Frame height.
   */
  void present(ByteBuffer pixels, int width, int height) {
    synchronized (this) {
      if (image == null || image.getWidth() != width || image.getHeight() != height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      }
      int[] out = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
      for (int y = 0; y < height; y++) {
        int source = (height - 1 - y) * width * 4;
        int target = y * width;
        for (int x = 0; x < width; x++, source += 4) {
          out[target + x] = (pixels.get(source) & 255) << 16 | (pixels.get(source + 1) & 255) << 8
                  | pixels.get(source + 2) & 255;
        }
      }
    }
    repaint();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    synchronized (this) {
      if (image != null) {
        graphics.drawImage(image, 0, 0, getWidth(), getHeight(), null);
      }
    }
  }
}
