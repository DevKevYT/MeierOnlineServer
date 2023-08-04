package com.devkev.server;

import java.util.ArrayList;

import org.jooby.Jooby;
import org.jooby.handlers.Cors;
import org.jooby.handlers.CorsHandler;
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
		
		use("*", new CorsHandler(new Cors()));
		
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
					
					if(match.isRunning()) {
						rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "The match is currently in progress. Please wait until the current round finishes and try again."));
						return;
					}
					
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
		
		//Requires the session id of the joined user. If the user is the host, a random other client is chosen and notified via event
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
			if(match == null) { //This should not happen!
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
		
		//It's the next person's turn.  If the query parameter "challenge" is set to any value, the current client challenges the previous
		//However if you are the first person to turn, these parameters are ignored
		//The sessionID parameter is required as usual
		//You also need to pass the die value as a two digit integer wich can be identified by the Match lookup table
		//IF the param challenge is set, you don't need to input values
		//Only the currentTurn is allowed to call this endpoint
		post("/api/match/next/", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			if(!ctx.param("dieValue").isSet() && !ctx.param("challenge").isSet()) {
				rsp.send(new ErrorResponse("", 100, "Required parameter: dieValue missing!"));
				return;
			}
			
			if(!ctx.param("sessionID").isSet()) {
				rsp.send(new ErrorResponse("", 100, "Required parameter: sessionID missing!"));
				return;
			}
			
			Client c = getOnlineClientBySession(ctx.param("sessionID").value());
			if(c == null) {
				rsp.send(new ErrorResponse("", 100, "The session id is not valid"));
				return;
			}
			
			Match match = getMatchBySessionID(c.sessionID);
			if(match == null) { //This should not happen!
				rsp.send(new ErrorResponse("", 100, "The session id is not associated with a match. This should not happen. Don't worry it's not your fault :("));
				return;
			}
			if(!match.getCurrentTurn().sessionID.equals(c.sessionID)) {
				rsp.send(new ErrorResponse("", 100, "You are not allowed to pass the dice to the next person! Please wait until it's your turn!"));
				return;
			}
			
			if(ctx.param("challenge").isSet()) {
				System.out.println("Trying to challenge");
				match.challenge();
			} else {
				
				//TODO should it be possible to pass the die without rolling?
				if(!c.alreadyRolled) {
					rsp.send(new ErrorResponse("", 100, "Before passing the die, please roll first!"));
					return;
				}
				
				int absoluteValue = match.getAbsoluteDieValue(ctx.param("dieValue").intValue());
				
				if(absoluteValue < match.getCurrentToldAbsoluteRoll()) {
					rsp.send(new ErrorResponse("", 100, "If you are already lying, you should really consider telling a higher number or are you intentionally trying to lose?"));
					return;
				}
				
				match.next(match.getAbsoluteDieValue(ctx.param("dieValue").intValue()));
			}
			
			rsp.send(new Response("")); //Just send a generic success response
		});
		
		//Requires: sessionID of the host
		//If the match has not been started yet, calling this endpoint as host or "last loser" will start a new round
		post("/api/match/roll/", (ctx, rsp) -> {
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
			if(match == null) { //This should not happen!
				rsp.send(new ErrorResponse("", 100, "The session id is not associated with a match. This should not happen. Don't worry it's not your fault :("));
				return;
			}
			if(!match.allowedToStart(c)) {
				rsp.send(new ErrorResponse("", 100, "Only the host or the last 'loser' is allowed to start/continue a match"));
				return;
			}
			//Start the match and tell eneryone who's turn it is. However people can still join and leave
			if(match.getMembers().size() <= 1) {
				rsp.send(new ErrorResponse("", 100, "Starting the match alone is a bit boring, isn't it? Wait for your friends to join first"));
				return;
			}
			
			if(c.alreadyRolled) {
				rsp.send(new ErrorResponse("", 100, "You can only throw the dice once!"));
				return;
			}
			
			if(!match.isRunning()) {
				System.out.println("Starting the match!");
				match.start();
			}
			
			match.roll();
			
			rsp.send(new Response("")); //Just send a generic success response
		});
		
		//The heartbeat for all clients. It is used to synchronize the virtual clients on the server and the actual clients
		//A client needs to hit this URL with his associated session ID. If the client is in a match, the sync data is being sent every second
		sse("/heartbeat/{sessionID}", (ctx, sse) -> {
			
			String session = ctx.param("sessionID").value();
			Client c = getOnlineClientBySession(session);
			
			sse.onClose(() -> {
				if(c != null) {
					c.sessionID = null;
					c.emitter = null;
				}
				logger.debug("Connection to user terminated");
			});
			
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
			
			c.emitter = sse;
		});

	}
}
