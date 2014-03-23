package com.intraffic.itrouting.core;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author intraffic
 */
public interface RMIItrouting2 extends Remote {
    /**
     * Given a LINK_ID (key), cost (new_cost) and timestamp (timestamp_str), update the cost with the new one.
     * @param key Must be a LINK_ID
     * @param new_cost double
     * @param timestamp_str SQL timestamp
     */
    public void updateLinkCost(String key, Double new_cost, String timestamp_str) throws RemoteException;
}
