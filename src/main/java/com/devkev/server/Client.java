package com.devkev.server;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jooby.Sse;

import com.devkev.models.ClientModel;
import com.devkev.server.Match.MatchLeaveReasons;

//This class is a "virtual" representation of the actual client and is constantly being synced with the server
public class Client {

	private static ScheduledExecutorService timeoutClients = Executors.newScheduledThreadPool(1);
	
	public static final int SESSION_LIFETIME = 60000 * 5; //5 minutes at default
	
	private static final ArrayList<String> issuedSessionIDs = new ArrayList<String>();
	
	public ClientModel model;
	
	//Live data.
	private String sessionID;
	public Match currentMatch;
	
	public Sse emitter; //The emitter associated with the current session ID
	
	//Handle connection loss and recover
	public static final long MAX_RECOVER_TIMEOUT = 5000; //If the client does not recover in 5 seconds, he is lost
	private boolean lostConnection = false;
	private int lastEventID = 0;
	private ScheduledFuture<?> waitForReconnect;
	
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
	
	/**Sets the client on connection loss status and scedules a timer until the client should reconnect*/
	public void handleConnectionLoss(Runnable timeout) {
		if(lostConnection) {
			System.out.println("Client " + model.displayName + " already lost the connection. Waiting for reconnect ...");
			return;
		}
		
		System.out.println("Handling connection loss for " + model.displayName + "!");
		lostConnection = true;
		lastEventID = currentMatch.getMostrecentEventID(); //When the client recovers, send all the events he missed!
		
		waitForReconnect = timeoutClients.schedule(timeout, MAX_RECOVER_TIMEOUT, TimeUnit.MILLISECONDS);
	}
	
	//Recovers a connection loss and resends all missed events
	public void handleConnectionRecover(Sse newEmitter) {
		
		System.out.println("Client " + model.displayName + " reconnected!");
		
		this.emitter = newEmitter;
		waitForReconnect.cancel(true);
		lostConnection = false;
		currentMatch.resendEvents(lastEventID, this);
		
	}
	
	public boolean hasLostConnection() {
		return lostConnection;
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
