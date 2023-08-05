package com.devkev.server;

import java.io.File;
import java.sql.SQLException;
import java.util.function.Supplier;

import org.jooby.Jooby;

import com.devkev.database.DBConnection;

public class ServerMain {
	
	public static ServerConfiguration SERVER_CONFIG;
	public static DBConnection DB_CON;
	
	public static void main(String[] args) {
		
		SERVER_CONFIG = new ServerConfiguration(new File(args[0]));
		try {
			DB_CON = new DBConnection(SERVER_CONFIG);
		} catch (ClassNotFoundException | SQLException e) {
			return;
		}
		
		Jooby.run(new Supplier<Jooby>() {
			@Override
			public Jooby get() {
				return new API(DB_CON);
			}
		});
	}

}
