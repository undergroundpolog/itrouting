package com.intraffic.itrouting.core.impl;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import com.intraffic.utils.propManager.PropManager;

/**
 *
 * @author intraffic
 */
public class RMIItrouting2Impl extends UnicastRemoteObject implements com.intraffic.itrouting.core.RMIItrouting2 {
    ArrayList<String> newCost;
    
    /**
     * Constructor.
     * @param graphDb   Graph instance.
     * @param nodeIndex Node indexs.
     * @throws RemoteException 
     */
    public RMIItrouting2Impl(GraphDatabaseService graphDb, Index<Node> nodeIndex) throws RemoteException{
        super(1098);
        newCost = new ArrayList<String>();
        int frequency = Integer.parseInt((new PropManager()).getProperty("update_frequency"));
        final daemonUpdaterCost daemon = new daemonUpdaterCost(newCost, frequency,graphDb,nodeIndex);
        daemon.start();
        
        Thread closeChildThread = new Thread() {
            public void run() {
                daemon.destroy();
            }
        };
        
        Runtime.getRuntime().addShutdownHook(closeChildThread);
        
    };
    
        
    /**
     * Given a LINK_ID (key), cost (new_cost) and timestamp (timestamp_str), update the cost with the new one.
     * @param key Must be a LINK_ID
     * @param new_cost double
     * @param timestamp_str SQL timestamp
     */
    @Override
    public void updateLinkCost(String key, Double new_cost, String timestamp_str) throws RemoteException {       
        String row = key+","+new_cost+","+timestamp_str;
        try {
            synchronized(newCost) {
                newCost.add(row);
            }
        } catch(Exception e){e.printStackTrace();}

    }
    
    
    private class daemonUpdaterCost extends Thread {
        ArrayList<String> newCost;
        long frequency;
        GraphDatabaseService graphDb;
        Index<Node> nodeIndex;
        SimpleDateFormat simpleDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final int IDX_LINK = 0;
        final int IDX_NEW_COST = 1;
        final int IDX_TIMESTAMP = 2;
        
        public daemonUpdaterCost(ArrayList<String> newCost, long frequency,GraphDatabaseService graphDb, Index<Node> nodeIndex) {
            this.newCost = newCost;
            this.frequency = frequency;
            
            this.graphDb = graphDb;
            this.nodeIndex = nodeIndex;
        }
        
        /**
        * Returns the time between t_timestamp and current time.
        * @param t_timestamp
        * @return time in minutes.
        */
       private double deltat(Date t_timestamp) {
           Calendar rightNow = Calendar.getInstance();
           double delta = rightNow.getTimeInMillis() - t_timestamp.getTime();
           delta = delta / 60000;
           return delta;
       }
        
        @Override
        public void run() {
            double confidence, deltat, ftt, new_cost;
            final double Rr = 60;
            final double Cota = 180;
            String key, timestamp_str;
            
            while(true) {
                try {
                    Thread.sleep(frequency);
                } catch (InterruptedException ie) {ie.printStackTrace();}
                
                //System.out.println("Entrando en objeto sincronizado");
                synchronized(newCost) {
                    //System.out.println(newCost.size()+" objeto(s)");
                    if(!newCost.isEmpty()) {
                        Transaction tx = this.graphDb.beginTx();
                        try {
                            for(String row : newCost) {
                                key = row.split(",")[IDX_LINK];
                                new_cost = Double.parseDouble(row.split(",")[IDX_NEW_COST]);
                                timestamp_str = row.split(",")[IDX_TIMESTAMP];


                                    //System.out.println("ITR2: [DEBUG] Updating Key: "+key);

                                    Date timestamp = this.simpleDate.parse(timestamp_str);
                                    //System.out.println("ITR2: [DEBUG] Updating Key: "+key);
                                    Node tmp = this.nodeIndex.get("name", key).getSingle();

                                    deltat = this.deltat(timestamp);
                                    if(deltat < Rr) {
                                        //System.out.println("ITR2: [DEBUG] deltat(menor que): "+deltat+" | new_cost: "+new_cost);
                                        tmp.setProperty("rt", new_cost);
                                        confidence = (Rr - deltat) / Rr;
                                        confidence = (1 - confidence) / 2; 
                                        tmp.setProperty("conf", confidence);
                                    } else {
                                        //System.out.println("ITR2: [DEBUG] deltat(mayor que): "+deltat);
                                        confidence = (Cota - (deltat - Rr)) / Cota;
                                        confidence = (confidence < 0 ? 0 : confidence);
                                        ftt = (Double) tmp.getProperty("ftt");
                                        tmp.setProperty("rt", new_cost*confidence + ftt*(1 - confidence));
                                        tmp.setProperty("conf", confidence);
                                    }
                            }
                            tx.success();
                        } catch(Exception e) {
                            System.out.println("ITR2: Error actualizando costo: "+e);
                            e.printStackTrace();
                        } finally {
                            tx.finish();
                        }
                        newCost.clear();
                    }
                }
            }
            
           
        }
    }
}
