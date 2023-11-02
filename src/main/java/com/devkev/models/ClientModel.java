package com.devkev.models;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.devkev.database.DBConnection;
import com.devkev.database.QueryParam;

public class ClientModel {
	
	public String uuid;
	public String displayName;
	public long expires;
	
	public int coins;
	
	//These values are only used while a client is online to count the amount of wins and losses therefore these are not saved or fetched in the database
	public int matchWins = 0;
	public int matchLosses = 0;
	
	private DBConnection dbSupplier;
	
	private ClientModel() {}
	
	//Tries to create a model from a database resultset
	/**@param dbSupplier - The database supplier, if you want to update this model later in the database. If not, this argument can be null*/
	public static ClientModel create(DBConnection dbSupplier, ResultSet resultSet) throws SQLException {
		
		if(resultSet.isBeforeFirst()) {
			if(!resultSet.next()) 
				return null;
		}
		
		
		try {
			ClientModel model = new ClientModel();
			model.uuid = resultSet.getString("user_id");
			model.displayName = resultSet.getString("display_name");
			model.expires = resultSet.getLong("expires");
			model.coins = resultSet.getInt("coins");
			model.dbSupplier = dbSupplier;
			return model;
		} catch(SQLException exception) {
			return null;
		}
	}
	
	/**Renders relevant changes to the model in the database (Currently settings and coins)
	 * @throws SQLException */
	public void updateModel() throws SQLException {
		
		if(dbSupplier == null) {
			System.out.println("WARNING: Unable to update user " + uuid + " because this model was created without a database supplier! Changes not saved in the database");
			return;
		}
			
		dbSupplier.queryUpdate("UPDATE user SET coins = ? WHERE user_id = '" + uuid + "'", QueryParam.of(coins));
	}
	
}
