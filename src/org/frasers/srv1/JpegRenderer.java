/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.frasers.srv1;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.PrintStream;

/**
 * Frame listener that decodes and renders JPEG frames to an AWT canvas.
 */
public class JpegRenderer extends Canvas implements FrameListener {

    private static PrintStream __log = System.out;

    @NotNull
    private byte[] _imgBuf = new byte[0];

    private Image _img = null;
    private MediaTracker _tracker = null;

    private int _w;
    private int _h;

    private int _imagex;
    private int _imagey;

    final private Frame _frame;

    public JpegRenderer( final Frame p_frame ) {
        _frame = p_frame;
        _tracker = new MediaTracker(this);
    }

    /**
     * Identical in implementation to update, but we duplicate the code here to avoid the extra
     * method call
     * @param p_graphics Graphics context to paint on
     * @see #update(java.awt.Graphics)
     */
    public void paint(final Graphics p_graphics) {
        if (_img != null) {
            p_graphics.drawImage(_img, _imagex, _imagey, null);
        }
    }

    /**
     * Identical in implementation to paint, but we duplicate the code here to avoid the extra
     * method call
     * @param p_graphics Graphics context to paint on
     * @see #paint(java.awt.Graphics)
     */
    @Override
    public void update(final Graphics p_graphics) {
        if (_img != null) {
            p_graphics.drawImage(_img, _imagex, _imagey, null);
        }
    }

    public void newFrame(final byte[] frame) {
        // don't create a new array unless we have to to save heap churnage
        // just make one a little bigger by 10% any time we need more headroom
        if( _imgBuf.length <= frame.length ) {
            _imgBuf = new byte[frame.length + (frame.length/10)];
        }
        System.arraycopy(frame, 0, _imgBuf, 0, frame.length);

        // @todo determine if this is really needed
        /*
        if (_img != null) {
            _img.flush();
        }
        */

        // hardly the most optimal way to decode a JPEG, but...
        final Image tempImage = Toolkit.getDefaultToolkit().createImage(_imgBuf);
        _tracker.addImage(tempImage, 0);
        try {
            _tracker.waitForID(0);
        } catch (InterruptedException ie) {
            __log.println("JPEG decode error " + ie);
        }
        // if (_tracker.isErrorID(0)) {
        _img = tempImage;
        _SetImageSizeAndPosition();
        _tracker.removeImage(_img);

        repaint();
    }

    /**
     * Keeps the image centered and sized right
     */
    private void _SetImageSizeAndPosition(){
        // resize frame/window, if necessary
        if (_w != _img.getWidth(this) || _h != _img.getHeight(this)) {
            _w = _img.getWidth(this);
            _h = _img.getHeight(this);
            if (_w <= 25) {
                _w = 320;
            }
            if (_h <= 25) {
                _h = 240;
            }
            this.setSize(_w, _h);
            _frame.pack();
        }

        // keep image centered, no matter the window size
        _imagex = Math.max((this.getWidth() - _w) / 2, 0);
        _imagey = Math.max((this.getHeight() - _h) / 2, 0);
    }
}