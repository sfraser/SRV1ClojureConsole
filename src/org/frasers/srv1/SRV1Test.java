/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.frasers.srv1;

/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *  SRV1Test.java - TCP/UDP test console for SRV-1 robot
 *    Copyright (C) 2005-2008  Surveyor Corporation
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details (www.gnu.org/licenses)
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public class SRV1Test
{
	public static String SRV_HOST = "169.254.0.10";
	public static int SRV_PORT = 10001;
	public static String SRV_PROTOCOL = "TCP"; // "UDP" or "TCP"
	static int UDP_LOCAL_PORT = 10001; // the SRV-1 must be set to use this as its "Remote Port"

	private static String ARCHIVE_PREFIX = "srv";

	static final int SO_TIMEOUT = 2000;  // socket timeout (ms)
	static final int MTU = 2048; // must be >= SRV-1 WiPort MTU (which has a default of 1400)

    public static final String CMD_DELIM = ",";  // used to send multiple commands in one shot
	public static final String ENC_ASCII = "ASCII";
	public static final String ENC_HEX = "Hex";

	public static void main(final String[] cmdLine) {
		final Map args = _ParseCommandLine(cmdLine);
		if (args.containsKey("usage") || args.containsKey("help")) {
			System.out.println("Command Line Options:");
			System.out.println("  -remote_addr : SRV-1 IP Address");
		    System.out.println("  -remote_port : SRV-1 Port");
            System.out.println("  -protocol    : TCP or UDP");
            System.out.println("  -local_port  : Local port (applicable to UDP only)");
            return;
        }
        if (args.containsKey("remote_addr"))    SRV_HOST = (String) args.get("remote_addr");
        if (args.containsKey("remote_port")) SRV_PORT = _ToInt((String) args.get("remote_port"), 10001);
        if (args.containsKey("protocol")) SRV_PROTOCOL = ((String) args.get("protocol")).toUpperCase();
        if (args.containsKey("local_port")) UDP_LOCAL_PORT = _ToInt((String) args.get("local_port"), 10001);

        final Frame f = new Frame("SRV-1 Console - TCP/UDP");

        final JpegRenderer jpegRender = new JpegRenderer(f);
        jpegRender.setSize(320, 240);
        
        f.setBackground(Color.WHITE);
        f.setLayout(new BorderLayout(3, 3));
        f.add("Center", jpegRender);

        final NetworkSRV1Reader srv1 = new NetworkSRV1Reader(SRV_HOST, SRV_PORT, SRV_PROTOCOL, jpegRender);
        srv1.start();

        f.add("South", _CreateBasicCommandPanel(srv1));
        f.pack();
        f.setVisible(true);
        f.repaint();
        // handle window close
        f.addWindowListener (new WindowAdapter() {
                public void windowClosing (WindowEvent evt) {
                    System.exit(0);
                }
            });
    }

    private static Map _ParseCommandLine(final String[] p_cmdLine)
    {
        final Map<String, Object> args = new HashMap<String,Object>();
        int count = p_cmdLine.length;
        for (int i = 0; i < count; i++) {
            if (p_cmdLine[i].startsWith("-")) {
                // if next arg. starts with dash, use "true" as the value, otherwise
                // use the string value that follows this arg.
                if (i+1 >= count || p_cmdLine[i+1].startsWith("-"))
                    args.put(p_cmdLine[i].substring(1), Boolean.TRUE);
                else if (i+1 < count)
                    args.put(p_cmdLine[i].substring(1), p_cmdLine[++i]);
            }
        }
        return args;
    }

    private static int _ToInt(final String p_sStringToParse, final int p_deflat)
    {
        int i;
        try { i = Integer.parseInt(p_sStringToParse); }
        catch (Exception e) { i = p_deflat; }
        return i;
    }

    // Create a GUI panel that offers basic support for sending of SRV-1 commands
    // and response display.
    private static Component _CreateBasicCommandPanel( final NetworkSRV1Reader p_srv1 )
    {
        final Choice encoding = new Choice();
        encoding.add(ENC_ASCII);
        encoding.add(ENC_HEX);

        final TextField commandField = new TextField(20);
        final Button sendButton = new Button("Send");
        final TextArea commandLog = new TextArea("", 5, 10);

        final ActionListener sendCommandAction = new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final String c = commandField.getText();
                    final String[] commands = c.split(CMD_DELIM);

                    for (String command : commands) {
                        final String cmdString = command.trim();
                        byte[] cmdBytes = _BuildCommand(cmdString, encoding.getSelectedItem());
                        final SRV1CommandCallback cb = new SRV1CommandCallback() {
                            public void success(String cmdString, String response) {
                                commandLog.append("[" + cmdString + "] " +
                                        response + System.getProperty("line.separator"));
                                try {
                                    commandLog.setCaretPosition(Integer.MAX_VALUE);
                                } catch (IllegalComponentStateException ise) {
                                }
                            }

                            public void failure(String cmdString) {
                            }
                        };

                        p_srv1.sendCommand(cmdString, cmdBytes, cb);
                    }
                }
            };

        sendButton.addActionListener(sendCommandAction);

        commandField.addKeyListener(new KeyListener() {
                public void keyTyped(KeyEvent e) { }
                public void keyPressed(KeyEvent e) { }
                public void keyReleased(KeyEvent e) {
                    // on enter key, simulate "Send" button click
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        sendCommandAction.actionPerformed(null); // FIXME: provide an ActionEvent?
                    }
                }
            });

        // encoding drop-down, command field & Send button on one line
        Panel pCmdLine = new Panel();
        pCmdLine.add(encoding);
        pCmdLine.add(commandField);
        pCmdLine.add(sendButton);

        Panel p = new Panel(new BorderLayout());
        p.add("North", pCmdLine);
        p.add("Center", commandLog);
        return p;
    }

    private static byte[] _BuildCommand( final String p_text,
                                        final String p_encoding)
    {
        try {
            if (ENC_HEX.equalsIgnoreCase(p_encoding)) {
                return (new java.math.BigInteger(p_text, 16)).toByteArray();
            } else {
                return p_text.getBytes("US-ASCII");
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Frame listener that stores each frame as a separate (timestamped) JPEG file in
     * the specified 'archiveDirectory'.
     */
    private static class SimpleFrameArchiver extends Thread implements FrameListener
    {
        private String archiveDirectory;
        private byte[] buf = null;

        public SimpleFrameArchiver(String archiveDirectory) {
            this.archiveDirectory = archiveDirectory.endsWith(File.separator) ?
                archiveDirectory : archiveDirectory + File.separator;
            File f = new File(this.archiveDirectory);
            if (f.exists() && !f.isDirectory()) {
                throw new IllegalArgumentException("'archiveDirectory' exists as a regular file");
            } else if (!f.exists()) {
                f.mkdir();
            }

            start();
        }

        public void run()
        {
            while (true) {
                if (buf == null) {
                    try { Thread.sleep(10); } catch (InterruptedException ie) { }
                    continue;
                }
                long tstamp = System.currentTimeMillis();
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(this.archiveDirectory +
                                               ARCHIVE_PREFIX + tstamp + ".jpeg");
                    fos.write(buf);
                    buf = null;
                    fos.flush();
                } catch (Exception e) {

                } finally {
                    try { fos.close(); } catch (Exception e) { }
                }
            }
        }
        public void newFrame(byte[] frame) {
            if (buf != null) return;  // drop frames if we fall behind
            buf = new byte[frame.length];
            System.arraycopy(frame, 0, buf, 0, frame.length);
        }
    }






}

