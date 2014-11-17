package com.intraffic.itrouting.core.impl;

import com.intraffic.utils.propManager.PropManager;

//import com.vividsolutions.jts.geom.Coordinate; 
import com.intraffic.utils.hibernateUtils.HibernateSession;
import java.io.BufferedReader;
import java.rmi.Naming;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.neo4j.kernel.impl.util.FileUtils;
import java.io.File;
import java.io.InputStreamReader;
//import java.io.BufferedReader;
//import java.io.FileReader;
import java.net.ServerSocket;
import java.net.Socket;
//import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.classic.Session;
//import org.neo4j.gis.spatial.SimplePointLayer;
//import org.neo4j.gis.spatial.SpatialDatabaseService;

/*import org.neo4j.graphalgo.CostEvaluator;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;*/

import com.intraffic.org.neo4j.graphalgo.CostEvaluator;
import com.intraffic.org.neo4j.graphalgo.GraphAlgoFactory2;
import com.intraffic.org.neo4j.graphalgo.PathFinder;
import com.intraffic.utils.Entity.Pair;



import org.neo4j.graphalgo.EstimateEvaluator;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.Traversal;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.ReadableIndex;

/**
 * 
 * @author intraffic
 */
public class Neo4jGraphImpl extends HibernateSession implements com.intraffic.itrouting.core.Neo4jGraph {
    
    //RMI
    RMIItrouting2Impl rmii2;
    
    //Neo4j stuff
    private static final String DB_PATH = "webapps/itrouting2/neo4j_target/neo4j-mygraph-db";
    String greeting;
    GraphDatabaseService graphDb;
    Session session;
    Index<Node> nodeIndex_way;
    Index<Node> nodeIndex_poi;
    Index<Node> nodeIndex_divter;
    ReadableIndex<Node>  autoNodeIndex;
    Node firstNode;
    Node secondNode;
    Relationship relationship;
    PathFinder<WeightedPath> astar;
    PathFinder<WeightedPath> astar_rt;
    
    HashMap<String,PathFinder<WeightedPath>> algos;
    
    final int NODES_BATCH = Integer.parseInt((new PropManager()).getProperty("upload_nodes_batch"));
    final int REL_BATCH = Integer.parseInt((new PropManager()).getProperty("upload_relations_batch"));
      
    //UpdateCostSocket stuff
    static updateCostService UpCS;
    
    //updateCost stuff
    final int HOWLONG = Integer.parseInt((new PropManager()).getProperty("eps"));
    
    //alpha stuff
    SimpleDateFormat simpleDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Enumerations of links relations.
     */
    private static enum RelTypes implements RelationshipType {
        MOVE_TO,
        NEAR_OF,
        IS_IN
    }
    
    /**
     * Gives the TCP service for cost updates.
     */
    private static class updateCostService extends Thread {
        LinkedList<String> lista;
        
        //Server
        ServerSocket server;
        GraphDatabaseService graphDb;
        Index<Node> nodeIndex;
        SimpleDateFormat simpleDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        //link_id, cost, dir, timestamp
        final int LINK_ID = 0;
        final int COST = 1;
        final int DIR = 2;
        final int TIMESTAMP = 3;
        final int HOWLONG = Integer.parseInt((new PropManager()).getProperty("eps"));
        String link_id, cost,dir, timestamp;
        
        /**
         * Constructor, Create the server socket in the given port.
         * @param graphDb Neo4j graph
         * @param nodeIndex Node indexs
         */
        public updateCostService(GraphDatabaseService graphDb, Index<Node> nodeIndex) {            
            this.graphDb = graphDb;
            this.nodeIndex = nodeIndex;
            int port = Integer.parseInt((new PropManager()).getProperty("updatecost_tcp_port"));
            
            try {
                this.server = new ServerSocket(port);
                System.out.println("ITR2: Servidor2 iniciado");
            } catch(IOException e) {
                System.out.println("ITR2: Error al iniciar socket. "+e.toString());
                e.printStackTrace();
            }
        }
        
        /**
         * Given a LINK_ID (key), cost (new_cost) and timestamp (timestamp_str), update the cost with the new one.
         * @param key Must be a LINK_ID
         * @param new_cost double
         * @param timestamp_str SQL timestamp
         */
        private void updateCost(String key, Double new_cost, String timestamp_str) {
            double confidence, deltat, ftt;
            final double Rr = 60;
            final double Cota = 180;

            Transaction tx = this.graphDb.beginTx();
            try {
                Date timestamp = this.simpleDate.parse(timestamp_str);
                //confidence = this.alpha(timestamp, this.HOWLONG);
                System.out.println("ITR2: [DEBUG] Updating Key: "+key);
                Node tmp = this.nodeIndex.get("name", key).getSingle();

                deltat = this.deltat(timestamp);
                if(deltat < Rr) {
                    System.out.println("ITR2: [DEBUG] deltat(menor que): "+deltat);
                    tmp.setProperty("rt", new_cost);
                    confidence = (Rr - deltat) / Rr;
                    confidence = (1 - confidence) / 2; 
                    tmp.setProperty("conf", confidence);
                } else {
                    System.out.println("ITR2: [DEBUG] deltat(mayor que): "+deltat);
                    confidence = (Cota - (deltat - Rr)) / Cota;
                    ftt = (Double) tmp.getProperty("ftt");
                    tmp.setProperty("rt", new_cost*confidence + ftt*(1 - confidence));
                    tmp.setProperty("conf", confidence);
                }

                tx.success();
            } catch(Exception e) {
                System.out.println("ITR2: Error actualizando costo: "+e);
                e.printStackTrace();
            } finally {
                tx.finish();
            }

        }
               
