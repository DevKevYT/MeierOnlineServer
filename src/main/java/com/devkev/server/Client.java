package com.devkev.server;

import java.net.Socket;

import org.jooby.Sse;

import com.devkev.models.ClientModel;

//This class is a "virtual" representation of the actual client and is constantly being synced with the server
public class Client {

	public ClientModel model;
	public String sessionID;
	public int matchID;
	
	public Client(ClientModel model) {
		this.model = model;
	}
	
	public void connect(Socket socket) {
		
	}
	
	public void sync() {
		
	}
}
