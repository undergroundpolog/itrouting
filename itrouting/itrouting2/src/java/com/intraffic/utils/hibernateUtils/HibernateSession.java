package com.intraffic.utils.hibernateUtils;
 
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
 
/**
 * 
 * @author intraffic
 */
public class HibernateSession {
 
    private static final SessionFactory sessionFactory = buildSessionFactory();
 
    /**
     * Create a Session Factory. This will allow to interact with PostgresSQL.
     * @return 
     */
    private static SessionFactory buildSessionFactory() {
        try {
            // Create the SessionFactory from hibernate.cfg.xml
            return new AnnotationConfiguration().configure()
                    .buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("Initial SessionFactory creation failed." + ex);
            ex.printStackTrace();
            throw new ExceptionInInitializerError(ex);
        }
    }

    /**
     * Returns the current session factory.
     * @return 
     */
    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}