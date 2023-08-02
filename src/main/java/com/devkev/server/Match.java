package com.devkev.server;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.devkev.models.MatchEvents.JoinEvent;
import com.devkev.models.MatchEvents.MatchEvent;
import com.google.gson.Gson;

import io.netty.util.concurrent.ScheduledFuture;

//This class handles match logic for all connected clients
public class Match {
	
	//
	private ScheduledExecutorService retry = Executors.newScheduledThreadPool(1);
	
	//Ensure every instance of a match is unique
	public static final ArrayList<Match> MATCHES = new ArrayList<Match>();
	private static Random random = new Random();
	private static final String matchIDChars = "123456789";
	
	//MAtch id is numeric, but there could be leading zeroes
	public final String matchID;
	private ArrayList<Client> members = new ArrayList<>();
	private Client host;
	
	//Here goes the event queue. Everything that happens in this queue is being sent when syncing clients in this match using sse
	//Every event has an ID and every event is saved until a match completes (TODO)
	//The eventID is the index of the queue
	ArrayList<MatchEvent> eventQueue = new ArrayList<MatchEvent>();
	
	private Match() {
		matchID = createUniqueID();
		MATCHES.add(this);
	}
	
	public static Match createMatch(Client host) {
		Match m = new Match();
		m.host = host;
		m.members.add(host);
		return m;
	}
	
	public int getMostrecentEventID() {
		return eventQueue.size()-1;
	}
	
	public Client getHost() {
		return host;
	}
	
	public ArrayList<Client> getMembers() {
		return members;
	}
	
	public void join(Client client) {
		JoinEvent event = new JoinEvent(getMostrecentEventID());
		
		event.clientID = client.model.uuid;
		event.displayName = client.model.displayName;
		
		triggerEvent(event);
	}
	
	//TODO Wenn eine Nachricht nicht ankommt, sende so lange im Sekundentakt bis:
	//Ein neues Event paassiert (Sende das alte mit, falls es gebraucht wird) oder ein Timeout erreicht ist und der Client aus dem Match gekickt wird.
	private int triggerEvent(MatchEvent event) {
		
		eventQueue.add(event);
		
		for(Client c : members) {
			
			System.out.println("Sending event to " + c.model.displayName);
			
			if(c.emitter != null && !c.lostConnection) {
				if(c.emitter.event(event).id(getMostrecentEventID()).send().isCompletedExceptionally()) {
					
					System.out.println("Failed to send event to client: " + c.model.displayName);
					
					c.lastEventID = getMostrecentEventID();
					c.lostConnection = true;
					
					//If we fail to transmit, handle it like described above
					java.util.concurrent.ScheduledFuture<?> future = retry.scheduleAtFixedRate(() -> {
						if(!c.lostConnection)  return; //We can't cancel the event, so just do nothing for the remainder of the retry TODO better solution
						
						
						//If we are successful here, set lostConnection to false
						ArrayList<MatchEvent> collected = new ArrayList<MatchEvent>(); 
						for(int i = c.lastEventID; i < getMostrecentEventID(); i++) {
							System.out.println("Resending event " + i + " to client " + c.model.displayName);
							collected.add(eventQueue.get(i));
						}
						
						if(!c.emitter.event(new Gson().toJson(collected)).id(getMostrecentEventID()).send().isCompletedExceptionally()) {
							System.out.println("Client " + c.model.displayName  + " reconnected!");
							c.lostConnection = false;
						}
						
					}, 0, 1, TimeUnit.SECONDS);
					
					retry.schedule(() -> { //Ugly and not resource friendly solution, but does the trick
						System.out.println("Cancelling timeout event for " + c.model.displayName);
						future.cancel(true);
					}, 10, TimeUnit.SECONDS);
				}
			}
		}
		
		return getMostrecentEventID();
	}
	
	private static String createUniqueID() {
		do {
			
			StringBuilder generated = new StringBuilder();
			for(int i = 0; i < 4; i++) {
				generated.append(matchIDChars.charAt(random.nextInt(3)));
			}
			for(Match m : MATCHES) {
				if(generated.toString().equals(m.matchID)) {
					continue;
				}
			}
			return generated.toString();
			
		} while(true);
	}
}