         /**
         * Returns diference between t_timestamp and current time
         * @param t_timestamp
         * @return 
         */
        private double deltat(Date t_timestamp) {
            Calendar rightNow = Calendar.getInstance();
            double delta = rightNow.getTimeInMillis() - t_timestamp.getTime();
            delta = delta / 60000;
            return delta;
        }

        /**
         * Service that wait for incomming updates.
         */
        @Override
        public void run() {
            String data;
            Socket client = null;
            BufferedReader br = null;

            try {
                client = this.server.accept();
                br = new BufferedReader(new InputStreamReader(client.getInputStream()));
                while(!(data = br.readLine().trim()).equals("end")) {
                    this.link_id = data.split(",")[LINK_ID].trim();
                    this.cost = data.split(",")[COST].trim();
                    this.timestamp = data.split(",")[TIMESTAMP].trim();
                    this.dir = data.split(",")[DIR].trim();
                    
                    try {
                        this.updateCost(
                                (this.dir.equals("C") ? this.link_id : Integer.toString(Integer.parseInt(this.link_id) - 1000000000)),
                                Double.parseDouble(this.cost),
                                this.timestamp);
                    } catch(Exception e) {
                        System.out.println("ITR2: Error actualizando costo. "+e.toString());
                        e.printStackTrace();
                    }
                }
                client.close();
                br.close();
            } catch(IOException e) {
                System.out.println("ITR2: Error de conexion. "+e.toString());
                e.printStackTrace();
            } catch(Exception alle) {
                System.out.println("ITR2: Error formato. "+alle.toString());
                alle.printStackTrace();
            }
        }
    }
    
    /**
     * Returns diference between t_timestamp and current time
     * @param t_timestamp
     * @return 
     */
    private double deltat(Date t_timestamp) {
        Calendar rightNow = Calendar.getInstance();
        double delta = rightNow.getTimeInMillis() - t_timestamp.getTime();
        delta = delta / 60000;
        return delta;
    }
    
    /**
    * Given a LINK_ID (key), cost (new_cost) and timestamp (timestamp_str), update the cost with the new one.
    * @param key Must be a LINK_ID
    * @param new_cost double
    * @param timestamp_str SQL timestamp
    */
    @Override
    public void updateCost(String key, Double new_cost, String timestamp_str) {
        double confidence, deltat, ftt;
        final double Rr = 60;
        final double Cota = 180;
        
        Transaction tx = this.graphDb.beginTx();
        try {
            Date timestamp = this.simpleDate.parse(timestamp_str);
            System.out.println("ITR2: [DEBUG] Updating Key: "+key);
            Node tmp = this.nodeIndex_way.get("name", key).getSingle();
            
            deltat = this.deltat(timestamp);
            if(deltat < Rr) {
                System.out.println("ITR2: [DEBUG] deltat(menor que): "+deltat);
                tmp.setProperty("rt", new_cost);
                confidence = (Rr - deltat) / Rr;
                confidence = (1 - confidence) / 2; 
                tmp.setProperty("conf", confidence);
            } else {
                System.out.println("ITR2: [DEBUG] deltat(mayor que): "+deltat);
                confidence = (Cota - (deltat - Rr)) / Cota;
                ftt = (Double) tmp.getProperty("ftt");
                tmp.setProperty("rt", new_cost*confidence + ftt*(1 - confidence));
                tmp.setProperty("conf", confidence);
            }
            
            tx.success();
        } catch(Exception e) {
            System.out.println("ITR2: Error actualizando costo: "+e);
            e.printStackTrace();
        } finally {
            tx.finish();
        }
        
        System.out.println("ITR2: [DEBUG] New cost: "+this.nodeIndex_way.get("name",key).getSingle().getProperty("rt"));
    }
    
    /**
     * Create the RMI instance used for cost updating.
     */
    @Override
    public void createRMIInstance() {
        try {
            java.rmi.registry.LocateRegistry.createRegistry(1099);
            System.out.println("Creating instance");
            this.rmii2 = new RMIItrouting2Impl(this.graphDb,this.nodeIndex_way);
            //this.rmii2 = (RMIItrouting2Impl) UnicastRemoteObject.exportObject(this.rmii2, 1098);
            
            System.out.println("rebind instance");
            Naming.rebind("rmi:///RMIItrouting2", this.rmii2);
        } catch(Exception e){e.printStackTrace();}
    }
    
