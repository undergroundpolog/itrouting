/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intraffic.utils.Verification;

/**
 *
 * @author intraffic
 */
public class Patterns {
    public boolean isValidPoint(String point) {
        if(point != null) {
            return point.trim().matches("^-?[0-9]*\\.?[0-9]+,-?[0-9]*\\.?[0-9]+|-?[0-9]*\\.?[0-9]+,-?[0-9]*\\.?[0-9]+,[0-9]*\\.?[0-9]+$");
        }
        return true;
    }
    
    public boolean isValidCost(String cost) {
        if(cost != null) {
            return cost.trim().matches("length|ftt|rt");
        }
        return true;
    }
    
    public boolean isValidFormat(String format) {
        if(format != null) {
            return format.trim().matches("json|geojson");
        }
        return true;
    }
    
    public boolean isValidPreference(String preference) {
        if(preference != null) {
            return preference.trim().matches("highway|secondary");
        }
        return true;
    }
    
    public boolean isValidMidpoints(String midpoints) {
        if(midpoints != null) {
            return midpoints.trim().matches("^(-?[0-9]*\\.?[0-9]+,-?[0-9]*\\.?[0-9]+|-?[0-9]*\\.?[0-9]+,-?[0-9]*\\.?[0-9]+,[0-9]*\\.?[0-9]+)(\\|(-?[0-9]*\\.?[0-9]+,-?[0-9]*\\.?[0-9]+|-?[0-9]*\\.?[0-9]+,-?[0-9]*\\.?[0-9]+,[0-9]*\\.?[0-9]+))*$");
        }
        return true;
    }
}
