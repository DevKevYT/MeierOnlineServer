package com.devkev.server;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jooby.Jooby;
import org.jooby.Sse;

import com.devkev.database.DBConnection;
import com.devkev.models.CreateMatchResponse;

public class API extends Jooby {
	
	ScheduledExecutorService heartbeat = Executors.newScheduledThreadPool(1);
	
	//All clients that are currenty online and being associated with a session id. 
	private ArrayList<Client> onlineClients = new ArrayList<>();
	private DBConnection dbSupplier;
	
	public API(DBConnection dbSupplier) {
		this.dbSupplier = dbSupplier;
		
	}
	
	public Client getClientBySession(String sessionID) {
		for(Client c : onlineClients) {
			if(c.sessionID.equals(sessionID)) {
				return c;
			}
		}
		return null;
	}
	
	public Client getClientByUUID(String id) {
		for(Client c : onlineClients) {
			if(c.model.uuid.equals(id)) {
				return c;
			}
		}
		return null;
	}
	
	public Match getMatchBySessionID(String sessionID) {
		for(Match m : Match.MATCHES) {
			if(m.getHost().sessionID.equals(sessionID)) return m;
			for(Client c : m.getMembers()) {
				if(c.sessionID.equals(sessionID)) return m;
			}
		}
		return null;
	}
	
	public Match getMatchByID(String matchID) {
		for(Match m : Match.MATCHES) {
			if(m.matchID.equals(matchID))
				return m;
		}
		return null;
	}
	
	{
		post("/api/createguest/", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			String displayName = ctx.param("displayName").value();
			Client client = dbSupplier.createGuestUser(displayName);
			
			rsp.send(new Response(client.model));
		});
		
		//Once the client joins a match, or creates a match, he is being put in the "online" list and being constantly synced with the actual client
		
		//Creates a match with a given 4 digit id other people can join
		//Requires: clientID as form parameter
		post("/api/match/create/", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			String clientID = ctx.param("clientID").value();
			
			//get the id of the user and create a session. This session is being used until the client goes offline
			//If the user already has a session, return an error
			if(getClientByUUID(clientID) == null) {
				
				Client c = dbSupplier.getUser(clientID);
				c.sessionID = ctx.session().id();
				
				Match m = Match.createMatch(c);
				onlineClients.add(c);
				
				CreateMatchResponse res = new CreateMatchResponse();
				res.clientID = c.model.uuid;
				res.matchID = m.matchID;
				res.displayName = c.model.displayName;
				res.sessionID = c.sessionID;
				
				rsp.send(new Response(res));
			} else {
				rsp.send(new ErrorResponse("", 1, "Client has already created a match. Please leave before creating a new one"));
			}
		});
		
		post("/api/match/join/{matchID}", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			String clientID = ctx.param("clientID").value();
			String matchID = ctx.param("matchID").value();
			
			Client client = getClientByUUID(clientID);
			if(client != null) {
				
				Match match = getMatchByID(matchID);
				
				if(match != null) {
					match.join(client);
				}
			}
		});
		
		//The heartbeat for all clients. It is used to synchronize the virtual clients on the server and the actual clients
		//A client needs to hit this URL with his associated session ID. If the client is in a match, the sync data is being sent every second
		sse("/heartbeat/{sessionID}", (ctx, sse) -> {
			String session = ctx.param("sessionID").value();
			
			//If the request has no valid session id associated, just drop the connection
			if(getClientBySession(session) == null) {
				sse.close();
				return;
			}
			
			Match joined = getMatchBySessionID(session);
			
			//Find the match the client is joined. If there is no match, also drop the connection
			if(joined == null) {
				sse.close();
				return;
			}
			
			int lastId = joined.getMostrecentEventID();
			
			//Send an event, if the queue for this match is not empty
			ScheduledFuture<?> future = heartbeat.scheduleAtFixedRate(() -> {
				
				sse.event("Hello World").id(lastId).send();
			
			}, 0, 1, TimeUnit.SECONDS);
			
			//Cancel the heartbeat for this client if the connection is lost
			sse.onClose(() -> {
				future.cancel(true);
			});
		});
	}
}