    @Override
    public void loadAllData() {
        Node node_tmp;
        Object prop_tmp;
        
        System.out.println("ITR2: [DEBUG] Load graph in memory!");
        for(Node node : this.graphDb.getAllNodes()) {
            for(Relationship rel : node.getRelationships()) {
                //relation
                for(String key: rel.getPropertyKeys()) {
                    prop_tmp = rel.getProperty(key);
                }
                
                //start node
                node_tmp = rel.getStartNode();
                for(String key: node_tmp.getPropertyKeys()) {
                    prop_tmp = node_tmp.getProperty(key);
                }
                
                //end node
                node_tmp = rel.getEndNode();
                for(String key: node_tmp.getPropertyKeys()) {
                    prop_tmp = node_tmp.getProperty(key);
                }
            }
        }
    }
    
    /**
     * Create a graph database based on Navteq information.
     */
    @Override
    public void createDb() {
        //hibernate stuff
        Query hib_query;
        List result = null;
        Object[] result_list;
        HashMap<String,Integer> index = new HashMap();
        HashMap<String,String> config = new HashMap();
        String query;
        
        //Neo4j stuff
        Transaction tx;
        Node A,B;
        int id, id_ori, source, target, node, node_ori, batch, count, batch_factor, source_id, link_id, town_id, state_id;
        double cost, x, y, max_time_cat, length;
        String linestring, name, alt_name, data_source, lang, type;
        boolean dir_link, private_;
        String source_id_str;

        config.put("cache_type","strong");
        //config.put("node_auto_indexing", "false");
        
        //config.put( "neostore.nodestore.db.mapped_memory", "150M");
        //config.put("neostore.relationshipstore.db.mapped_memory", "5G");
        //config.put( "neostore.propertystore.db.mapped_memory", "100M");
        //config.put( "neostore.propertystore.db.strings.mapped_memory", "130M");
        //config.put( "neostore.propertystore.db.arrays.mapped_memory", "130M");
        //config.put( "node_auto_indexing", "true");
        config.put( "use_memory_mapped_buffers", "true");
        //config.put( "neostore.propertystore.db.index.keys.mappedsource_id_memory", "150M");
        //config.put( "neostore.propertystore.db.index.mapped_memory", "150M");

        //config.put("relationship_auto_indexing", "false");
        config.put("node_keys_indexable", "name,length,rt,conf,x,y");
        config.put("relationship_keys_indexable", "length,link_name,original_link_name");
	    config.put("keep_logical_logs","false");

        this.session = HibernateSession.getSessionFactory().openSession();
        
   	    //The graph is not created if it exists into a file
    	if(fileExists(new File( DB_PATH ))) {
            //graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( DB_PATH );
            graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( DB_PATH ).setConfig(config).newGraphDatabase();
            nodeIndex_way = graphDb.index().forNodes("way");
            nodeIndex_poi = graphDb.index().forNodes("poi");
            nodeIndex_divter = graphDb.index().forNodes("divter");
            //autoNodeIndex = graphDb.index().getNodeAutoIndexer().getAutoIndex();
            registerShutdownHook( graphDb );
            //if(this.pointLayer == null) { this.createSpatialDb(); }
            if(((new PropManager()).getProperty("updatecost_tcp")).equals("yes")) {
                this.UpCS = new updateCostService(this.graphDb, this.nodeIndex_way);
                this.UpCS.start();
                System.out.println("ITR2: [DEBUG] Actualizacion de costo via socket habilitada.");
            }
            return;
    	}
    	
    	clearDb();
        deleteFileOrDirectory( new File( DB_PATH ) );
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( DB_PATH ).setConfig(config).newGraphDatabase();
        //nodeIndex = graphDb.index().forNodes("nodes");
        nodeIndex_way = graphDb.index().forNodes("way");
        nodeIndex_poi = graphDb.index().forNodes("poi");
        nodeIndex_divter = graphDb.index().forNodes("divter");

        registerShutdownHook( graphDb );

        tx = graphDb.beginTx();
        try {   
            //states (nodes)
            index.put("id",0);
            index.put("source_id",1);
            index.put("type",2);
            index.put("name",3);
            index.put("lang",4);
            index.put("data_source",5);
            count = 0;
            batch_factor = 0;
            while(true) {
                batch = batch_factor*this.NODES_BATCH;
                batch_factor++;
                query = "select id,source_id,type,name,lang,source from itrouting_new_info.states where id > "+batch+" order by id limit "+this.NODES_BATCH;
                hib_query = this.session.createSQLQuery(query);
                result = hib_query.list();
                if(result.isEmpty()) {break;}

                for(Object res : result) {
                    System.out.println("ITR2: [DEBUG] Loading states "+count);
                    count++;
                    result_list = (Object[]) res;

                    id = (Integer) result_list[index.get("id")];
                    source_id = (Integer) result_list[index.get("source_id")];
                    type = (String) result_list[index.get("type")];
                    name = (String) result_list[index.get("name")];
                    lang = (String) result_list[index.get("lang")];
                    data_source = (String) result_list[index.get("data_source")];

                    Node nod = graphDb.createNode();
                    nod.setProperty("id", id);
                    nod.setProperty("source_id", source_id);
                    nod.setProperty("type", type);
                    nod.setProperty("name", name);
                    nod.setProperty("lang", lang);
                    nod.setProperty("data_source", data_source);

                    //Node index
                    nodeIndex_divter.add(nod,"id",id);
                    nodeIndex_divter.add(nod,"source_id",source_id);
                    nodeIndex_divter.add(nod,"type",type);
                    nodeIndex_divter.add(nod,"name",name);
                    nodeIndex_divter.add(nod,"lang",lang);
                }
                result.clear();
                
                System.gc();
            }

            //towns
            index.clear();
            result.clear();
            index.put("id",0);
            index.put("source_id",1);
            index.put("type",2);
            index.put("name",3);
            index.put("lang",4);
            index.put("data_source",5);
            count = 0;
            batch_factor = 0;
            while(true) {
                batch = batch_factor*this.NODES_BATCH;
                batch_factor++;
                query = "select id,source_id,type,name,lang,source from itrouting_new_info.towns where id > "+batch+" order by id limit "+this.NODES_BATCH;
                hib_query = this.session.createSQLQuery(query);
                result = hib_query.list();
                if(result.isEmpty()) {break;}

                for(Object res : result) {
                    System.out.println("ITR2: [DEBUG] Loading towns "+count);
                    count++;
                    result_list = (Object[]) res;

                    id = (Integer) result_list[index.get("id")];
                    source_id = (Integer) result_list[index.get("source_id")];
                    type = (String) result_list[index.get("type")];
                    name = (String) result_list[index.get("name")];
                    lang = (String) result_list[index.get("lang")];
                    data_source = (String) result_list[index.get("data_source")];

                    Node nod = graphDb.createNode();
                    nod.setProperty("id", id);
                    nod.setProperty("source_id", source_id);
                    nod.setProperty("type", type);
                    nod.setProperty("name", name);
                    nod.setProperty("lang", lang);
                    nod.setProperty("data_source", data_source);

                    //Node index
                    nodeIndex_divter.add(nod,"id",id);
                    nodeIndex_divter.add(nod,"source_id",source_id);
                    nodeIndex_divter.add(nod,"type",type);
                    nodeIndex_divter.add(nod,"name",name);
                    nodeIndex_divter.add(nod,"lang",lang);
                }
                result.clear();
                
                System.gc();
            }

            //pois
            index.clear();
            result.clear();
            index.put("id",0);
            index.put("source_id",1);
            index.put("type",2);
            index.put("name",3);
            index.put("alt_name",4);
            index.put("poi_lang",5);
            index.put("private",6);
            index.put("lat",7);
            index.put("lon",8);
            index.put("data_source",9);
            count = 0;
            batch_factor = 0;
            while(true) {
                batch = batch_factor*this.NODES_BATCH;
                batch_factor++;
                query = "select id ,source_id,type ,name,alt_name ,poi_lang ,private ,lat ,lon,source                      from (                         select                              id ,                             source_id,                             coalesce(type,'') as type ,                             coalesce(name,'') as name,                             coalesce(alt_name,'') as alt_name ,                             coalesce(poi_lang,'') as poi_lang ,                             coalesce(private,false) as private ,                             lat ,                             lon,                             source                          from itrouting_new_info.pois                     where id > "+batch+" order by id limit "+this.NODES_BATCH+") as foo";
                hib_query = this.session.createSQLQuery(query);
                result = hib_query.list();
                if(result.isEmpty()) {break;}

                for(Object res : result) {
                    System.out.println("ITR2: [DEBUG] Loading pois "+count);
                    count++;
                    result_list = (Object[]) res;

                    id = (Integer) result_list[index.get("id")];
                    source_id_str = (String) result_list[index.get("source_id")];
                    type = (String) result_list[index.get("type")];
                    name = (String) result_list[index.get("name")];
                    alt_name = (String) result_list[index.get("alt_name")];
                    lang = (String) result_list[index.get("poi_lang")];
                    private_ = (Boolean) result_list[index.get("private")];
                    x = (Double) result_list[index.get("lon")];
                    y = (Double) result_list[index.get("lat")];
                    data_source = (String) result_list[index.get("data_source")];

                    Node nod = graphDb.createNode();
                    nod.setProperty("id", id);
                    nod.setProperty("source_id", source_id_str);
                    nod.setProperty("type", type);
                    nod.setProperty("name", name);
                    nod.setProperty("alt_name", alt_name);
                    nod.setProperty("lang", lang);
                    nod.setProperty("private", private_);
                    nod.setProperty("lon", x);
                    nod.setProperty("lat", y);
                    nod.setProperty("data_source", data_source);

                    //Node index
                    if(id == 100001) {
                        System.out.println("Encontrado 100001");
                    }
                    nodeIndex_poi.add(nod,"id",id);
                    nodeIndex_poi.add(nod,"source_id",source_id_str);
                    nodeIndex_poi.add(nod,"type",type);
                    nodeIndex_poi.add(nod,"name",name);
                    nodeIndex_poi.add(nod,"lang",lang);
                    nodeIndex_poi.add(nod,"private", private_);
                    nodeIndex_poi.add(nod,"lon", x);
                    nodeIndex_poi.add(nod,"lat", y);
                    nodeIndex_poi.add(nod,"data_source", data_source);
                }
                result.clear();
                
                System.gc();
            }

            //Ways (nodes)
            index.clear();
            result.clear();
            index.put("node", 0);
            index.put("cost", 1);
            index.put("length", 2);
            index.put("max_time_cat",3);
            index.put("dir_link",4);
            index.put("node_ori", 5);
            index.put("linestring", 6);
            index.put("x", 7);
            index.put("y", 8);
            count = 0;
            batch_factor = 0;
            while(true) {
                batch = batch_factor*this.NODES_BATCH;
                batch_factor++;
                query = "select node, cost, length, max_time_cat, dir_link, node_ori, linestring, x, y from itrouting2.sod_nodes where id > "+batch+" order by id limit "+this.NODES_BATCH;
                hib_query = this.session.createSQLQuery(query);
                result = hib_query.list();
                if(result.isEmpty()) {break;}
                
                for(Object res : result) {                
                    System.out.println("ITR2: [DEBUG] Loading ways "+count);
                    count++;
                    result_list = (Object[]) res;

                    node = (Integer) result_list[index.get("node")]; 
                    cost = (Double) result_list[index.get("cost")];
                    length = (Double) result_list[index.get("length")];
                    max_time_cat = (Double) result_list[index.get("max_time_cat")];
                    dir_link = (Boolean) result_list[index.get("dir_link")];
                    node_ori = (Integer) result_list[index.get("node_ori")];
                    linestring = (String)  result_list[index.get("linestring")];
                    x = (Double) result_list[index.get("x")];
                    y = (Double) result_list[index.get("y")];

                    //Node creation
                    Node nod = graphDb.createNode();
                    nod.setProperty("name", Integer.toString(node));
                    nod.setProperty("ftt", cost);
                    nod.setProperty("length", length);
                    nod.setProperty("rt", cost);
                    nod.setProperty("max_time_cat",max_time_cat);
                    nod.setProperty("dir_link",dir_link);
                    nod.setProperty("linestring", linestring);
                    nod.setProperty("bbox", this.getBbox(linestring));
                    nod.setProperty("conf",0.0);
                    nod.setProperty("old_name",Integer.toString(node_ori));
                    nod.setProperty("x",x);
                    nod.setProperty("y",y);

                    //Node index
                    nodeIndex_way.add(nod,"name",Integer.toString(node));
                    nodeIndex_way.add(nod,"old_name",Integer.toString(node_ori));
                }
                result.clear();
                
                System.gc();
                
            }
            
            //Ways (relations)
            index.clear();
            result.clear();
            index.put("id", 0);
            index.put("source", 1);
            index.put("target", 2);
            index.put("length", 3);
            index.put("id_ori", 4);
            count = 0;
            batch_factor = 0;
            while(true) {
                batch = batch_factor*this.REL_BATCH;
                batch_factor++;
                query = "select id, source, target, length, id_ori from itrouting2.\"Streets_one_dir2\" where id > "+batch+" order by id limit "+this.REL_BATCH; 
                hib_query = this.session.createSQLQuery(query);
                result = hib_query.list();
                if(result.isEmpty()){break;}
                
                for(Object res : result) {
                    System.out.println("ITR2: [DEBUG] Loading ways relationships "+count);
                    count++;
                    result_list = (Object[]) res;
                    id = (Integer) result_list[index.get("id")];
                    source = (Integer) result_list[index.get("source")];
                    target = (Integer) result_list[index.get("target")];
                    cost = (Double) result_list[index.get("length")];
                    id_ori = (Integer) result_list[index.get("id_ori")];

                    A = nodeIndex_way.get("name",Integer.toString(source)).getSingle();
                    B = nodeIndex_way.get("name",Integer.toString(target)).getSingle();

                    Relationship rel = A.createRelationshipTo(B,RelTypes.MOVE_TO);
                    rel.setProperty("length", cost);
                    rel.setProperty("link_name",Integer.toString(id));
                    rel.setProperty("original_link_name", Integer.toString(id_ori));
                }
                result.clear();
                System.gc();
            }

            //poi (relations)
            index.clear();
            result.clear();
            index.put("id", 0);
            index.put("link_id", 1);
            index.put("town_id", 2);
            index.put("state_id", 3);
            count = 0;
            batch_factor = 0;
            Relationship rel;

            int fail_poi_way = 0;
            int fail_poi_town = 0;
            int fail_poi_state = 0;
            while(true) {
                batch = batch_factor*this.REL_BATCH;
                batch_factor++;
                query = "select id, link_id, town_id, state_id from (select id,coalesce(link_id,0) as link_id, town_id, state_id from itrouting_new_info.pois where id > "+batch+" order by id limit "+this.REL_BATCH+") as foo"; 
                hib_query = this.session.createSQLQuery(query);
                result = hib_query.list();
                if(result.isEmpty()){break;}
                
                for(Object res : result) {
                    System.out.println("ITR2: [DEBUG] Loading poi relationships "+count);
                    count++;
                    result_list = (Object[]) res;
                    id = (Integer) result_list[index.get("id")];
                    link_id = (Integer) result_list[index.get("link_id")];
                    town_id = (Integer) result_list[index.get("town_id")];
                    state_id = (Integer) result_list[index.get("state_id")];

                    //poi -> way
                    //System.out.println("A(id): "+id+" B(link_id): "+link_id);
                    A = nodeIndex_poi.get("id",id).getSingle();
                    B = nodeIndex_way.get("name",Integer.toString(link_id - 1000000000)).getSingle();
                    if(A == null || B == null) {
                        fail_poi_way++;
                    } else {
                        rel = A.createRelationshipTo(B,RelTypes.NEAR_OF);
                    }

                    //poi -> town
                    B = nodeIndex_divter.get("source_id",town_id).getSingle();
                    if(A == null || B == null) {
                        fail_poi_town++;
                    } else {
                        rel = A.createRelationshipTo(B,RelTypes.IS_IN);                    
                    }

                    //poi -> state
                    B = nodeIndex_divter.get("source_id",state_id).getSingle();
                    if(A == null || B == null) {
                        fail_poi_state++;
                    } else {
                        rel = A.createRelationshipTo(B,RelTypes.IS_IN);                    
                    }
                }
                result.clear();
                System.gc();
            }

            //System.out.println("Total. poi->way in null: "+fail_poi_way+" | poi->town in null: "+fail_poi_town+" | poi->state in null: "+fail_poi_state);

            //ways (extra relations)
            index.clear();
            result.clear();
            index.put("link_id", 0);
            index.put("town_id", 1);
            index.put("state_id", 2);
            count = 0;
            batch_factor = 0;
            while(true) {
                batch = batch_factor*this.REL_BATCH;
                batch_factor++;
                query = "select link_id, town_id, state_id from itrouting_new_info.streets_relationships where id > "+batch+" order by id limit "+this.REL_BATCH; 
                hib_query = this.session.createSQLQuery(query);
                result = hib_query.list();
                if(result.isEmpty()){break;}
                
                for(Object res : result) {
                    System.out.println("ITR2: [DEBUG] Loading ways extra relationships "+count);
                    count++;
                    result_list = (Object[]) res;
                    link_id = (Integer) result_list[index.get("link_id")];
                    town_id = (Integer) result_list[index.get("town_id")];
                    state_id = (Integer) result_list[index.get("state_id")];

                    //way -> town
                    //System.out.println("A(id): "+id+" B(link_id): "+link_id);
                    A = nodeIndex_way.get("name",Integer.toString(link_id - 1000000000)).getSingle();
                    B = nodeIndex_divter.get("source_id",town_id).getSingle();
                    if(A != null && B != null) {
                        rel = A.createRelationshipTo(B,RelTypes.IS_IN);
                    }

                    //way -> state
                    B = nodeIndex_divter.get("source_id",state_id).getSingle();
                    if(A != null && B != null) {
                        rel = A.createRelationshipTo(B,RelTypes.IS_IN); 
                    }
                }
                result.clear();
                System.gc();
            }

            
            System.out.println("ITR2: [DEBUG] "+count+" relaciones cargadas");
          
            index.clear();
            result.clear();
            tx.success();
            
            if(((new PropManager()).getProperty("updatecost_tcp")).equals("yes")) {
                this.UpCS = new updateCostService(this.graphDb, this.nodeIndex_way);
                this.UpCS.start();
                System.out.println("ITR2: [DEBUG] Actualizacion de costo via socket habilitada. Escuchando por el puerto 5115.");
            }
        
        } catch (HibernateException hibe) {
            System.out.println("ITR2: Fallo de inicializacion de Hibernate. "+hibe.toString());
            hibe.printStackTrace();
        } catch (Exception e) {
            System.out.println("ITR2: Error creando base de datos geografica. "+e.toString());
            e.printStackTrace();
        } finally {
            tx.finish();
            //this.session.close();
        }

        //graphDb.shutdown();
    }
    
    
    /**
     * 
     * @param linestring bbox int the format X1 Y1, X2 Y2
     * @return 
     */
    private String getBbox(String linestring) {
        String[] points = linestring.split(",");
        double Xmin, Xmax, Ymin, Ymax, x, y;
        Xmin = Double.MAX_VALUE;
        Ymin = Double.MAX_VALUE;
        Xmax = - Double.MAX_VALUE;
        Ymax = - Double.MAX_VALUE;
        
        for(int i=0; i < points.length; i++) { 
            x = Double.parseDouble(points[i].split(" ")[0]); 
            y = Double.parseDouble(points[i].split(" ")[1]);
            Xmin = x < Xmin ? x : Xmin;
            Ymin = y < Ymin ? y : Ymin;
            Xmax = x > Xmax ? x : Xmax;
            Ymax = y > Ymax ? y : Ymax;
        }
        
        return Xmin+" "+Ymin+","+Xmax+" "+Ymax;
        
    }
    
