package com.intraffic.itrouting.core.impl;
import com.intraffic.utils.hibernateUtils.HibernateSession;
import org.hibernate.classic.Session;
import com.intraffic.itrouting.core.Neo4jGraph;
import com.intraffic.itrouting.core.impl.Neo4jGraphImpl;
import com.intraffic.utils.Entity.Preferences.Phase1Element;
import java.util.HashMap;
import org.hibernate.Query;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import com.intraffic.utils.Entity.Pair;
import com.intraffic.utils.SkylineLib.Skyline;


public class PreferencesImpl extends HibernateSession implements com.intraffic.itrouting.core.Preferences {
	private Session session;
	private Neo4jGraph graph;
	private String EPS_PHASE1;
	private String EPS_PHASE2;//"0.001";

	/**
	* Implements the phase 1 of the algorithm described in "Goal Directed Relative Skyline Queries in Time Dependent Road Networks"
	* @param position		lat,log of the user position
	* @param targetPOItype	type of POI requested by the user
	*/
	private LinkedList<Phase1Element> phase1(String position, String targetPOItype) {
		int id;
		double lat, lon;
		String name, type;

		Object[] result_list;
        HashMap<String,Integer> index = new HashMap<String,Integer>();
        Query hib_query;
        List result = null;

        LinkedList<Phase1Element> candidates = new LinkedList<Phase1Element>();

        index.put("id",0);
        index.put("lat",1);
        index.put("lon",2);
        index.put("name",3);
        index.put("type",4);

        String query = " " +
			"with candidates as (  "+
			"	select id,lat,lon, trim(upper(translate(upper(\"name\"),'ÁÉÍÓÚÑ.-Ü&/()%''\"','AEIOUN  U       '))) as \"name\", \"type\" "+
			"	from itrouting_new_info.pois "+
			"	where "+
			"		ST_Dwithin(ST_geomFromText('POINT("+position.replace(',',' ')+")',4326),the_geom,"+this.EPS_PHASE1+") "+
			") "+
			"select * "+
			"from candidates "+
			"where "+
			"	(\"name\" like '%"+targetPOItype+"%' or "+ 
			"	\"type\" like '%"+targetPOItype+"%' )";

		try {
			System.out.println(query);
			hib_query = this.session.createSQLQuery(query);
            result = hib_query.list();
            for(Object res : result) {
            	result_list = (Object[]) res;
            	Phase1Element e = new Phase1Element(
            		(Integer) result_list[index.get("id")],
	            	(Double) result_list[index.get("lat")],
	            	(Double) result_list[index.get("lon")],
	            	(String) result_list[index.get("name")],
	            	(String) result_list[index.get("type")]
            		);
            	candidates.add(e);
            }
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return candidates;
	}

	/**
	* Implements the phase 2 of the algorithm cited in the comment of phase 1
	* @param candididates			candidates list obtained for phase1
	* @param restrictionPOItype		additional information provide by the user for calculating the skyline
	* @param costType				cost type to use during pathfinder operations
	*/
	private HashMap<String,Double[]> phase2(LinkedList<Phase1Element> candidates, String[] restrictionPOItype,String costType) {
		Object[] result_list;
        HashMap<String,Integer> index = new HashMap<String,Integer>();
        Query hib_query;
        List result = null;
        double x1, y1;
        int poi_id;

        HashMap<String,Double[]> candidates_skyline = new HashMap<String,Double[]>();

		for(Phase1Element candidate : candidates) {
			String qpart1 = "";
			String qpart2 = "";
			String qpart3 = "";
			String qpart4 = "";
			for(int i=0; i < restrictionPOItype.length; i++) {
				qpart1 += "\"name\" like '%"+restrictionPOItype[i]+"%' or \"type\" like '%"+restrictionPOItype[i]+"%' or";
				qpart2 += "b"+i+".id||','||b"+i+".lat||','||b"+i+".lon as colum"+i+",";
				qpart3 += i < restrictionPOItype.length - 1 ? "bar as b"+i+"," : "bar as b"+i;
				qpart4 += "(b"+i+".\"name\" like '%"+restrictionPOItype[i]+"%' or b"+i+".\"type\" like '%"+restrictionPOItype[i]+"%') and";
			}

			String query =  "with bar as ( "+
							"	with candidates as ( "+
							"		select id,lat,lon, trim(upper(translate(upper(\"name\"),'ÁÉÍÓÚÑ.-Ü&/()%''\"','AEIOUN  U       '))) as \"name\", \"type\" "+
							"		from itrouting_new_info.pois "+
							"		where "+
							"			ST_Dwithin(ST_geomFromText('POINT("+candidate.lat+" "+candidate.lon+")',4326),the_geom,"+EPS_PHASE2+") "+
							"	) "+
							"	select * "+
							"	from candidates "+
							"	where "+
							"		"+ qpart1 +
							"		false "+
							"	 "+
							" )  "+
							" select "+
							"    " + qpart2 +
							"     cast(0 as text) as x "+
							"from "+
							"    " + qpart3 +
							" where "+
							"    "+ qpart4 +
							"    true";

			try {
				hib_query = this.session.createSQLQuery(query);
	            result = hib_query.list();
	            for(Object res : result) {
	            	result_list = (Object[]) res;
	            	Double[] candidate_skyline = new Double[result_list.length - 1];
	            	String key = Integer.toString(candidate.id);
	            	int i = 0;
	            	for(Object column : result_list) {
	            		String point = (String) column;
	            		if(!point.trim().equals("0")) {
		            		poi_id = Integer.parseInt(point.split(",")[0]);
		            		x1 = Double.parseDouble(point.split(",")[1]);
		            		y1 = Double.parseDouble(point.split(",")[2]);
		            		candidate_skyline[i] = this.getDistance(x1,y1,candidate.lat,candidate.lon);
		            		key += "_" + poi_id;
	            		}
	            		i++;
	            	}

	            	candidates_skyline.put(key,candidate_skyline);
	            }

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return candidates_skyline;
	}

	/**
	* Returns the distance of the route between p(x1,y1) and p(x2,y2)
	* @param x1	longitude of position 1
	* @param y1 latitude of position 1
	* @param x2 longitude of position 2
	* @param y2 latitude of position 2
	*/
	public double getDistance(double x1, double y1,double x2, double y2) {
		Pair<ArrayList<String>,Double> relative_path = null;
		String[]  params = {"ftt","nome","0"};
		String[] azimuth = {"0","180"};
		double dist = 0.0;
		for(int i = 0; i<2; i++) {
			for(int j = 0; j<2; j++) {
				relative_path = this.graph.getPath(
                    this.graph.coorToNode(x1,y1,azimuth[i]), 
                    this.graph.coorToNode(x2,y2,azimuth[j]), 
                    params);
				if(relative_path != null) 
					break;
			}
		}
		if(relative_path != null)
			dist = relative_path.getValue();
		
		if(dist == 0.0)
			dist = Math.sqrt(Math.pow(x1 - x2,2) + Math.pow(y1 - y2,2)) /  0.000007078;
		return dist;
	}
	
	/**
	* Implements the whole algorithm described in "Goal Directed Relative Skyline Queries in Time Dependent Road Networks"
	* @param position 				geospatial position of the user
	* @param targetPOItype			the pois that the user wants to find
	* @param restrictionPOItype		the pois that the user specified as the restrictions
	* @param k 						limit or top which restrict the skyline result
	* @param costType				cost type to use during pathfinder operations
	* @param graph     				the DB graph over which the skyline algorithm will be applied
	*/
	public HashMap topKSkyline(String position, String targetPOItype, String[] restrictionPOItype, int k, String costType, String targetradius, String restradius, Neo4jGraph graph) {	
		Skyline sky = new Skyline();
		this.session = HibernateSession.getSessionFactory().openSession();
		this.graph = graph;
		this.EPS_PHASE1 = targetradius;
		this.EPS_PHASE2 = restradius;
		try {
			//Phase 1
			LinkedList<Phase1Element> candidates = this.phase1(position,targetPOItype);
			System.out.println("phase 1: length: "+candidates.size());

			//Phase 2
			HashMap<String,Double[]> candidates_skyline = this.phase2(candidates,restrictionPOItype,costType);
			for(String key : candidates_skyline.keySet()) {
				Double[] csky_values = candidates_skyline.get(key);
				System.out.print(key);
				for(int i = 0; i < csky_values.length; i++) {
					System.out.print(" "+csky_values[i]);
				}
				System.out.println("");
			}

			// Phase 3
			LinkedList<String> skylineResult = sky.getSkyline(candidates_skyline);
			return this.buildJSON(skylineResult);
		} catch (Exception e){
			e.printStackTrace();
		} finally {
			this.session.close();
		}
		return null;
	}

	/**
	* Build the JSON object to be returned by the endpoint
	* @param skylines 	skyline set obtained by the algorithm described below
	*/
	private HashMap buildJSON(LinkedList<String> skylines) {
		HashMap result = new HashMap();
		LinkedList<HashMap> skylines_json = new LinkedList<HashMap>();
		for(String skyline : skylines) {
			//this will be the new skyline entry
			HashMap skyline_entry = new HashMap();
			String[] ids = skyline.split("_");
			int skyline_id = Integer.parseInt(ids[0]);

			//store the skyline
			skyline_entry.put("skyline",this.graph.getSerializeNodeInfo(skyline_id,"poi"));

			//store the restrictions
			LinkedList<HashMap> restrictions = new LinkedList<HashMap>();
			for(int i = 1; i < ids.length; i++) {
				restrictions.add(this.graph.getSerializeNodeInfo(Integer.parseInt(ids[i]),"poi"));
			}
			skyline_entry.put("restrictions",restrictions);

			//store the entry
			skylines_json.add(skyline_entry);
		}
		result.put("skylines",skylines_json);

		return result;
	}
}