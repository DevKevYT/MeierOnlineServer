package com.devkev.models;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.devkev.database.DBConnection;

public class ClientModel {
	
	public String uuid;
	public String displayName;
	public long expires;
	
	public int coins;
	
	//These values are only used while a client is online to count the amount of wins and losses therefore these are not saved or fetched in the database
	public int matchWins = 0;
	public int matchLosses = 0;
	
	private ClientModel() {}
	
	//Tries to create a model from a database resultset
	public static ClientModel create(ResultSet resultSet) throws SQLException {
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
			return model;
		} catch(SQLException exception) {
			return null;
		}
	}
	
	/**Updates relevant changes to the model in the database (Currently settings and */
	public void updateModel(DBConnection database) {
		
	}
	
}
