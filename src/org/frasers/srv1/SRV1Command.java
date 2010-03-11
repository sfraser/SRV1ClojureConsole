/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.frasers.srv1;

/**
 *
 * @author sfraser
 */
class SRV1Command
{
    private final String _cmdString;
    private final byte[] _cmdBytes;
    private final SRV1CommandCallback _callback;

    public SRV1Command(String cmdString, byte[] cmdBytes, SRV1CommandCallback callback) {
        _cmdString = cmdString;
        _cmdBytes = cmdBytes;
        _callback = callback;
    }

    public String getString() { return _cmdString; }
    public byte[] getBytes() { return _cmdBytes; }
    public SRV1CommandCallback getCallback() { return _callback; }
}
