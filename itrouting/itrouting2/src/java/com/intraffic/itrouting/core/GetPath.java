package com.intraffic.itrouting.core;

import com.opensymphony.xwork2.ActionSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import com.intraffic.utils.Verification.Patterns;
import com.intraffic.utils.Entity.Pair;

/**
 *
 * @author intraffic
 */
public class GetPath extends ActionSupport {
    private String check_status, source_node, target_node, source, target, node, cost, dir, timestamp, format, preference, theta, midpoints, k, restriction, targetradius, restradius;
    private double x1, y1, x2, y2;
    private HashMap path;
    private static Neo4jGraph graph;
    private final int LON = 0;
    private final int LAT = 1;
    private final int AZI = 2;
    private final int CUS_AZI = 3;
    
    private static String[][][] azi_options;
    private static HashMap<String,String> keys;
        
    /**
     * Constructor.
     */
    public GetPath() {
        this.azi_options = new String[2][4][2];
        azi_options[0][0][0] = "0"; azi_options[0][0][1] = "0";
        azi_options[0][1][0] = "0"; azi_options[0][1][1] = "180";
        azi_options[0][2][0] = "180"; azi_options[0][2][1] = "0";
        azi_options[0][3][0] = "180"; azi_options[0][3][1] = "180";

        azi_options[1][0][0] = "90"; azi_options[1][0][1] = "90"; 
        azi_options[1][1][0] = "90"; azi_options[1][1][1] = "270"; 
        azi_options[1][2][0] = "270"; azi_options[1][2][1] = "90"; 
        azi_options[1][3][0] = "270"; azi_options[1][3][1] = "270";

        this.keys = new HashMap<String,String>();
        keys.put("0","0,0");
        keys.put("180","0,3");
        keys.put("90","1,0");
        keys.put("270","1,3");
    }
    
    /**
     * Set the midpoints param 
     * @param midpoints a set of points in the format 'lon,lat[,azimuth]' delimited by '|'
     */
    public void setMidpoints(String midpoints) {this.midpoints = midpoints;}
    
    
    /**
     * Set the theta param 
     * @param theta if given, must be a value between 0 and 1, including both
     */
    public void setTheta(String theta) {this.theta = theta;}
    
    /**
     * Set the preference
     * @param preference if given, the value must be 'highway' or 'secondary' 
     */
    public void setPreference(String preference) {this.preference = preference;}
    
    /**
     * Set the source point of the path.
     * @param source Must be a point in the format 'lot,lan[,azimuth]'
     */
    public void setSource(String source) {this.source = source;}
    
    /**
     * Set the response format
     * @param format Must be 'json', 'geojson' or none
     */
    public void setFormat(String format) {this.format = format;}
    
    /**
     * Set the target point of the path.
     * @param target Must be a point in the format 'lot,lan[,azimuth]'
     */
    public void setTarget(String target) {this.target = target;}
    
    /**
     * Set the cost type.
     * @param cost Use 'rt' for real time cost or 'normal' for normal cost
     */
    public void setCost(String cost) {this.cost = cost;}
    
    /**
     * Set the LINK_ID that will be updated.
     * @param node Must be a LINK_ID
     */
    public void setNode(String node) {this.node = node;}
    
    /**
     * Set the link direction.
     * @param dir Must be 'P' for prescribed, otherwise 'C'
     */
    public void setDir(String dir) {this.dir = dir;}
    
    /**
     * Set the timestamp of the speed that will be used to set the confidence.
     * @param timestamp SQL timestamp format
     */
    public void setTimestamp(String timestamp) {this.timestamp = timestamp;}

    /**
     * Set the POI types to be consider in the preference 
     * @param sequence of POI types
     */
    public void setRestriction(String restriction) {this.restriction = restriction;}

    /**
     * Set the POI types to be consider in the preference 
     * @param sequence of POI types
     */
    public void setTargetradius(String targetradius) {this.targetradius = targetradius;}

    /**
     * Set the POI types to be consider in the preference 
     * @param sequence of POI types
     */
    public void setRestradius(String restradius) {this.restradius = restradius;}

