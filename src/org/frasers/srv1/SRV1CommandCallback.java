/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.frasers.srv1;

/**
 *
 * @author sfraser
 */
public interface SRV1CommandCallback
{
    public void success(String cmdString, String response);
    public void failure(String cmdString);
}
