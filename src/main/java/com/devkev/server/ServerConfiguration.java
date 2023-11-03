package com.devkev.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**Diese Klasse managed die Serverkonfigurationsdatei
 * Da die Konfiguration für ein Programm einzigartig ist ist diese Klasse ein Singleton und Ressourcen sind static*/
public class ServerConfiguration {
	
	Properties prop;
	
	//Datenbank
	public final String dbSchemaName;
	public final String dbUsername;
	public final String dbPassword;
	public final int dbPort;
	public final String dbAddress;
	
	public final String ssl_certificatePath;
	public final String ssl_keyPath;
	
	//Logging
	public final String logLevel;
	
	ServerConfiguration(File file) throws IOException {
		
		prop = new Properties();
		
		try (FileInputStream fis = new FileInputStream(file)) {
		    prop.load(fis);
		} catch (FileNotFoundException ex) {
			throw new IllegalArgumentException("Missing server configuration file");
		}
		
		Logger logger = LoggerFactory.getLogger(ServerConfiguration.class);
		
		//node = Parser.parse(file);
		//System.out.println(file.getAbsolutePath() + " configuration:\n" + node.printTree(true));
		logger.info(file.getAbsolutePath() + " configuration:\n" + prop.toString());
		
		
		dbUsername = getMandatoryField("db_username");
		dbPassword = getMandatoryField("db_password");
		dbPort = Integer.valueOf(getOptionalField("db_port", "3306"));
		dbAddress = getOptionalField("db_server", "localhost");
		dbSchemaName = getOptionalField("db_schema_name", "new_database");
		logLevel = getOptionalField("log_level", "ALL"); //
		
		ssl_certificatePath = getOptionalField("ssl_cert", "");
		ssl_keyPath = getOptionalField("ssl_key", "");
		
		if(ssl_certificatePath.isEmpty() || ssl_keyPath.isEmpty()) {
			System.out.println("No ssl set");
		}
	}
	
	private String getOptionalField(String fieldName, String fallback) {
		String value = prop.getProperty(fieldName);
		if(value == null) return fallback;
		
		return value;
	}
	
	
	private String getMandatoryField(String fieldName) {
		String value = prop.getProperty(fieldName);
		if(value == null) throw new IllegalArgumentException("Mandatory value missing or wrong format in configuration file: " + fieldName);
		return value;
	}
}