    /**
     * Prepare two Instances of the A* algorithm, one for FTV cost (normal cost) and the other one for real time cost.
     */
    @Override
    public void prepareAstar() {
        this.algos = new HashMap<String, PathFinder<WeightedPath>>();
        
    	try {
            EstimateEvaluator<Double> estimateEvaluator_length = new EstimateEvaluator<Double>() {
                @Override
                public Double getCost( final Node node, final Node goal ) {
                    double dx = (Double) node.getProperty( "x" ) - (Double) goal.getProperty( "x" );
                    double dy = (Double) node.getProperty( "y" ) - (Double) goal.getProperty( "y" );
                    double result = Math.sqrt( Math.pow( dx, 2 ) + Math.pow( dy, 2 ) );
                    return result;
                }
            };
            
            EstimateEvaluator<Double> estimateEvaluator_time = new EstimateEvaluator<Double>() {
                @Override
                public Double getCost( final Node node, final Node goal ) {
                    double dx = (Double) node.getProperty( "x" ) - (Double) goal.getProperty( "x" );
                    double dy = (Double) node.getProperty( "y" ) - (Double) goal.getProperty( "y" );
                    double result = Math.sqrt( Math.pow( dx, 2 ) + Math.pow( dy, 2 ) );
                    return result / 30.0;
                }
            };
            
            CostEvaluator<Double> my_costEval = new CostEvaluator<Double>() {
                        
                final int COST = 0;
                final int PREFERENCE = 1;
                final int THETA = 2;
                
                @Override
            	public Double getCost(Relationship relationship, Direction direction, Object params) {
                    String type, preference, theta_str;
                    type = ((String[]) params)[COST];
                    preference = ((String[]) params)[PREFERENCE];
                    theta_str = ((String[]) params)[THETA];
                    
                    if(type.equals("rt")) {
                        Node endNode = relationship.getEndNode();
                        double theta = Double.parseDouble(theta_str);
                        double conf = (Double) endNode.getProperty("conf");
                        double MTC = (Double) endNode.getProperty("max_time_cat");
                        double ftt = (Double) endNode.getProperty("ftt");
                        double rt_cost = (Double) endNode.getProperty("rt");

                        double cost = (1 - conf)*(10*ftt*theta + (1 - theta)*rt_cost) + conf*rt_cost;

                        return cost;
                    } else if (type.equals("length")) {
                        Node endNode = relationship.getEndNode();
                        return (Double) endNode.getProperty("length");
                    } else {
                        Node endNode = relationship.getEndNode();
                        return (Double) endNode.getProperty("ftt");
                    }
            	}
            };
            
            //astar - free_travel_time
            this.algos.put(
                    "time", GraphAlgoFactory2.aStar(
                    //Traversal.expanderForAllTypes(Direction.OUTGOING),
                    Traversal.expanderForTypes(RelTypes.MOVE_TO,Direction.OUTGOING),
                    my_costEval,
                    estimateEvaluator_time ));
            
            this.algos.put(
                    "length", GraphAlgoFactory2.aStar(
                    //Traversal.expanderForAllTypes(Direction.OUTGOING),
                    Traversal.expanderForTypes(RelTypes.MOVE_TO,Direction.OUTGOING),
                    my_costEval,
                    estimateEvaluator_length ));
            
    	} catch (Exception e) {	
            System.out.println("ITR2: Error al configurar A*. "+e.toString());
            e.printStackTrace();
        }
    }
    
