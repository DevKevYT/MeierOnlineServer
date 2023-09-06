package com.devkev.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


import com.devkev.models.ClientModel;
import com.devkev.models.MatchEvents.HostPromotion;
import com.devkev.models.MatchEvents.JoinEvent;
import com.devkev.models.MatchEvents.LeaveEvent;
import com.devkev.models.MatchEvents.MatchEvent;
import com.devkev.models.MatchEvents.MatchEvent.Scope;
import com.devkev.models.MatchEvents.NewTurnEvent;
import com.devkev.models.MatchEvents.RoundCancelledEvent;
import com.devkev.models.MatchEvents.RoundFinishEvent;
import com.google.gson.Gson;

//This class handles match logic for all connected clients
public class Match {
	
	//There is always just one persons turn. 
	private static final ScheduledExecutorService TIMEOUT_SCEDULER = Executors.newScheduledThreadPool(1);
	
	public static final int TURN_AFK_TIMEOUT = 60;
	
	//Reasons, why a client left an active match
	public enum MatchLeaveReasons {
		UNKNOWN,
		REGULAR,  //The user pressed the "leave" button ...
		KICKED, 
		SESSION_EXPIRED, 
		CONNECTION_LOSS, //aka "afk"
		NEW_LOGIN
	}
	
	private ScheduledExecutorService retry = Executors.newScheduledThreadPool(1);
	
	//Ensure every instance of a match is unique
	public static final List<Match> MATCHES = Collections.synchronizedList(new ArrayList<Match>());
	private static Random random = new Random();
	private static final String matchIDChars = "123456789";
	
	//MAtch id is numeric, but there could be leading zeroes
	public final String matchID;
	private ArrayList<Client> members = new ArrayList<>();
	
	private boolean roundInProgress = false; //The match starts (initially), when the host triggers an endpoint or the last loser. If a round is in progress, nobody can join
	
	private Client currentTurn;
	private ScheduledFuture<?> currentTurnTimeout;
	
	private String prevTurnClientID; //Cannot "swich" objects that easily like primitivy datatypes
	
	private int turnCounter = 0; //Always increment if someone turns. You can calculate the index of the actual client
	private int streak = 0; //The more rounds we pass, the higher the stake will be
	
	private int actualAbsoluteValue;
	private int toldAbsoluteValue; //May not be true
	
	private Client host;
	
	//Increments every time, a round is finished
	private int currentRound = 0;
	//Create a lookup table for the actual values and combinations
	//Just add the numbers mathematically and the output is the second value
	public static final HashMap<Integer, Integer> lookup = new HashMap<>();
	
	//TODO das kann man doch etwas sch�ner machen!
	static {
		lookup.put(1, 31);
		lookup.put(2, 32);
		lookup.put(3, 41);
		lookup.put(4, 42);
		lookup.put(5, 43);
		lookup.put(6, 51);
		lookup.put(7, 52);
		lookup.put(8, 53);
		lookup.put(9, 54);
		lookup.put(10, 54);
		lookup.put(11, 61);
		lookup.put(12, 62);
		lookup.put(13, 63);
		lookup.put(14, 64);
		lookup.put(15, 65);
		//Pasch
		lookup.put(16, 11);
		lookup.put(17, 22);
		lookup.put(18, 33);
		lookup.put(19, 44);
		lookup.put(20, 55);
		lookup.put(21, 66);
		//Meyer!
		lookup.put(22, 21);
	}
	
	//Here goes the event queue. Everything that happens in this queue is being sent when syncing clients in this match using sse
	//Every event has an ID and every event is saved until a match completes (TODO)
	//The eventID is the index of the queue
	ArrayList<MatchEvent> eventQueue = new ArrayList<MatchEvent>();
	
	private Match() {
		matchID = createUniqueID();
		
		synchronized (MATCHES) {
			MATCHES.add(this);			
		}
	}
	
	public static Match createMatch(Client host) {
		Match m = new Match();
		m.host = host;
		m.currentTurn = host;
		m.members.add(host);
		host.currentMatch = m;
		return m;
	}
	
	/**If the current round is in progress. People need to wait if they want to join*/
	public boolean isRunning() { 
		return roundInProgress;
	}
	
	private Client getMemberByUUID(String uuid) {
		for(Client c : members) {
			if(c.model.uuid.equals(uuid)) return c;
		}
		return null;
	}
	
	public boolean allowedToStart(Client c) {
		return currentTurn == null ? (c.model.uuid.equals(getHost().model.uuid)) : currentTurn.model.uuid.equals(c.model.uuid);
	}
	
