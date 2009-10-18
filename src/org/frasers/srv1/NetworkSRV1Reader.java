/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.frasers.srv1;

import java.io.*;
import java.util.*;
import java.net.*;
import static java.lang.String.format;

/**
 *
 * @author sfraser
 */
public class NetworkSRV1Reader extends Thread {

    final boolean bDebug = false;

    private static PrintStream log = System.out;

    private static final byte[] FRAME_HEAD = { '#', '#', 'I', 'M', 'J' };

    private InetAddress host;  // SRV-1 connection information
    private int port;
    private String transport;
    private Socket s = null;
    private DatagramSocket dgs = null;
    private InputStream is = null;  // only applicable in TCP mode
    private OutputStream os = null; // "
    private boolean udp = false; // true => UDP, false => TCP
    private boolean connected = false;
    private boolean shouldRun = true;
    private DatagramPacket frameRequest = null;
    private java.util.List/*<FrameListener>*/ frameListeners = new ArrayList();
    private java.util.List/*<SRV1Command>*/ commandQueue = Collections.synchronizedList(new ArrayList());

    public NetworkSRV1Reader(String host, int port, String transport,
            java.util.List/*<FrameListener>*/ frameListeners) {
        try {
            this.host = InetAddress.getByName(host);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.port = port;
        this.transport = transport;
        this.frameListeners = frameListeners;
        frameRequest = new DatagramPacket(new byte[]{(byte) 'I'}, 1, this.host, this.port);
        log.println("[NetworkSRV1Reader] - " + host + ":" + port + " (" + transport + ")");
    }

    public boolean sendCommand(String cmdString, byte[] cmdBytes, SRV1CommandCallback cb) {

        commandQueue.add(new SRV1Command(cmdString, cmdBytes, cb));
        return true;
    }

    @Override
    public void interrupt() {
        this.shouldRun = false;
    }

    @Override
    public void run() {
        byte[] frame = null;
        int framePos = 0; // current position in "frame" byte[]
        int frameCount = 0;
        long start = System.currentTimeMillis();

        while (this.shouldRun) {
            // 1) Send pending commands (if any), read responses
            // 2) Send frame request
            // 3) Read frame data
            // 4) goto step 1

            if (!connected) {
                _Open();

                // if still not connected BAIL
                if(!connected) {
                    log.println("[NetworkSRV1Reader] - error in main loop - unable to connect");
                    return;
                }
            }

            try {
                if (frame == null) {

                    // run through the command queue befure requesting a new frame
                    while (!commandQueue.isEmpty()) {
                        SRV1Command c = (SRV1Command) commandQueue.remove(0);
                        _Send(c.getBytes());
                        // Thread.sleep(1000);
                        byte[] cmdResponse = new byte[SRV1Test.MTU];
                        int resLen = _Read(cmdResponse);
                        String response = "--no response--";
                        if (resLen > 0) {
                            response = new String(cmdResponse, 0, resLen);
                        }
                        c.getCallback().success(c.getString(), response);
                    }

                    _Send(frameRequest);
                }

                byte[] buf = new byte[SRV1Test.MTU];

                int retries = 4;
                int bytes = _Read(buf);
                while (retries-- > 0 && bytes < 0) {
                    bytes = _Read(buf);
                }

                if (retries <= 0) {
                    frame = null;
                    framePos = 0;
                    continue;
                }

                if (bytes > 0 && frame == null) {
                    int frameStart = _IndexOf(buf, FRAME_HEAD, 0);
                    if (frameStart == -1) {
                        log.println("discarding...");
                        // TODO: read / discard everything on the line
                        continue;
                    } else {
                        int offset = FRAME_HEAD.length;
                        long frameSize = 0;
                        byte frameDim = buf[offset++];
                        if( bDebug ) {
                            log.println("frame dim: " + frameDim);
                        }
                        for (int i = 0; i < 4; i++) {
                            frameSize += (0xff & buf[offset++]) << (8 * i);
                        }
                        if( bDebug ) {
                            log.println("frame size: " + frameSize);
                        }
                        frame = new byte[(int) frameSize];
                        framePos = 0;
                        System.arraycopy(buf, offset, frame, framePos, bytes - offset);
                        framePos += bytes - offset;
                    }
                } else if (bytes > 0 && frame != null) {
                    int leftToRead = frame.length - framePos; // bytes that remain to be read

                    if (bytes < leftToRead) {
                        System.arraycopy(buf, 0, frame, framePos, bytes);
                        framePos += bytes;
                    } else {
                        System.arraycopy(buf, 0, frame, framePos, leftToRead);

                        // ship this frame out to the frame listener(s)
                        for (Iterator fl = this.frameListeners.iterator(); fl.hasNext();) {
                            ((FrameListener) fl.next()).newFrame(frame);
                        }

                        frameCount++;
                        long elapsed = (System.currentTimeMillis() - start) / 1000; // s
                        float fps = frameCount / (elapsed + 1);
                        if( bDebug ) {
                            log.println("read full frame, size: " + frame.length + ", " + fps + " fps");
                        }
                        framePos = 0;
                        frame = null;
                    }
                } else {
                    // no bytes ready, delay
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ie) {
                    }
                }

            } catch (Throwable t) {
                log.println("[NetworkSRV1Reader] - error in main loop - " + t);
                t.printStackTrace();
            }
        }

        _Close();
    }