    /**
     * Map the given point with a graph node.
     * @param x long
     * @param y lat
     * @return LINK_ID
     */
    @Override
    public String coorToNode(double x, double y, String azimuth) {
        String query, params;
        Query hib_query;
        List result;
        
        params = (azimuth == null ? (x+","+y) : (x+","+y+","+azimuth));
               
        query = "select link_id from itrouting2.get_nearest_edge_by_coor("+params+")";
        hib_query = this.session.createSQLQuery(query);
        result = hib_query.list();
        for(Object res :  result) {
            return Integer.toString((Integer) res);
        }
        
        return null;
        
    }
    
    @Override
    public String coorToNode(String x, String y, String azimuth) {
        String query, params;
        Query hib_query;
        List result;
        
        params = (azimuth == null ? (x+","+y) : (x+","+y+","+azimuth));
               
        query = "select link_id from itrouting2.get_nearest_edge_by_coor("+params+")";
        hib_query = this.session.createSQLQuery(query);
        result = hib_query.list();
        for(Object res :  result) {
            return Integer.toString((Integer) res);
        }
        
        return null;
        
    }
    
    @Override
    public String coorToNode(String point) {
        String[] point_arr = point.split(",");
        String query, params;
        Query hib_query;
        List result;
        
        if(point_arr.length == 3) {
            params = point_arr[0]+","+point_arr[1]+","+point_arr[2];
        } else {
            params = point_arr[0]+","+point_arr[1];
        }
               
        query = "select link_id from itrouting2.get_nearest_edge_by_coor("+params+")";
        hib_query = this.session.createSQLQuery(query);
        result = hib_query.list();
        for(Object res :  result) {
            return Integer.toString((Integer) res);
        }
        
        return null;
        
    }
    
