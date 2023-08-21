package com.devkev.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devkev.models.ClientModel;
import com.devkev.server.Client;
import com.devkev.server.ServerConfiguration;
import com.devkev.server.ServerMain;

/**Diese Klasse managed alle Datenbank verbindungen, liest entsprechende Konfigurationen und handled Queries*/
public class DBConnection {
	
	private final Logger logger;
	
	private final ServerConfiguration configuration;
	public static final long GUEST_USER_LIFESPAN = 60000L * 60L; //1 stunde
	
	private Connection c;
	
	public DBConnection(ServerConfiguration configuration) throws ClassNotFoundException, SQLException {
		logger  = LoggerFactory.getLogger(ServerMain.class);
		
		this.configuration = configuration;
		
		logger.debug("Checking jdbc Driver ...");
		Class.forName("com.mysql.cj.jdbc.Driver");
		
		Connection testConnection;
		try {
			logger.info("Checking MySQL server connection ...");
			testConnection = DriverManager.getConnection("jdbc:mysql://" + configuration.dbAddress + ":" 
						+ configuration.dbPort + "/?user=" + configuration.dbUsername + "&password=" + configuration.dbPassword);
		} catch(SQLException exception) {
			logger.error("Failed to establish a MySQL connection to " + configuration.dbAddress + " using port " + configuration.dbPort 
					+ ". Please ensure you have a MySQL server up and running on the desired address and port.\nAlso check the given credentials may cause a failing login.\n"
					+ "SQL State: " + exception.getSQLState());
			throw exception;
		}
		
		logger.debug("Database connection established");
		
	    checkSOLCSchema(testConnection);
	   
	    testConnection.close();
	    
	    c = createConnection();
	}
	
	/**Generates a schema with information from the configuration with all nessecary tables using a test connection
	 * @throws SQLException */
	private void checkSOLCSchema(Connection connection) throws SQLException {
		logger.debug("Checking schema ...");
		
		Statement createSchema = connection.createStatement();
		createSchema.executeUpdate("CREATE DATABASE IF NOT EXISTS " + configuration.dbSchemaName);
		createSchema.executeUpdate("USE " + configuration.dbSchemaName);
    	createSchema.close();
	    
	    //Create potentially missing tables
	    Statement checkPhasingTable = connection.createStatement();
	    checkPhasingTable.executeUpdate("CREATE TABLE IF NOT EXISTS user ("
			+ "user_id  VARCHAR(36) NOT NULL," //This is going to be a uuid
			+ "display_name varchar(50) NOT NULL,"
			+ "expires BIGINT NOT NULL,"
			+ "PRIMARY KEY (user_id))");
	    checkPhasingTable.close();
	    
	    logger.debug("Check done");
	}
	
	//Creates a guest user and stores him in the database
	public Client createGuestUser(String displayName) throws SQLException {
		String uuid = UUID.randomUUID().toString();
		
		queryUpdate("INSERT INTO user (user_id, display_name, expires) VALUES (?, ?, " +  (System.currentTimeMillis() + GUEST_USER_LIFESPAN) + ")", 
				QueryParam.of(uuid),
				QueryParam.of(displayName));
		
		return new Client(ClientModel.create(query("SELECT * FROM user WHERE user_id = '" + uuid + "'")));
	}
	
	//TODO create a sceduled future (Every 5 minutes)
	public void deleteExpiredUsers() throws SQLException {
		queryUpdate("DELETE FROM user WHERE expired < ?", QueryParam.of(System.currentTimeMillis()));
	}
	
	public void deleteUser(String uuid) throws SQLException {
		queryUpdate("DELETE FROM user WHERE user_id = ?", QueryParam.of(uuid));
	}
	
	/**Extends the lifespan of a guest user. For example by creating session id's regulary
	 * @throws SQLException */
	public void extendGuestUserLifespan(Client user) throws SQLException {
		logger.debug("Extending user lifespan");
		queryUpdate("UPDATE user SET expires = ? WHERE user_id = ?", QueryParam.of(System.currentTimeMillis() + GUEST_USER_LIFESPAN), QueryParam.of(user.model.uuid));
	}
	
	public Client getUser(String uuid) throws SQLException {
		ClientModel model = ClientModel.create(query("SELECT * FROM user WHERE user_id = ?", QueryParam.of(uuid)));
		return model != null ? new Client(model) : null;
	}
	
	private Connection createConnection() throws SQLException {
		//logger.debug("Connecting to " + "jdbc:mysql://" + configuration.dbAddress + ":" + configuration.dbPort);
		
		return DriverManager.getConnection("jdbc:mysql://" + configuration.dbAddress + ":" 
				+ configuration.dbPort + "/" + configuration.dbSchemaName + "?user=" + configuration.dbUsername + "&password=" + configuration.dbPassword);
	}
	
	public void queryUpdate(String query, QueryParam<?> ... parameters) throws SQLException {
		if(!c.isValid(1)) {
			try {
				c.close();
			} catch(SQLException e) { }
			c = createConnection();
		}
		
		PreparedStatement stmt = c.prepareStatement(query);
		logger.debug("Executing update query: " + query);
		
		if(parameters != null) {
			for(int i = 0; i < parameters.length; i++) {
				if(parameters[i].getData() instanceof Integer) 
					stmt.setInt(i+1, (int) parameters[i].getData());
				else if(parameters[i].getData() instanceof Long)
					stmt.setLong(i+1, (long) parameters[i].getData());
				else stmt.setString(i+1, parameters[i].getData().toString());
			}
		}
		
	    stmt.executeUpdate();
	}
	
	/**Close the connection!*/
	public ResultSet query(String query, QueryParam<?> ... parameters) throws SQLException {
		if(!c.isValid(1)) {
			try {
				c.close();
			} catch(SQLException e) { }
			c = createConnection();
		}
		PreparedStatement stmt = c.prepareStatement(query);
		logger.debug("Executing query: " + query);
		
		if(parameters != null) {
			for(int i = 0; i < parameters.length; i++) {
				if(parameters[i].getData() instanceof Integer) 
					stmt.setInt(i+1, (int) parameters[i].getData());
				else if(parameters[i].getData() instanceof Long)
					stmt.setLong(i+1, (long) parameters[i].getData());
				else stmt.setString(i+1, parameters[i].getData().toString());
			}
		}
	    ResultSet result = stmt.executeQuery();
	    
	    return result;
	}
}
