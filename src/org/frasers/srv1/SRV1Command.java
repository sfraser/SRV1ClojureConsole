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
    private final String cmdString;
    private final byte[] cmdBytes;
    private final SRV1CommandCallback callback;

    public SRV1Command(String cmdString, byte[] cmdBytes, SRV1CommandCallback callback) {
        this.cmdString = cmdString;
        this.cmdBytes = cmdBytes;
        this.callback = callback;
    }

    public String getString() { return cmdString; }
    public byte[] getBytes() { return cmdBytes; }
    public SRV1CommandCallback getCallback() { return callback; }
}