    /**
     * Given a source and a target LINK_ID, perform A* and find the best path.
     * @param source_id Must be a LINK_ID
     * @param target_id Must be a LINK_ID
     * @param cost type cost. Must be rt or normal
     * @return ArrayList of LINK_IDs
     */
    @Override
    public Pair<ArrayList<String>,Double> getPath(String source_id, String target_id, String[] params) {
        ArrayList<String> shortest_path = new ArrayList();
        ArrayList<String> tmp = new ArrayList();
        ArrayList<String> tmp2 = new ArrayList();
        tmp2.add("-1");
        final int COST=0;
        
        String cost = params[COST];
    	try {
            Node source = nodeIndex_way.get("name",source_id).getSingle(); //autoNodeIndex.get("name",source_id).getSingle();//
            Node target = nodeIndex_way.get("name",target_id).getSingle(); //autoNodeIndex.get("name",target_id).getSingle();//
            
            WeightedPath path;
            if(cost.equals("rt")) {
                //System.out.println("ITR2: [DEBUG] COST rt");
                path = this.algos.get("time").findSinglePath(source, target, params); //astar_rt.findSinglePath(source, target);
            } else if(cost.equals("ftt")) {
                //System.out.println("ITR2: [DEBUG] COST free_travel_time");
                path = this.algos.get("time").findSinglePath(source, target, params); //astar.findSinglePath(source, target);
            } else {
                //System.out.println("ITR2: [DEBUG] COST length");
                path = this.algos.get("length").findSinglePath(source, target, params);
            }
            
            for(PropertyContainer nod : path) {   
                if(nod.hasProperty("old_name")) {
                        tmp.add(
                                
                                nod.getProperty("old_name")+","+
                                nod.getProperty(cost)+","+//(cost.equals("rt") ? nod.getProperty("rt") : nod.getProperty("ftt"))+","+
                                nod.getProperty("linestring").toString().replace(',', '|')+","+
                                nod.getProperty("dir_link")
                                );
                }
                if(nod.hasProperty("original_link_name")) {
                        tmp2.add((String) nod.getProperty("original_link_name"));
                }
            }
            
            for(int i = 0; i < tmp.size(); i++) {
                shortest_path.add(tmp2.get(i)+","+tmp.get(i));
            }
            
            tmp.clear();
            tmp2.clear();

            Pair<ArrayList<String>,Double> res = new Pair<ArrayList<String>,Double>(shortest_path,path.weight());
            return res;
            
    	} catch (Exception e) {
            //System.out.println("ITR2: Error construyendo camino "+e.toString());
            //e.printStackTrace();
        }
        return null;
    }