	public int getAbsoluteDieValue(int roll) throws Exception {
		for(Integer value : lookup.keySet()) {
			if(lookup.get(value) == roll) {
				return value;
			}
		}
		
		throw new Exception("Illegal roll value: " + roll);
	}
	
	public int getRollValue(int absoluteValue) {
		return lookup.get(absoluteValue);
	}
	
	public Client getCurrentTurn()  {
		return currentTurn;
	}
	
	//Only the person whos turn it is currently can start the match
	public void start() {
		prevTurnClientID = "";
		roundInProgress = true;
		
		NewTurnEvent event = new NewTurnEvent(getMostrecentEventID());
		event.currentLosses = currentTurn.model.matchLosses;
		event.currentWins = currentTurn.model.matchWins;
		event.clientID = currentTurn.model.uuid;
		event.displayName = currentTurn.model.displayName;
		event.streak = 0;
		event.prevClientID = "";
		event.prevDisplayName = "";
		event.prevLosses = 0;
		event.prevWins = 0;
		triggerEvent(event);
		
		setTimeoutScedulerForCurrentTurn();
	}
	
	public int roll() {
		
		currentTurn.alreadyRolled = true;
		
		rollDice();
		System.out.println("Rolled: " + actualAbsoluteValue);
		return actualAbsoluteValue;
		//Keep the previous values hidden
		//NewTurnDieValueEvent roll = new NewTurnDieValueEvent(getMostrecentEventID());
		//roll.dieValues = getRollValue(actualAbsoluteValue);
		//roll.absolueValue = actualAbsoluteValue;
		//triggerEvent(roll, currentTurn);
	}
	
	private void cancelTimeoutSceduler() {
		if(currentTurnTimeout != null) {
			System.out.println("Cancelling timeout sceduler for " + currentTurn.model.displayName);
			if(currentTurnTimeout.cancel(true)) {
				System.out.println("Failed to cancel current turn task. The next person should get kicked. But it's not his fault :(");
			}
		}
	}
	