    /**
     * number of preferences to be returned
     * @param k integer
     */
    public void setK(String k) {this.k = k;}

    /**
     * Build the json or geojson of the path
     * @param result_path path
     */
    private void prepareResponse(ArrayList<String> result_path) {
        String[] elem, points;
        String linestring;
        this.format = (format == null ? "json" : this.format);
        if(this.format.equals("geojson")) {

            Vector<HashMap> features = new Vector<HashMap>();
            HashMap<String,Object> featureColl = new HashMap<String, Object>();


            for(String elem_s : result_path) {
                //coordinate
                Vector<Vector> coordinates = new Vector<Vector>();

                elem = elem_s.trim().split(",");
                linestring = elem[3];
                points = linestring.split("\\|");
                for(String p : points) {
                    Vector<Double> point = new Vector<Double>();
                    point.add(Double.parseDouble(p.split(" ")[0]));
                    point.add(Double.parseDouble(p.split(" ")[1]));
                    coordinates.add(point);
                }

                //geometry
                HashMap<String,Object> geometry = new HashMap<String, Object>();
                geometry.put("type","LineString");
                geometry.put("coordinates",coordinates);

                //features_hash (contiene info de cada link)
                HashMap<String,Object> feature_hash = new HashMap<String, Object>();
                feature_hash.put("type", "Feature");
                feature_hash.put("geometry", geometry);
                feature_hash.put("properties",new HashMap<String,Object>());
                //features               
                features.add(feature_hash);    
            }

            //featurecollection
            featureColl.put("type", "FeatureCollection");
            featureColl.put("features", features);

            this.path = featureColl;

        } else {
            Vector<HashMap<String,Object>> json_path = new Vector<HashMap<String,Object>>();
            //Vector<Integer> values = new Vector<Integer>();
            for(String elem_s : result_path) {
                //System.out.println("ITR2: "+elem_s);
                HashMap<String,Object> link_info = new HashMap<String,Object>();
                elem = elem_s.split(",");
                link_info.put("id",Integer.parseInt(elem[1]));
                //values.add(Integer.parseInt(elem[1]));
                link_info.put("direction",Boolean.parseBoolean(elem[4]));
                json_path.add(link_info);

            }

            this.path.put("path", json_path);
            //this.path.put("path", values);
        }

        result_path.clear();
    }
    
    /**
     * Prepare the points that will be used to find the path, The preparation include set the default azimuth if not given for some points.
     */
    private String[][] preparePoints() {
        String all_points = this.source + "|" + (this.midpoints == null ? "" : this.midpoints + "|") + this.target;
        System.out.println(all_points);
        String[] all_points_arr = all_points.split("\\|");
        String[][] allPoints = new String[all_points_arr.length][4];
        String[] A_pointArr, B_pointArr;
        double Ay, Ax, By, Bx;
        int Atam, Btam;
        for(int i = 0; i < all_points_arr.length - 1; i++) {
            A_pointArr = all_points_arr[i].split(",");
            B_pointArr = all_points_arr[i+1].split(",");
            Atam = A_pointArr.length;
            Btam = B_pointArr.length;
            Ax = Double.parseDouble(A_pointArr[this.LON]);
            Ay = Double.parseDouble(A_pointArr[this.LAT]);
            Bx = Double.parseDouble(B_pointArr[this.LON]);
            By = Double.parseDouble(B_pointArr[this.LAT]);
            
            //lat lon
            //A
            allPoints[i][this.LON] = A_pointArr[this.LON];
            allPoints[i][this.LAT] = A_pointArr[this.LAT];
            allPoints[i][this.CUS_AZI] = Atam == 3 ? "true" : "false";
            
            //B
            allPoints[i+1][this.LON] = B_pointArr[this.LON];
            allPoints[i+1][this.LAT] = B_pointArr[this.LAT];
            allPoints[i+1][this.CUS_AZI] = Btam == 3 ? "true" : "false";
            
            
            //azimuth
            if(Ay > By) {
                if(i == 0)
                    allPoints[i][this.AZI] = (Atam  == 3 ? A_pointArr[this.AZI] : "180");
                allPoints[i+1][this.AZI] = (Btam  == 3 ? B_pointArr[this.AZI] : "180");
            } else if(Ay < By) {
                if(i == 0)
                    allPoints[i][this.AZI] = (Atam  == 3 ? A_pointArr[this.AZI] : "0");
                allPoints[i+1][this.AZI] = (Btam  == 3 ? B_pointArr[this.AZI] : "0");
            } else {
                if(Ax < Bx) {
                    if(i == 0)
                        allPoints[i][this.AZI] = (Atam  == 3 ? A_pointArr[this.AZI] : "90");
                    allPoints[i+1][this.AZI] = (Btam  == 3 ? B_pointArr[this.AZI] : "90");
                } else {
                    if(i == 0)
                        allPoints[i][this.AZI] = (Atam  == 3 ? A_pointArr[this.AZI] : "270");
                    allPoints[i+1][this.AZI] = (Btam  == 3 ? B_pointArr[this.AZI] : "270");
                }
            }
            
            System.out.println(allPoints[i][this.LON]+","+allPoints[i][this.LAT]+","+allPoints[i][this.AZI]+" - "+allPoints[i+1][this.LON]+","+allPoints[i+1][this.LAT]+","+allPoints[i+1][this.AZI]);
        }
        
        return allPoints;
        
    }
    