    /**
     * Clear the graph DB.
     */
    private void clearDb() {
        try {
            FileUtils.deleteRecursively( new File( DB_PATH ) );
        } catch ( IOException e ) {
            throw new RuntimeException( e );
        }
    }

    /**
     * Shut down the spatial database.
     */
    @Override
    public void shutDown() {
        graphDb.shutdown();
    }

    private static void registerShutdownHook( final GraphDatabaseService graphDb ) { 
        Runtime.getRuntime().addShutdownHook( new Thread() {
            @Override
            public void run() {
                graphDb.shutdown();
            }
        });
    }
    
    /**
     * Check if a file exists.
     * @param file file
     * @return true if the file exists
     */
    private static boolean fileExists(File file) {
    	return file.exists();
    }

    /**
     * Delete the given file.
     * @param file 
     */
    private static void deleteFileOrDirectory( File file ) {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            for ( File child : file.listFiles() ) {
                deleteFileOrDirectory( child );
            }
        } else {
            file.delete();
        }
    }

    // ************************************************************************
    // ************************************************************************
    //                          General Query functions
    // ************************************************************************
    // ************************************************************************
    /**
    *   Returns the serialize information of a particular entity in the graph
    *   @param id   ID of the entity
    *   @param type type of entity
    */
    public HashMap<String,Object> getSerializeNodeInfo(int id, String type) {
        HashMap<String,Object> info = new HashMap<String,Object>();
        Node thisnode;
        try {
            if(type.equals("poi")) {
                thisnode = nodeIndex_poi.get("id",id).getSingle();
                for(String key : thisnode.getPropertyKeys()) {
                    info.put(
                        key,
                        thisnode.getProperty(key)
                        );
                }
                return info;
            }
            
        } catch (Exception e) {
            System.out.println("Error in getSerializeNodeInfo: ");
            e.printStackTrace();
        }
        return null;
    }

    
}
