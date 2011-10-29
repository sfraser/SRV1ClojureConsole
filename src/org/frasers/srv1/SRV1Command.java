/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.frasers.srv1;

/**
 * @author sfraser
 */
class SRV1Command {
    public final String CMD_STRING;
    public final byte[] CMD_BYTES;
    public final SRV1CommandCallback CMD_CALLBACK;

    public SRV1Command(String cmdString, byte[] cmdBytes, SRV1CommandCallback callback) {
        CMD_STRING = cmdString;
        CMD_BYTES = cmdBytes;
        CMD_CALLBACK = callback;
    }

}
