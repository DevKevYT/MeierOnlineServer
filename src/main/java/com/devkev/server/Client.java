package com.devkev.server;

import java.util.ArrayList;
import java.util.UUID;

import org.jooby.Sse;

import com.devkev.models.ClientModel;

//This class is a "virtual" representation of the actual client and is constantly being synced with the server
public class Client {

	public static final int SESSION_LIFETIME = 60000 * 5; //5 minutes at default
	
	private static final ArrayList<String> issuedSessionIDs = new ArrayList<String>();
	
	public ClientModel model;
	
	//Live data.
	private String sessionID;
	public Match currentMatch;
	
	public Sse emitter; //The emitter associated with the current session ID
	
	public boolean lostConnection = false;
	public int lastEventID = 0;
	public boolean alreadyRolled = false;
	
	//The timestamp when this session becomes invalid and the client is automatically kicked
	private long sessionIdValid = 0;
	
	public Client(ClientModel model) {
		this.model = model;
	}
	
	public boolean hasSession() {
		return sessionID != null;
	}
	
	public String getSessionID() {
		return sessionID;
	}
	
	public void removeSessionID() {
		issuedSessionIDs.remove(sessionID);
		sessionID = null;
	}
	
	public void extendSessionLifetime() {
		sessionIdValid = System.currentTimeMillis() + SESSION_LIFETIME;
		System.out.println("Extended session lifetime for " + model.displayName + " until " + sessionIdValid);
	}
	
	public boolean sessionValid() {
		return System.currentTimeMillis() <= sessionIdValid && hasSession();
	}
	
	public String generateUniqueSessionID() {
		
		extendSessionLifetime();
		
		do {
			String uuid = UUID.randomUUID().toString();
			
			for(String issued : issuedSessionIDs) {
				if(issued.equals(uuid)) continue;
			}
			sessionID = uuid;
			return uuid;
		} while(true);
		
	}
}