	private void setTimeoutScedulerForCurrentTurn() {
		if(currentTurnTimeout != null) {
			System.out.println("Cancelling timeout sceduler for " + currentTurn.model.displayName);
			if(currentTurnTimeout.cancel(true)) {
				System.out.println("Failed to cancel current turn task. The next person should get kicked. But it's not his fault :(");
			}
		}
		
		System.out.println("Timeout sceuled for " + currentTurn.model.displayName);
		currentTurnTimeout = TIMEOUT_SCEDULER.schedule(() -> {
			System.out.println("TIMEOUT!");
			//If this gets triggered, before interruped. The person who's turn it is either passes the cup telling the truth or automatically loses
			try {
				challenge(true);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}, TURN_AFK_TIMEOUT, TimeUnit.SECONDS);
	}
	
	public void next(int toldDieAbsoluteValue) throws Exception {
		
		toldAbsoluteValue = toldDieAbsoluteValue;
		
		turnCounter++;
		streak++;
		
		NewTurnEvent event = new NewTurnEvent(getMostrecentEventID());
		event.prevLosses = currentTurn.model.matchLosses;
		event.prevWins = currentTurn.model.matchWins;
		event.prevClientID = currentTurn.model.uuid;
		event.prevDisplayName = currentTurn.model.displayName;
		event.streak = streak;
		
		currentTurn.alreadyRolled = false;
		prevTurnClientID = currentTurn.model.uuid;
		
		Client next = members.get(turnCounter % members.size());
		
		if(next.model.uuid.equals(prevTurnClientID) && members.size() > 1) { //Prevent to be your next turn but only if the host is not alone
			turnCounter++;
			next = members.get(turnCounter % members.size());
		}
		
		currentTurn = next;
		
		event.currentLosses = next.model.matchLosses;
		event.currentWins = next.model.matchWins;
		event.clientID = next.model.uuid;
		event.displayName = next.model.displayName;
		event.toldDieAbsoluteValue = toldAbsoluteValue;
		event.toldDieRoll = getRollValue(toldDieAbsoluteValue);
		
		triggerEvent(event);
		setTimeoutScedulerForCurrentTurn();
	}
	
	//This function can make a client lose! Calling this function always ends the current round!
	//TODO How about the case someone lies, but the value is actually higher? Should he win instead? Also make fun rules
	//If timeout is true, the callenge was initiated by someone being afk
	public void challenge(boolean timeout) {
		
		Client challenger = getMemberByUUID(prevTurnClientID);
		
		//Just cancel the match, because someone was afk! Trigger a cancel round event. Nobody lost or won TODO The afk player gets a lose point!
		if(challenger == null && timeout) {
			currentTurn = getHost();
			endRound(getHost());
			
			RoundCancelledEvent event = new RoundCancelledEvent(getMostrecentEventID());
			event.reason = "The person who";
			event.newTurn = currentTurn.model;
			triggerEvent(event);
			
			return;
		}
		
		Client winner;
		Client loser;
		
		System.out.println("Trying to challenge " + toldAbsoluteValue + " from " + challenger.model.displayName +  " against " + actualAbsoluteValue + " from " + currentTurn.model.uuid);
		
		if(toldAbsoluteValue == actualAbsoluteValue) {
			System.out.println(currentTurn.model.displayName + " lost! ");
			winner = challenger;
			loser = currentTurn;
		} else if(toldAbsoluteValue < actualAbsoluteValue) {
			System.out.println(currentTurn.model.displayName + " should lose, but the told value is less than the actual! Second chance");
			
			winner = challenger;
			loser = currentTurn;
		} else {
			System.out.println(prevTurnClientID + " lost! ");
			winner = currentTurn;
			loser = challenger;
		}
		
		currentRound++;
		
		winner.model.matchWins++;
		loser.model.matchLosses++;
		
		RoundFinishEvent event = new RoundFinishEvent(getMostrecentEventID());
		event.callengeBecauseAFK = timeout;
		event.actualDieAbsoluteValue = actualAbsoluteValue;
		event.actualDieRoll = getRollValue(actualAbsoluteValue);
		event.winner = winner.model;
		event.loser = loser.model;
		event.isMeyer = toldAbsoluteValue == 22;
		event.streak = streak;
		event.toldDieAbsoluteValue = toldAbsoluteValue;
		event.toldDieRoll = getRollValue(toldAbsoluteValue);
		event.currentRound = currentRound;
		triggerEvent(event);
		
		//TODO round end message
		System.out.println("The round finishes! " + currentTurn.model.displayName + " drinks and starts the next round!");
		
		endRound(loser);
		cancelTimeoutSceduler();
	}
	
	private void endRound(Client firstTurn) {
		currentTurn = firstTurn;
		toldAbsoluteValue = 0;
		actualAbsoluteValue = 0;
		streak = 0;
		roundInProgress = false;
		prevTurnClientID = "";
		
		for(Client c : members) {
			c.alreadyRolled = false;
		}
	}
	
	public int getCurrentToldAbsoluteRoll() {
		return toldAbsoluteValue;
	}
	
	public int getStreak() {
		return streak;
	}
	
	private void rollDice() {
		actualAbsoluteValue = random.nextInt(22) + 1;
		System.out.println("Rolled: " +  actualAbsoluteValue);
	}
	
	public void deleteMatch() {
		synchronized (MATCHES) {
			MATCHES.remove(this);
		}
	}
	
	public int getMostrecentEventID() {
		return eventQueue.size();
	}
	
	public Client getHost() {
		return host;
	}
	
	public ArrayList<Client> getMembers() {
		return members;
	}
	
	private void setHost(Client newHost) {
		this.host = newHost;
	}
	
	public void join(Client client) {
		client.currentMatch = this;
		members.add(client);
		
		JoinEvent event = new JoinEvent(getMostrecentEventID());
		event.clientID = client.model.uuid;
		event.displayName = client.model.displayName;
		event.currentTurnID = currentTurn.model.uuid;
		
		ArrayList<ClientModel> members = new ArrayList<>();
		for(Client c : getMembers()) members.add(c.model);
		event.currentMembers = members.toArray(new ClientModel[members.size()]);
		
		triggerEvent(event);
	}
	
	//TODO pass the turn, if the person who's turn it was leaves
	public void leave(Client client, MatchLeaveReasons reason) throws Exception {
		
		client.currentMatch = null;
		client.removeSessionID();
		client.lastEventID = 0;
		
		System.out.println("Client " + client.model.displayName + " removed from match. " + (members.size()-1) + " left!");
		
		LeaveEvent leave = new LeaveEvent(getMostrecentEventID());
		leave.clientID = client.model.uuid;
		leave.displayName = client.model.displayName;
		leave.reason = reason;
		
		ArrayList<ClientModel> leftOver = new ArrayList<>();
		for(Client c : getMembers()) {
			if(!c.model.uuid.equals(client.model.uuid)) leftOver.add(c.model);
		}
		leave.currentMembers = leftOver.toArray(new ClientModel[members.size()]);
		
		triggerEvent(leave);
		
		for(Client m : members) {
			if(m.model.uuid.equals(client.model.uuid)) {
				members.remove(m);
				break;
			}
		}
		
		//The emitter is null, if the client (for whatever reason) never hit the /heartbeat endpoint when joining a match!
		if(client.emitter != null)
			client.emitter.close();
		
		//if no members are left, just remove itself
		if(members.size() == 0) {
			System.out.println("No members left. Removing. " + MATCHES.size() + " matches currently running");
			deleteMatch();
			return;
		}
		
		if(client.model.uuid.equals(host.model.uuid)) {
			//Chose another one to be the host
			Random r = new Random();
			setHost(members.get(r.nextInt(members.size())));
			System.out.println("The host left the match. " + getHost().model.displayName + " is the new host");
			
			HostPromotion promo = new HostPromotion(getMostrecentEventID());
			promo.clientID = getHost().model.uuid;
			promo.displayName = getHost().model.displayName;
			triggerEvent(promo);
		}
		
		//Breche die aktuelle Runde einfach ab
		if(client.model.uuid.equals(currentTurn.model.uuid)) {
			System.out.println("The current turn left the match. Starting a new round");
			
			currentTurn = getHost();
			endRound(getHost());
			
			RoundCancelledEvent event = new RoundCancelledEvent(getMostrecentEventID());
			event.reason = "Someone left the match while we were waiting for his turn.";
			event.newTurn = currentTurn.model;
			triggerEvent(event);
		}
		
	}
	
	private int triggerEvent(MatchEvent event) {
		return triggerEvent(event, null);
	}
	
	//TODO Wenn eine Nachricht nicht ankommt, sende so lange im Sekundentakt bis:
	//Ein neues Event paassiert (Sende das alte mit, falls es gebraucht wird) oder ein Timeout erreicht ist und der Client aus dem Match gekickt wird.
	//TODO It is also possible to send an event to a specific client
	private int triggerEvent(MatchEvent event, Client target) {
		
		if(target == null && event.scope == Scope.SINGLE) 
			throw new IllegalAccessError("Cannot call the event " + event.EVENT_ID + " to a single client when this client is not being specified!");
		
		eventQueue.add(event);
		
		if(event.scope == Scope.EVERYONE) {
			for(Client c : members) {
				triggerEventForSingleClient(event, c);
			}
		} else {
			triggerEventForSingleClient(event, target);
		}
		
		return getMostrecentEventID();
	}
	
	private void triggerEventForSingleClient(MatchEvent event, Client c) {
		System.out.println("Sending event to " + c.model.displayName + new Gson().toJson(event));
		
		if(c.emitter != null && !c.lostConnection) {
			if(c.emitter.event(event.toString()).id(getMostrecentEventID()).name(event.eventName).send().isCompletedExceptionally()) {
				
				System.out.println("Failed to send event to client: " + c.model.displayName + " client lost the connection!");
				
				c.lastEventID = getMostrecentEventID();
				c.lostConnection = true;
				
				//If we fail to transmit, handle it like described above
				java.util.concurrent.ScheduledFuture<?> future = retry.scheduleAtFixedRate(() -> {
					if(!c.lostConnection)  return; //We can't cancel the event, so just do nothing for the remainder of the retry TODO better solution
					
					
					//If we are successful here, set lostConnection to false
					ArrayList<MatchEvent> collected = new ArrayList<MatchEvent>(); 
					for(int i = c.lastEventID; i < getMostrecentEventID(); i++) {
						System.out.println("Resending event " + i + " to client " + c.model.displayName);
						if(!c.emitter.event(new Gson().toJson(collected)).id(getMostrecentEventID()).name(event.eventName).send().isCompletedExceptionally()) {
							if(i == 0) {
								//Only the first event should be send. Otherwise, the client would be confused. Send the rest
								c.lostConnection = false;
							}
						}
					}
					
					
				}, 0, 1, TimeUnit.SECONDS);
				
				retry.schedule(() -> { //Ugly and not resource friendly solution, but does the trick
					System.out.println("Cancelling timeout event for " + c.model.displayName);
					future.cancel(true);
				}, 10, TimeUnit.SECONDS);
			}
		}
	}
	
	private static String createUniqueID() {
		do {
			
			StringBuilder generated = new StringBuilder();
			for(int i = 0; i < 4; i++) {
				generated.append(matchIDChars.charAt(random.nextInt(matchIDChars.length())));
			}
			synchronized (MATCHES) {
				for(Match m : MATCHES) {
					if(generated.toString().equals(m.matchID)) {
						continue;
					}
				}
			}
			return generated.toString();
			
		} while(true);
	}
}
