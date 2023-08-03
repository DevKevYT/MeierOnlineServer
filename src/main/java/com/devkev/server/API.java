package com.devkev.server;

import java.util.ArrayList;

import org.jooby.Jooby;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devkev.database.DBConnection;
import com.devkev.models.ClientModel;
import com.devkev.models.ErrorResponse;
import com.devkev.models.Response;
import com.devkev.models.Response.ResponseCodes;
import com.devkev.models.ResponseModels.CreateMatchResponse;
import com.devkev.models.ResponseModels.JoinMatchResponse;

public class API extends Jooby {
	
	//ScheduledExecutorService heartbeat = Executors.newScheduledThreadPool(1);
	
	//All clients that are currenty online and being associated with a session id. 
	private ArrayList<Client> onlineClients = new ArrayList<>();
	private DBConnection dbSupplier;
	
	private final Logger logger = LoggerFactory.getLogger(API.class);
	
	public API(DBConnection dbSupplier) {
		this.dbSupplier = dbSupplier;
	}
	
	public Client getOnlineClientBySession(String sessionID) {
		for(Client c : onlineClients) {
			if(c.sessionID.equals(sessionID)) {
				return c;
			}
		}
		return null;
	}
	
	public Client getOnlineClientByUUID(String id) {
		for(Client c : onlineClients) {
			if(c.model.uuid.equals(id)) {
				return c;
			}
		}
		return null;
	}
	
	public void removeOnlineClient(Client client) {
		for(Client c : onlineClients) {
			if(c.model.uuid.equals(client.model.uuid)) {
				onlineClients.remove(c);
				return;
			}
		}
	}
	
	public Match getMatchBySessionID(String sessionID) {
		for(Match m : Match.MATCHES) {
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
			
			if(!ctx.param("displayName").isSet()) {
				System.out.println("Body: " + ctx.body().value());
				rsp.send(new ErrorResponse("", 100, "Required parameter: displayName missing"));
				return;
			}
			
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
			if(getOnlineClientByUUID(clientID) == null) {
				
				Client c = dbSupplier.getUser(clientID);
				
				if(c == null) {
					rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "Unknown clientID: " + clientID));
					return;
				}
				
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
				rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "Client has already created a match. Please leave before creating a new one"));
			}
		});
		
		//TODO Error codes!!
		/**Returns: 
		 * { data: {matchID: [matchID], sessionID: [sessionID], joinedClients: [ { uuid: [uuid], displayName: [name], expires: [expires]} ]}, code: 0}*/
		post("/api/match/join/{matchID}", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			String clientID = ctx.param("clientID").value();
			String matchID = ctx.param("matchID").value();
			
			Client client = dbSupplier.getUser(clientID);
			 
			//If the second statement is not null, the client has already joined another match!
			if(client != null && getOnlineClientByUUID(clientID) == null) {
				
				Match match = getMatchByID(matchID);
				
				if(match != null) {
					
					match.join(client);
					onlineClients.add(client);
					
					client.sessionID = ctx.session().id();
					
					JoinMatchResponse joinResponse = new JoinMatchResponse();
					joinResponse.matchID = match.matchID;
					joinResponse.sessionID = client.sessionID;
					
					ArrayList<ClientModel> coll = new ArrayList<>();
					for(Client c : match.getMembers()) 
						coll.add(c.model);
					joinResponse.joinedClients = coll.toArray(new ClientModel[coll.size()]);
					
					rsp.send(new Response(joinResponse));
					
				} else rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "The match you are trying to join does not exist"));
			
			} else rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "The client with this id does not exist or already joined another match!"));
		});
		
		post("/api/match/leave/", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
		
			if(!ctx.param("sessionID").isSet()) {
				rsp.send(new ErrorResponse("", 100, "Required parameter: sessionID missing"));
				return;
			}
			
			Client c = getOnlineClientBySession(ctx.param("sessionID").value());
			if(c == null) {
				rsp.send(new ErrorResponse("", 100, "The session id is not valid"));
				return;
			}
			
			Match match = getMatchBySessionID(c.sessionID);
			if(match == null) {
				//This should not happen!
				rsp.send(new ErrorResponse("", 100, "The session id is not associated with a match. This should not happen. Don't worry it's not your fault :("));
				return;
			}
			
			try {
				match.leave(c);
				removeOnlineClient(c);
				rsp.send(new Response("")); //Send a generic "ok" message
			} catch(Exception e) {
				rsp.send(new ErrorResponse("", 100, "Error while leaving match: " + e.getMessage()));
				e.printStackTrace();
			}
		});
		
		//The heartbeat for all clients. It is used to synchronize the virtual clients on the server and the actual clients
		//A client needs to hit this URL with his associated session ID. If the client is in a match, the sync data is being sent every second
		sse("/heartbeat/{sessionID}", (ctx, sse) -> {
			String session = ctx.param("sessionID").value();
			
			Client c = getOnlineClientBySession(session);
			
			//If the request has no valid session id associated, just drop the connection
			if(c == null) {
				sse.close();
				return;
			}
			
			Match joined = getMatchBySessionID(session);
			
			//Find the match the client is joined. If there is no match, also drop the connection
			if(joined == null) {
				sse.close();
				return;
			}
			
			sse.onClose(() -> {
				logger.debug("Connection to user " + c.model.displayName + " terminated");
				c.sessionID = null;
				c.emitter = null;
			});
			
			c.emitter = sse;
		});
	}
}
