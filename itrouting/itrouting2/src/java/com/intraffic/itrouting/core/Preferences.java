package com.intraffic.itrouting.core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import com.intraffic.utils.Entity.Preferences.Phase1Element;


/**
 * 
 * @author intraffic
 */
public interface Preferences { 
    
    public HashMap topKSkyline(String position, String targetPOItype, String[] restrictionPOItype, int k, String costType, String targetradius, String restradius, Neo4jGraph graph);
    
}
