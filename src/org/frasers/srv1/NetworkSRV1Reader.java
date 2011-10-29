/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.frasers.srv1;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author sfraser
 */
public class NetworkSRV1Reader extends Thread {

    final boolean _bDebug = false;

    private static PrintStream __log = System.out;

    private static final byte[] __FRAME_HEAD = {'#', '#', 'I', 'M', 'J'};

    final private InetAddress _host;  // SRV-1 connection information
    final private int _port;
    final private String _transport;
    private Socket _sock = null;
    private DatagramSocket _dgs = null;
    private InputStream _is = null;  // only applicable in TCP mode
    private OutputStream _os = null; // "
    private boolean _isUdp = false; // true => UDP, false => TCP
    private boolean _isConnected = false;
    private boolean _shouldRun = true;
    private DatagramPacket _frameRequest = null;
    final private FrameListener _frameListener;
    final private List<SRV1Command> _commandQueue = Collections.synchronizedList(new ArrayList<SRV1Command>());

    public NetworkSRV1Reader(final String p_sHost,
                             final int p_port,
                             final String p_sTransport,
                             final FrameListener p_frameListeners) {
        try {
            _host = InetAddress.getByName(p_sHost);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        _port = p_port;
        _transport = p_sTransport;
        _frameListener = p_frameListeners;
        _frameRequest = new DatagramPacket(new byte[]{(byte) 'I'}, 1, _host, _port);
        __log.println("[NetworkSRV1Reader] - " + p_sHost + ":" + p_port + " (" + p_sTransport + ")");

        _Open();

        // if still not connected BAIL
        if (!_isConnected) {
            __log.println("[NetworkSRV1Reader] - unable to connect");
            throw new RuntimeException("Unable to connect!");
        }

    }

    public boolean sendCommand(String cmdString, byte[] cmdBytes, SRV1CommandCallback cb) {

        _commandQueue.add(new SRV1Command(cmdString, cmdBytes, cb));
        return true;
    }

    @Override
    public void interrupt() {
        _shouldRun = false;
    }

    @Override
    public void run() {
        byte[] frame = null;
        int framePos = 0; // current position in "frame" byte[]
        int frameCount = 0;
        final long start = System.currentTimeMillis();

        while (_shouldRun) {
            // 1) Send pending commands (if any), read responses
            // 2) Send frame request
            // 3) Read frame data
            // 4) goto step 1

            try {
                if (frame == null) {

                    // run through the command queue befure requesting a new frame
                    while (!_commandQueue.isEmpty()) {
                        SRV1Command c = _commandQueue.remove(0);
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

                    _Send(_frameRequest);
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
                    int frameStart = __IndexOf(buf, __FRAME_HEAD, 0);
                    if (frameStart == -1) {
                        __log.println("discarding...");
                        // TODO: read / discard everything on the line
                        continue;
                    } else {
                        int offset = __FRAME_HEAD.length;
                        long frameSize = 0;
                        byte frameDim = buf[offset++];
                        if (_bDebug) {
                            __log.println("frame dim: " + frameDim);
                        }
                        for (int i = 0; i < 4; i++) {
                            frameSize += (0xff & buf[offset++]) << (8 * i);
                        }
                        if (_bDebug) {
                            __log.println("frame size: " + frameSize);
                        }
                        // @todo java.lang.NegativeArraySizeException
                        // @todo reuse a buffer here???
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

                        _frameListener.newFrame(frame);

                        frameCount++;
                        long elapsed = (System.currentTimeMillis() - start) / 1000; // _sock
                        float fps = frameCount / (elapsed + 1);
                        if (_bDebug) {
                            __log.println("read full frame, size: " + frame.length + ", " + fps + " fps");
                        }
                        framePos = 0;
                        frame = null;
                    }
                }
            } catch (Throwable t) {
                __log.println("[NetworkSRV1Reader] - error in main loop - " + t);
                t.printStackTrace();
            }
        }

        _Close();
    }

    // Search for the sequence "part" in the full array "data", starting at index "start".
    // Returns -1 if the "data" array does not contain "part".

    private static int __IndexOf(
            final byte[] p_data,
            final byte[] p_part,
            final int p_start) {
        int match = -1;
        for (int i = p_start; i <= p_data.length - p_part.length && match == -1; i++) {
            for (int j = 0; j < p_part.length && p_data[i + j] == p_part[j]; j++) {
                if (j == p_part.length - 1) {
                    match = i;
                }
            }
        }
        return match;
    }

    private int _Read(byte[] buf) throws Exception {
        int bytes = -1;
        if (_isUdp) {
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try {
                _dgs.receive(dp);
                bytes = dp.getLength();
            } catch (SocketTimeoutException ste) {
                bytes = -1;
            }
        } else {
            try {
                bytes = _is.read(buf);
            } catch (SocketTimeoutException ste) {
                // ste.printStackTrace();
                bytes = -1;
            }

        }
        //log.println("_read() - " + bytes + " bytes");
        return bytes;
    }

    private void _Send(DatagramPacket dp) throws Exception {
        if (_isUdp) {
            _dgs.send(dp);
        } else {
            _os.write(dp.getData());
            _os.flush();
        }
        //log.println("_send(DP) - " + dp.getData().length + " bytes");
    }

    private void _Send(byte[] msg) throws Exception {
        if (_isUdp) {
            DatagramPacket dp = new DatagramPacket(msg, msg.length,
                    _host, _port);
            _dgs.send(dp);
        } else {
            _os.write(msg);
            _os.flush();
            // log.println( format( "Wrote [%_sock] over TCP", msg ));
        }
        //log.println("_send() - " + msg.length + " bytes");
    }

    private boolean _Open() {
        if (_isConnected) {
            __log.println("[NetworkSRV1Reader] - open() called when already connected");
        }

        if ("UDP".equalsIgnoreCase(_transport)) {
            // set up UDP socket
            try {
                _dgs = new DatagramSocket(SRV1Test.UDP_LOCAL_PORT);
                _dgs.setSoTimeout(SRV1Test.SO_TIMEOUT);
                _dgs.connect(_host, _port);
                __log.println("[NetworkSRV1Reader] - listening on UDP port: " + SRV1Test.UDP_LOCAL_PORT);
                _isUdp = true;
                _isConnected = true;

                //_send(new byte[] { 'a' });

                return true;
            } catch (Exception e) {
                __log.println("[NetworkSRV1Reader] - error during UDP open() " + e);
                e.printStackTrace();
            }
        } else {
            // default to TCP-mode
            try {
                _sock = new Socket(_host, _port);
                _sock.setSoTimeout(SRV1Test.SO_TIMEOUT);
                _sock.setKeepAlive(true);
                _sock.setTcpNoDelay(true);
                _is = new BufferedInputStream(_sock.getInputStream());
                _os = new BufferedOutputStream(_sock.getOutputStream());
                __log.println("[NetworkSRV1Reader] - created TCP connection");
                _isConnected = true;
                return true;
            } catch (Exception e) {
                __log.println("[NetworkSRV1Reader] - error during TCP open() " + e);
                e.printStackTrace();
            }

        }
        _Close(); // clean up (possibly partially established connections)
        return false;
    }

    private boolean _Close() {
        if (_dgs != null) {
            try {
                _dgs.close();
            } catch (Exception e) {
            }
        }
        if (_is != null) {
            try {
                _is.close();
            } catch (Exception e) {
            }
        }
        if (_os != null) {
            try {
                _os.close();
            } catch (Exception e) {
            }
        }
        if (_sock != null) {
            try {
                _sock.close();
            } catch (Exception e) {
            }
        }
        _sock = null;
        _dgs = null;
        _is = null;
        _os = null;
        _isConnected = false;
        return true;
    }
}
