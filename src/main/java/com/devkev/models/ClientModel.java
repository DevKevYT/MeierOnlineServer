package com.devkev.models;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ClientModel {
	
	public String uuid;
	public String displayName;
	public long expires;
	
	private ClientModel() {}
	
	//Tries to create a model from a database resultset
	public static ClientModel create(ResultSet resultSet) throws SQLException {
		if(resultSet.next()) {
			ClientModel model = new ClientModel();
			model.uuid = resultSet.getString("user_id");
			model.displayName = resultSet.getString("display_name");
			model.expires = resultSet.getLong("expires");
			return model;
		}
		return null;
	}
	
}
