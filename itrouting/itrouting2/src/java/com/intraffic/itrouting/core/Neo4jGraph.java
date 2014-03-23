package com.intraffic.itrouting.core;

import java.util.ArrayList;


/**
 * 
 * @author intraffic
 */
public interface Neo4jGraph { 
    
    
    /**
    * Given a LINK_ID (key), cost (new_cost) and timestamp (timestamp_str), update the cost with the new one.
    * @param key Must be a LINK_ID
    * @param new_cost double
    * @param timestamp_str SQL timestamp
    */
    public void updateCost(String key, Double new_cost, String timestamp_str);
    
    /**
     * Create the RMI instance used for cost updating.
     */
    public void createRMIInstance();
    
    /**
     * Load all data in memory.
     */
    public void loadAllData();
    
    /**
     * Create a graph database.
     */
    public void createDb();
    
    
    /**
     * Prepare two Instances of the A* algorithm, one for FTV cost (normal cost) and the other one for real time cost.
     */
    public void prepareAstar();
    
    /**
     * Map the given point with a graph node.
     * @param x long
     * @param y lat
     * @param azimuth azimuth
     * @return LINK_ID
     */
    public String coorToNode(double x, double y, String azimuth);
    
    /**
     * Map the given point with a graph node.
     * @param x long
     * @param y lat
     * @param azimuth azimuth
     * @return LINK_ID
     */
    public String coorToNode(String x, String y, String azimuth);
    
    /**
     * Map the given point with a graph node.
     * @param point
     * @return LINK_ID
     */
    public String coorToNode(String point);
    
   
    /**
     * Given a source and a target LINK_ID, perform A* and find the best path.
     * @param source_id Must be a LINK_ID
     * @param target_id Must be a LINK_ID
     * @param cost type cost. Must be rt, length or normal
     * @return ArrayList of LINK_IDs
     */
    public ArrayList<String> getPath(String source_id, String target_id, String[] params);

    /**
     * Shut down the spatial database.
     */
    public void shutDown();
    
}
