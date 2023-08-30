package com.devkev.server;

import java.io.File;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.function.Supplier;

import org.jooby.Jooby;

import com.devkev.database.DBConnection;
import com.devkev.devscript.raw.Process;

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
		
		if(!SERVER_CONFIG.ssl_certificatePath.isEmpty() && !SERVER_CONFIG.ssl_keyPath.isEmpty()) {
			System.setProperty("ssl.keystore.cert", SERVER_CONFIG.ssl_certificatePath);
			System.setProperty("ssl.keystore.key", SERVER_CONFIG.ssl_keyPath);
		}
		
		Jooby.run(new Supplier<Jooby>() {
			@Override
			public Jooby get() {
				return new API(DB_CON);
			}
		});
		
		new Thread(() -> {
			Scanner s = new Scanner(System.in);
			
			Process interpreter = new Process(true);
			interpreter.clearLibraries();
			interpreter.includeLibrary(new Commands());
			interpreter.addSystemOutput();
			
			try {
				while(true) {
					String command = s.nextLine();
					interpreter.execute(command, false);
				}
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				s.close();
			}
		}, "command-line").start();
	}

}