    /**
     * Check the inputs params.
     * @return 
     */
    private String checkParams(String endpoint) {
        Patterns patt = new Patterns();
        //source
        if(endpoint.equals("getPath")) {
            if(!patt.isValidPoint(source)) 
                return "El punto inicial debe ser tener el formato 'lon,lat[,azimuth]', donde 'lon','lat' y 'azimuth' pueden ser enteros o flotantes";
            
            if(!patt.isValidPoint(target)) 
                return "El punto final debe ser tener el formato 'lon,lat[,azimuth]', donde 'lon','lat' y 'azimuth' pueden ser enteros o flotantes";
            
            if(!patt.isValidCost(cost)) 
                return "El costo debe ser 'length', 'ftt' o 'rt'";
            
            if(!patt.isValidFormat(format)) 
                return "El formato debe ser 'json' o 'geojson'";
            
            if(!patt.isValidPreference(preference)) 
                return "La preferencia debe ser 'highway' o 'secondary'";
            
            if(!patt.isValidMidpoints(midpoints)) 
                return "Los puntos deben tener el formato 'lon,lat[,azimuth]'. Cada punto debe estar delimitado por un '|'";
        } else {
            return null;
        }     
        return null;
    }
    
    /**
     * Given a source, target and a cost type, perform a A* and return the best path, The result is a JSON that has nodes, links and costs.
     * @throws Exception 
     */
    @Override
    public String execute() throws Exception {
        if((check_status = this.checkParams("getPath")) != null) {
            this.path = new HashMap();
            this.path.put("name","Internal Error");
            this.path.put("message", check_status);
            return ERROR;
        }

        if(this.graph == null) {
            this.initAll();
        }
        
        ArrayList<String> result_path, relative_path;
        String [][] allPoints;
        try {
            this.path = new HashMap();
            
            //get path
            String[]  params = {
                (cost == null ? "ftt" : cost).trim(),
                (preference == null ? "none" : preference).trim(),
                (theta == null ? "0" : theta).trim()
            };
            
            allPoints = this.preparePoints();
            result_path = new ArrayList<String>();
            
            relative_path = null;
            int count, mode;
            
            
            for(int i = 0; i < allPoints.length - 1; i++) {
                mode = keys.containsKey(allPoints[i][this.AZI]) ? Integer.parseInt(keys.get(allPoints[i][this.AZI]).split(",")[0]) : 0;
                count = keys.containsKey(allPoints[i][this.AZI]) ? Integer.parseInt(keys.get(allPoints[i][this.AZI]).split(",")[1]) : 0;
                for(int j = 0; j < 4; j++) {
                    System.out.println("Intentando buscar camino entre "+allPoints[i][this.LON]+","+allPoints[i][this.LAT]+","+allPoints[i][this.AZI]+","+allPoints[i][this.CUS_AZI]+" y "+allPoints[i+1][this.LON]+","+allPoints[i+1][this.LAT]+","+allPoints[i+1][this.AZI]+","+allPoints[i+1][this.CUS_AZI]);
                    relative_path = this.graph.getPath(
                            this.graph.coorToNode(allPoints[i][this.LON],allPoints[i][this.LAT],allPoints[i][this.AZI]), 
                            this.graph.coorToNode(allPoints[i+1][this.LON],allPoints[i+1][this.LAT],allPoints[i+1][this.AZI]), 
                            params).getKey();
                    
                    if(relative_path == null) {
                        count++;
                        count = count % 4;
                        System.out.println("No hay camino para"+allPoints[i][this.LON]+","+allPoints[i][this.LAT]+","+allPoints[i][this.AZI]+" y "+allPoints[i+1][this.LON]+","+allPoints[i+1][this.LAT]+","+allPoints[i+1][this.AZI]+". Recalculando nuevos azimuth");
                        allPoints[i][this.AZI] = allPoints[i][this.CUS_AZI].equals("true") ? allPoints[i][this.AZI] : azi_options[mode][count][0];
                        allPoints[i+1][this.AZI] = allPoints[i+1][this.CUS_AZI].equals("true") ? allPoints[i+1][this.AZI] : azi_options[mode][count][1];
                        System.out.println(allPoints[i][this.AZI]+" - "+allPoints[i+1][this.AZI]);
                        
                    } else {
                        System.out.println("Se encontro un camino");
                        break;
                    }
                    
                }
                
                if(i != 0 ) 
                    relative_path.remove(0);
                result_path.addAll(relative_path);
            }
            
            this.prepareResponse(result_path);
            
            //shutDown graph database
            //this.graph.shutDown();
            
        } catch (Exception alle) {
          alle.printStackTrace();
        }
        
        return SUCCESS;
    }
    
