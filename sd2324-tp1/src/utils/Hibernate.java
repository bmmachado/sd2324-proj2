package utils;

import java.io.File;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;

import tukano.api.java.Result;
import tukano.api.java.Result.ErrorCode;

/**
 * A helper class to perform POJO (Plain Old Java Objects) persistence, using Hibernate and a backing relational database.
 */
public class Hibernate {
//	private static Logger Log = Logger.getLogger(Hibernate.class.getName());

	private static final String HIBERNATE_CFG_FILE = "hibernate.cfg.xml";
	private SessionFactory sessionFactory;
	private static Hibernate instance;

	

	private Hibernate() {
		try {
			sessionFactory = new Configuration()
            .configure(new File(HIBERNATE_CFG_FILE))
            .buildSessionFactory();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the Hibernate instance, initializing if necessary.
	 * Requires a configuration file (hibernate.cfg.xml)
	 * @return
	 */
	synchronized public static Hibernate getInstance() {
		if (instance == null)
			instance = new Hibernate();
		return instance;
	}
	
	/**
	 * Persists one or more objects to storage
	 * @param objects - the objects to persist
	 */ 
	public Result<Void> persist(Object... objects) {		
		return execute( (session) -> {
			for( var o : objects )
		    	 session.persist(o);
			return Result.ok();
		});    
	}
	
	/**
	 * Updates one or more objects previously persisted.
	 * @param objects - the objects to update
	 */
	public Result<Void> update(Object... objects) {
		return execute( (session) -> {
			for( var o : objects )
		    	 session.merge(o);
			return Result.ok();
		}); 
	}
	
	/**
	 * Removes one or more objects from storage 
	 * @param objects - the objects to remove from storage
	 */
	public Result<Void> delete(Object... objects) {
		return execute( (session) -> {
			for( var o : objects )
		    	 session.remove(o);
			return Result.ok();
		}); 
	}
	
	/**
	 * Performs a jpql Hibernate query (SQL dialect) 
	 * @param <T> The type of objects returned by the query
	 * @param jpqlStatement - the jpql query statement
	 * @param clazz - the class of the objects that will be returned
	 * @return - list of objects that match the query
	 */
	public <T> List<T> jpql(String jpqlStatement, Class<T> clazz) {
		try(var session = sessionFactory.openSession()) {
			var query = session.createQuery(jpqlStatement, clazz);
        	return query.list();
		} catch (Exception e) {
		    throw e;
		}
	}

	/**
	 * Performs a (native) SQL query  
	 * 
	 * @param <T> The type of objects returned by the query
	 * @param jpqlStatement - the jpql query statement
	 * @param clazz - the class of the objects that will be returned
	 * @return - list of objects that match the query
	 */
	public <T> List<T> sql(String sqlStatement, Class<T> clazz) {
		try(var session = sessionFactory.openSession()) {
			var query = session.createNativeQuery(sqlStatement, clazz);
        	return query.list();
		} catch (Exception e) {
		    throw e;
		}
	}
	
	
	public <T> Result<T> getOne(Object id, Class<T> clazz) {
		try(var session = sessionFactory.openSession()) {
			var res = session.find(clazz, id);
			if( res == null )
				return Result.error( ErrorCode.NOT_FOUND );
			else
				return Result.ok( res );
			
		} catch (Exception e) {
		    throw e;
		}
	}
	
	public <T> Result<T> updateOne(T obj) {
		Transaction tx = null;
		try(var session = sessionFactory.openSession()) {
			tx = session.beginTransaction();
			var res = session.merge(obj);			
			tx.commit();	
			if( res == null )
				return Result.error( ErrorCode.NOT_FOUND );
			else
				return Result.ok( res );
		} catch (Exception e) {
			if (tx!=null) tx.rollback();
		    throw e;
		}
	}
	
	public Result<Void> deleteOne(Object obj) {
		Transaction tx = null;
		try(var session = sessionFactory.openSession()) {
			tx = session.beginTransaction();
			session.remove(obj);
			session.flush();
			tx.commit();	
			return Result.ok();
		} catch (Exception e) {
		    throw e;
		}
	}
	
	public <T> Result<T> execute( TransactionWork<T> ops) {
		Transaction tx = null;
		try(var session = sessionFactory.openSession()) {
			tx = session.beginTransaction();
			ops.executeTransaction(session);
			session.flush();
			tx.commit();	
			return Result.ok();
		} catch( ConstraintViolationException __) {
			return Result.error( ErrorCode.CONFLICT );
		} catch (Exception e) {
		     if (tx!=null) tx.rollback();
		     throw e;
		}
	}
	
	public static interface TransactionWork<T> {
		public Result<T> executeTransaction( Session s );
	}
}