package com.devkev.server;

import org.jooby.Sse;

import com.devkev.models.ClientModel;

//This class is a "virtual" representation of the actual client and is constantly being synced with the server
public class Client {

	public ClientModel model;
	
	//Live data.
	public String sessionID;
	public int matchID;
	public Sse emitter; //The emitter associated with the current session ID
	
	public boolean lostConnection = false;
	public int lastEventID = 0;
	
	public Client(ClientModel model) {
		this.model = model;
	}
	
}
