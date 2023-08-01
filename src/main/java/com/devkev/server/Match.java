package com.devkev.server;

import java.util.ArrayList;
import java.util.Random;

import com.devkev.models.MatchEvents.JoinEvent;
import com.devkev.models.MatchEvents.MatchEvent;

//This class handles match logic for all connected clients
public class Match {
	
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
	int eventID = 0;
	ArrayList<MatchEvent> eventQueue = new ArrayList<MatchEvent>();
	
	private Match() {
		matchID = createUniqueID();
		MATCHES.add(this);
	}
	
	public static Match createMatch(Client host) {
		Match m = new Match();
		m.host = host;
		return m;
	}
	
	public int getMostrecentEventID() {
		return eventID;
	}
	
	public Client getHost() {
		return host;
	}
	
	public ArrayList<Client> getMembers() {
		return members;
	}
	
	public void join(Client client) {
		JoinEvent event = new JoinEvent(eventID);
		
		event.clientID = client.model.uuid;
		event.displayName = client.model.displayName;
		
		triggerEvent(event);
	}
	
	private int triggerEvent(MatchEvent event) {
		eventQueue.add(event);
		eventID++;
		
		for(Client c : members) {
			if(c.emitter != null) {
				c.emitter.event(event).id(eventID).send();
			}
		}
		
		return eventID;
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
