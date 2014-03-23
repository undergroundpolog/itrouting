package com.intraffic.utils.propManager;

import java.util.Properties;

/**
 *
 * @author intraffic
 */
public class PropManager {
    Properties properties;
    
    /**
     * Constuctor Set the properties file.
     */
    public PropManager() {
        this.properties = new Properties();
        try {
            //load properties from the class path
            this.properties.load(this.getClass().getClassLoader().getResourceAsStream("itrouting2.properties"));

        } catch (Exception ex) {
         ex.printStackTrace();
       }
    }
    
    /**
     * Returns the property associated with the given key.
     * @param key propertie configured in the itrouting2.properties file
     * @return value
     */
    public String getProperty(String key) {
        try {
            return this.properties.getProperty(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
