package com.devkev.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jooby.Jooby;
import org.jooby.handlers.Cors;
import org.jooby.handlers.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devkev.database.DBConnection;
import com.devkev.database.QueryParam;
import com.devkev.models.ClientModel;
import com.devkev.models.ErrorResponse;
import com.devkev.models.Response;
import com.devkev.models.Response.ResponseCodes;
import com.devkev.models.ResponseModels.CreateMatchResponse;
import com.devkev.models.ResponseModels.JoinMatchResponse;
import com.devkev.models.ResponseModels.RollDiceResponse;
import com.devkev.models.ResponseModels.ServerInfoResponse;
import com.devkev.server.Match.MatchLeaveReasons;


public class API extends Jooby {
	
	public static final String VERSION = "Beta 1.0.0";
	
	ScheduledExecutorService deleteExpiredClients = Executors.newScheduledThreadPool(1);
	
	//All clients that are currenty online and being associated with a session id. 
	//private ArrayList<Client> onlineClients = new ArrayList<>();
	List<Client> onlineClients = Collections.synchronizedList(new ArrayList<Client>());
	//Hashtable<String, Client> onlineClients = new Hashtable<String, Client>();
	
	private DBConnection dbSupplier;
	
	private final Logger logger = LoggerFactory.getLogger(API.class);
	//private final Pattern UUID_REGEX = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	
	public API(DBConnection dbSupplier) {
		this.dbSupplier = dbSupplier;
		
		deleteExpiredClients.scheduleAtFixedRate(() -> {
			try {
				
				//Handle invalid sessions. They are automatically kicked from the match but receive a sse event informing about an expired session
				synchronized (onlineClients) {
					ArrayList<Client> garbage = new ArrayList<>();
					for(Client c : onlineClients) {
						if(!c.sessionValid())
							garbage.add(c);
					}
					
					while(garbage.size() > 0) {
						try {
							logger.debug("Kicking " + garbage.get(0).model.displayName + " because the session expired!");
							removeOnlineClient(garbage.get(0), MatchLeaveReasons.SESSION_EXPIRED);
						} catch (Exception e) {
							logger.warn("Exception while kicking client: " + e.getMessage());
							e.printStackTrace();
						}
						garbage.remove(0);
					}
				}
				
				ResultSet set = dbSupplier.query("SELECT * FROM user WHERE expires < ?", QueryParam.of(System.currentTimeMillis()));
				
				while(set.next()) {
					Client c = new Client(ClientModel.create(set));
					
					if(c.model == null) {
						System.out.println("Unable to create client model. This should not happen!");
						continue;
					}
					
					System.out.println(c.model.displayName + " is expired. Checking online status and deleting");
					
					Client actual = getOnlineClientByUUID(c.model.uuid);
					
					if(actual != null) {
						System.out.println("Client is currently online. Do nothing, cause lifespan just got extended. By the way, this should not happen! This could be a corrupted client!");
						//dbSupplier.query("UPDATE user SET expires = " + (System.currentTimeMillis() + 60000) + " WHERE user_id = " + actual.model.uuid);
					} else {
						System.out.println("Client is not online! Deleting!");
						dbSupplier.deleteUser(c.model.uuid);
					}
				}
				System.out.println("Sceduler finished. " + onlineClients.size() + " clients online, " + Match.MATCHES.size() + " matches in progress.");
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}, 10, 10, TimeUnit.SECONDS);
		
	}
	
	public Client getOnlineClientBySession(String sessionID) {
		
		ArrayList<Client> garbage = new ArrayList<Client>();
		for(Client c : onlineClients) {
			if(!c.sessionValid()) garbage.add(c);
		}
		
		while(garbage.size() > 0) {
			removeOnlineClient(garbage.get(0), MatchLeaveReasons.UNKNOWN);
			onlineClients.remove(garbage.get(0));
			garbage.remove(0);
		}
		
		//TODO optionally clean up corrupted hosts
		System.out.println(onlineClients.size() + " clients are online");
		System.out.println(Match.MATCHES.size() + " matches in progress");
		
		for(Client c : onlineClients) {
			if(c.getSessionID().equals(sessionID)) {
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
	
	public synchronized Match getMatchBySessionID(String sessionID) {
		
		synchronized (Match.MATCHES) {
			for(Match m : Match.MATCHES) {
				for(Client c : m.getMembers()) {
					if(c.getSessionID().equals(sessionID)) return m;
				}
			}
			return null;
		}
	}
	
	public synchronized Match getMatchByID(String matchID) {
		
		synchronized (Match.MATCHES) {
			for(Match m : Match.MATCHES) {
				if(m.matchID.equals(matchID))
					return m;
			}
			return null;
		}
	}
	
	//Removes a client from:
	// - a match
	// - online list
	// - releases session id (if valid)
	public void removeOnlineClient(Client client, MatchLeaveReasons reason) {
		
		if(client.currentMatch != null) {
			try {
				client.currentMatch.leave(client, reason);
			} catch (Exception e) {
				logger.warn("Exception while triggering leave match: " + e.getLocalizedMessage() + " while removing online client");
			}
		}
		
		try {
			for(Match m : Match.MATCHES) {
				for(Client c : m.getMembers()) {
					if(client.model.uuid.equals(c.model.uuid)) {
						m.leave(c, MatchLeaveReasons.UNKNOWN);
						logger.debug("User found and left the match");
						break;
					}
				}
			}
		} catch (Exception e) {
			logger.warn("Exception while cleaning up client: " + e.getLocalizedMessage() + " while removing online client");
		}
		
		client.currentMatch = null;
		client.removeSessionID();
		
		if(client.emitter != null) {
			try {
				client.emitter.close();
			} catch (Exception e) {
				logger.warn("Exception while closing sse emitter: " + e.getLocalizedMessage() + " while removing online client");
			}
			client.emitter = null;
		}
		
		onlineClients.remove(client);
	}
	
	{
		securePort(6969);
		
		err((req, rsp, err) -> {
			//Sende immer den Status 200. Das sollte manche verdreckten Bots verwirren. Ein Mensch sollt aus der Fehlernachricht schlau werden
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			logger.debug("Connection from: " + req.ip());
			rsp.status(200);
		    rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "An unhandled server error occurred " + err.getMessage()));
		});
		
		use("*", new CorsHandler(new Cors()));
		
		post("/api/createguest/", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			if(!ctx.param("displayName").isSet()) {
				rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_FORM_DATA, "Required parameter: displayName missing"));
				return;
			}
			if(ctx.param("displayName").value().length() > 20) {
				rsp.send(new ErrorResponse("", ResponseCodes.USERNAME_TOO_LONG, "Your name should be less than 20 characters"));
				return;
			}
			if(ctx.param("displayName").value().contains(" ")) {
				rsp.send(new ErrorResponse("", ResponseCodes.USERNAME_CONTAINS_INVALID_CHARS, "Your name contains illegal characters"));
				return;
			}
			
			String displayName = ctx.param("displayName").value();
			Client client = dbSupplier.createGuestUser(displayName);
			
			
			rsp.send(new Response(client.model));
		});
		
