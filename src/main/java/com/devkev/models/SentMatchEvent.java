package com.devkev.models;

import java.util.ArrayList;

import com.devkev.models.MatchEvents.MatchEvent;
import com.devkev.server.Client;

/**Can be used for a list of events including receiver and other stuff*/
public class SentMatchEvent {
	
	private ClientModel[] receiver; //Just save the model to prevent unnessecary references when a client leaves the match for example
	private MatchEvent eventData;
	
	public SentMatchEvent(MatchEvent event, Client ... receiver) {
		this.eventData = event;
		
		this.receiver = new ClientModel[receiver.length];
		for(int i = 0; i < receiver.length; i++) {
			this.receiver[i] = receiver[i].model;
		}
	}
	
	public SentMatchEvent(MatchEvent event, ArrayList<Client> receiver) {
		this.eventData = event;
		
		this.receiver = new ClientModel[receiver.size()];
		for(int i = 0; i < receiver.size(); i++) {
			this.receiver[i] = receiver.get(i).model;
		}
	}
	
	public ClientModel[] getReceiver() {
		return receiver;
	}
	
	public boolean hasReceived(ClientModel model) {
		for(ClientModel r : receiver) {
			if(r.uuid.equals(model.uuid)) return true;
		}
		return false;
	}
	
	public MatchEvent getEventData() {
		return eventData;
	}
	
}
