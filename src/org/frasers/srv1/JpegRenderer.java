/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.frasers.srv1;

import java.awt.*;
import java.io.PrintStream;

/**
 * Frame listener that decodes and renders JPEG frames to an AWT canvas.
 */
public class JpegRenderer extends Canvas implements FrameListener {

    private static PrintStream log = System.out;

    private byte[] imgBuf = null;
    private Image img = null;
    private MediaTracker tracker = null;
    private int w,  h,  x,  y;

    final private Frame m_frame;

    public JpegRenderer( final Frame p_frame ) {
        m_frame = p_frame;
        tracker = new MediaTracker(this);
    }

    public void paint(Graphics g) {
        if (img == null && imgBuf != null) {
            // hardly the most optimal way to decode a JPEG, but...
            img = Toolkit.getDefaultToolkit().createImage(imgBuf);
            tracker.addImage(img, 0);
            try {
                tracker.waitForID(0);
            } catch (InterruptedException ie) {
                log.println("JPEG decode error " + ie);
            }
            if (!tracker.isErrorID(0)) {
            }
            tracker.removeImage(img);
        }

        if (img != null) {
            // resize frame/window, if necessary
            if (w != img.getWidth(this) || h != img.getHeight(this)) {
                w = img.getWidth(this);
                h = img.getHeight(this);
                if (w <= 25) {
                    w = 320;
                }
                if (h <= 25) {
                    h = 240;
                }
                this.setSize(w, h);
                m_frame.pack();
            }

            // keep image centered, no matter the window size
            x = Math.max((this.getWidth() - w) / 2, 0);
            y = Math.max((this.getHeight() - h) / 2, 0);
            g.drawImage(img, x, y, null);
        }
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    public void newFrame(byte[] frame) {
        imgBuf = new byte[frame.length];
        System.arraycopy(frame, 0, imgBuf, 0, frame.length);
        if (img != null) {
            img.flush();
        }
        img = null;
        repaint();
    }
}