    /**
     * Used for Struts2 to get the path as a JSON
     * @return JSON o GEOJSON
     */    
    public HashMap getPath() {
        return this.path;
    }
    
     /**
     * Create graph and other stuff related with the spatial database.
     */
    public void initAll() {
        this.graph = new com.intraffic.itrouting.core.impl.Neo4jGraphImpl();
        this.graph.createDb();
        this.graph.prepareAstar();
        this.graph.createRMIInstance();
        //this.graph.loadAllData();
        return;
    }
    
    /**
     * Update a link cost given the ID, direction and timestamp, The timestamp is used to set the speed confidence.
     */
    public void updateCost() {
        try {
            this.graph.updateCost(
                    (dir.trim().equals("C") ? node : Integer.toString(Integer.parseInt(node) - 1000000000)),
                    Double.parseDouble(cost),
                    timestamp
                    );
        } catch (Exception e){
            System.out.println("ITR2: Error actualizando costo. "+e.toString());
            e.printStackTrace();
        
        }
        
    }

    /**
    * Given a position, a preference POI type and a list of restrictions POI type, returns THE skyline
    */
    public String getPreference() {
        if((check_status = this.checkParams("getPreference")) != null) {
            this.path = new HashMap();
            this.path.put("name","Internal Error");
            this.path.put("message", check_status);
            return ERROR;
        }

        Preferences p = new com.intraffic.itrouting.core.impl.PreferencesImpl();
        HashMap tks = p.topKSkyline(
            this.source, 
            this.target, 
            this.restriction.split(","), 
            this.k == null ? -1 : Integer.parseInt(this.k), 
            this.cost, 
            this.targetradius == null ? "0.002" : this.targetradius,
            this.restradius == null ? "0.001" : this.restradius,
            this.graph);

        this.path = tks;        
        return SUCCESS;
    }
}