		//Reports, how many players are online, what server version is running (coming soon) and how many matches are in progress
		get("/api/serverinfo", (ctx, rsp) -> {
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "GET");
			
			ServerInfoResponse info = new ServerInfoResponse();
			info.matchesInProgress = Match.MATCHES.size();
			info.playersPlaying = onlineClients.size();
			info.version = VERSION;
			
			rsp.send(new Response(info));
		});
		
		get("/api/user/get/{clientID}", (ctx, rsp) -> {
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "GET");
			
			if(!ctx.param("clientID").isSet()) {
				rsp.send(new ErrorResponse("", 100, "Required parameter: clientID missing"));
				return;
			}
			
			Client c = dbSupplier.getUser(ctx.param("clientID").value());
			if(c == null) {
				rsp.send(new ErrorResponse("", 120, "Invalid client ID"));
				return;
			}
			
			rsp.send(new Response(c.model));
		});
		
		//TODO check for valid uuid format before accessing database
		/**You are not allowed to call this endpoint while a session id is associated with the target client!*/
		post("/api/user/delete", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			if(!ctx.param("clientID").isSet()) {
				rsp.send(new ErrorResponse("", 100, "Required parameter: clientID missing"));
				return;
			}
			
			Client c = dbSupplier.getUser(ctx.param("clientID").value());
			if(c == null) {
				rsp.send(new ErrorResponse("", 120, "Invalid client ID"));
				return;
			}
			
			if(c.hasSession() && c.sessionValid()) {
				rsp.send(new ErrorResponse("", 100, "The client has a valid session id issued and is marked online. Cannot forget online users"));
				return;
			}
			
			dbSupplier.deleteUser(c.model.uuid);
			rsp.send(new Response("")); //Send a generic "ok" message
		});
		
		//Once the client joins a match, or creates a match, he is being put in the "online" list and being constantly synced with the actual client
		
		//Creates a match with a given 4 digit id other people can join
		//Requires: clientID as form parameter
		//TODO optional parameter to create a match without dice comparison. Good if you are in a round together
		post("/api/match/create/", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			String clientID = ctx.param("clientID").value();
			
			//Check if the client has a valid session id. If not, remove the client from the online users
			Client c = getOnlineClientByUUID(clientID);
			if(c != null) {
				if(c.sessionValid()) {
					rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "Client has already created a match. Please leave before creating a new one"));
					return;
				} else {
					onlineClients.remove(c);
				}
			}
			
			c = dbSupplier.getUser(clientID);
			
			if(c == null) {
				rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "Unknown clientID: " + clientID));
				return;
			}
			
			c.generateUniqueSessionID();
			
			Match m = Match.createMatch(c);
			onlineClients.add(c);
			dbSupplier.extendGuestUserLifespan(c);
			
			CreateMatchResponse res = new CreateMatchResponse();
			res.clientID = c.model.uuid;
			res.matchID = m.matchID;
			res.displayName = c.model.displayName;
			res.sessionID = c.getSessionID();
			
			rsp.send(new Response(res));
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
			
			if(client == null) {
				rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "Unknown Client id"));
			}
			
			if(client != null && getOnlineClientByUUID(clientID) == null) 
				removeOnlineClient(client, MatchLeaveReasons.NEW_LOGIN);
			
			//If the second statement is not null, the client has already joined another match!
			Match match = getMatchByID(matchID);
			
			if(match != null) {
				
				if(match.isRunning()) {
					rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "The match is currently in progress. Please wait until the current round finishes and try again."));
					return;
				}
				
				match.join(client);
				onlineClients.add(client);
				dbSupplier.extendGuestUserLifespan(client);
				client.generateUniqueSessionID();
				
				JoinMatchResponse joinResponse = new JoinMatchResponse();
				joinResponse.matchID = match.matchID;
				joinResponse.sessionID = client.getSessionID();
				joinResponse.currentTurn = match.getCurrentTurn().model;
				
				ArrayList<ClientModel> coll = new ArrayList<>();
				for(Client c : match.getMembers()) 
					coll.add(c.model);
				joinResponse.joinedClients = coll.toArray(new ClientModel[coll.size()]);
				
				rsp.send(new Response(joinResponse));
				
			} else rsp.send(new ErrorResponse("", ResponseCodes.UNKNOWN_ERROR, "The match you are trying to join does not exist"));
		});
		
		//Requires the session id of the joined user. If the user is the host, a random other client is chosen and notified via event
		//Endpoints that require a session ID may return the error code 121 (session id expired)
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
				rsp.send(new ErrorResponse("", 100, "The session id is not valid or expired"));
				return;
			}
			
			Match match = getMatchBySessionID(c.getSessionID());
			if(match == null) { //This should not happen!
				rsp.send(new ErrorResponse("", 100, "The session id is not associated with a match. This should not happen. Don't worry it's not your fault :("));
				return;
			}
			
			try {
				match.leave(c, MatchLeaveReasons.REGULAR);
				removeOnlineClient(c, MatchLeaveReasons.REGULAR);
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
				rsp.send(new ErrorResponse("", 100, "The session id is not valid or expired"));
				return;
			}
			
			Match match = getMatchBySessionID(c.getSessionID());
			if(match == null) { //This should not happen!
				rsp.send(new ErrorResponse("", 100, "The session id is not associated with a match. This should not happen. Don't worry it's not your fault :("));
				return;
			}
			if(!match.getCurrentTurn().getSessionID().equals(c.getSessionID())) {
				rsp.send(new ErrorResponse("", 100, "You are not allowed to pass the dice to the next person! Please wait until it's your turn!"));
				return;
			}
			
			if(ctx.param("challenge").isSet() && c.alreadyRolled) {
				rsp.send(new ErrorResponse("", 100, "You have already accepted the previous roll! Please tell a number and pass the dice to the next person!"));
				return;
			}
			
			if(ctx.param("challenge").isSet()) {
				System.out.println("Trying to challenge");
				match.challenge(false);
				c.extendSessionLifetime();
			} else {
				
				//TODO should it be possible to pass the die without rolling?
				if(!c.alreadyRolled) {
					rsp.send(new ErrorResponse("", 100, "Before passing the die, please roll first!"));
					return;
				}
				
				int absoluteValue;
				
				try {
					absoluteValue = match.getAbsoluteDieValue(ctx.param("dieValue").intValue());
				} catch(Exception e) {
					rsp.send(new ErrorResponse("", 100, "Illegal dice value"));
					return;
				}
				
				if(absoluteValue < match.getCurrentToldAbsoluteRoll()) {
					rsp.send(new ErrorResponse("", ResponseCodes.TOLD_DICE_VALUE_TOO_LOW, "If you are already lying, you should really consider telling a higher number or are you intentionally trying to lose?"));
					return;
				}
				
				c.extendSessionLifetime();
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
				rsp.send(new ErrorResponse("", 100, "The session id is not valid or expired"));
				return;
			}
			Match match = getMatchBySessionID(c.getSessionID());
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
				rsp.send(new ErrorResponse("", ResponseCodes.WAIT_FOR_OTHERS_TO_JOIN, "Starting the match alone is a bit boring, isn't it? Wait for your friends to join first"));
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
			
			c.extendSessionLifetime();
			int absoluteRoll = match.roll();
			
			RollDiceResponse res = new RollDiceResponse();
			res.absolueValue = absoluteRoll;
			res.dieValues = match.getRollValue(absoluteRoll);
			
			rsp.send(new Response(res)); //Just send a generic success response
		});
		
		//TODO handle abrupt connection loss (Currently causes n exception and corrupts the match with the problematic client)
		//The heartbeat for all clients. It is used to synchronize the virtual clients on the server and the actual clients
		//A client needs to hit this URL with his associated session ID. If the client is in a match, the sync data is being sent every second
		sse("/heartbeat/{sessionID}", (ctx, sse) -> {
			
			String session = ctx.param("sessionID").value();
			Client c = getOnlineClientBySession(session);
			
			//TODO don't instantly close the connection. wait for a reconnect for example when the user refreshes the browser or has a poor internet connection. (It is possible, just wait for a heartbeat connect from the same user)
			sse.onClose(() -> {
				if(c != null) {
					
					logger.debug("Logged user cut the connection");
					logger.debug("Running cleanup routine for lost user ... " + c.model.displayName + " (" + c.model.uuid + ")");
					
					if(c.currentMatch != null)
						c.currentMatch.leave(c, MatchLeaveReasons.CONNECTION_LOSS);
					
					c.currentMatch = null;
					c.removeSessionID();
					c.emitter = null;
					
					removeOnlineClient(c, MatchLeaveReasons.CONNECTION_LOSS);
					
				}
				
				logger.debug("Connection to user terminated");
			});
			
			//If the request has no valid session id associated, just drop the connection
			if(c == null) {
				sse.close();
				return;
			}
			
			sse.keepAlive(5000);
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