    // Search for the sequence "part" in the full array "data", starting at index "start".
    // Returns -1 if the "data" array does not contain "part".
    private int _IndexOf(byte[] data, byte[] part, int start) {
        int match = -1;
        for (int i = start; i <= data.length - part.length && match == -1; i++) {
            for (int j = 0; j < part.length && data[i + j] == part[j]; j++) {
                if (j == part.length - 1) {
                    match = i;
                }
            }
        }
        return match;
    }

    private int _Read(byte[] buf) throws Exception {
        int bytes = -1;
        if (udp) {
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try {
                dgs.receive(dp);
                bytes = dp.getLength();
            } catch (SocketTimeoutException ste) {
                bytes = -1;
            }
        } else {
            try {
                bytes = is.read(buf);
            } catch (SocketTimeoutException ste) {
                // ste.printStackTrace();
                bytes = -1;
            }

        }
        //log.println("_read() - " + bytes + " bytes");
        return bytes;
    }

    private void _Send(DatagramPacket dp) throws Exception {
        if (udp) {
            dgs.send(dp);
        } else {
            os.write(dp.getData());
            os.flush();
        }
    //log.println("_send(DP) - " + dp.getData().length + " bytes");
    }

    private void _Send(byte[] msg) throws Exception {
        if (udp) {
            DatagramPacket dp = new DatagramPacket(msg, msg.length,
                    this.host, this.port);
            dgs.send(dp);
        } else {
            os.write(msg);
            os.flush();
            // log.println( format( "Wrote [%s] over TCP", msg ));
        }
    //log.println("_send() - " + msg.length + " bytes");
    }

    private boolean _Open() {
        if (connected) {
            log.println("[NetworkSRV1Reader] - open() called when already connected");
        }

        if ("UDP".equalsIgnoreCase(transport)) {
            // set up UDP socket
            try {
                this.dgs = new DatagramSocket(SRV1Test.UDP_LOCAL_PORT);
                this.dgs.setSoTimeout(SRV1Test.SO_TIMEOUT);
                this.dgs.connect(this.host, this.port);
                log.println("[NetworkSRV1Reader] - listening on UDP port: " + SRV1Test.UDP_LOCAL_PORT);
                udp = true;
                connected = true;

                //_send(new byte[] { 'a' });

                return true;
            } catch (Exception e) {
                log.println("[NetworkSRV1Reader] - error during UDP open() " + e);
                e.printStackTrace();
            }
        } else {
            // default to TCP-mode
            try {
                this.s = new Socket(host, port);
                this.s.setSoTimeout(SRV1Test.SO_TIMEOUT);
                this.s.setKeepAlive(true);
                this.s.setTcpNoDelay(true);
                this.is = new BufferedInputStream(this.s.getInputStream());
                this.os = new BufferedOutputStream(this.s.getOutputStream());
                log.println("[NetworkSRV1Reader] - created TCP connection");
                connected = true;
                return true;
            } catch (Exception e) {
                log.println("[NetworkSRV1Reader] - error during TCP open() " + e);
                e.printStackTrace();
            }

        }
        _Close(); // clean up (possibly partially established connections)
        return false;
    }

    private boolean _Close() {
        if (this.dgs != null) {
            try {
                this.dgs.close();
            } catch (Exception e) {
            }
        }
        if (this.is != null) {
            try {
                this.is.close();
            } catch (Exception e) {
            }
        }
        if (this.os != null) {
            try {
                this.os.close();
            } catch (Exception e) {
            }
        }
        if (this.s != null) {
            try {
                this.s.close();
            } catch (Exception e) {
            }
        }
        this.s = null;
        this.dgs = null;
        this.is = null;
        this.os = null;
        connected = false;
        return true;
    }
}
