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
	public int lastEventID; //This value should be in sync with the server, otherwise send missing events using the dispatcher in the match
	
	public Client(ClientModel model) {
		this.model = model;
	}
	
}
