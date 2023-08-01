package com.devkev.models;

import com.google.gson.Gson;

public interface MatchEvents {
	
	public abstract class MatchEvent {
		
		public final int EVENT_ID;
		
		public MatchEvent(int eventID) {
			this.EVENT_ID = eventID;
		}
		
		public String toString() {
			return new Gson().toJson(this);
		}
		
	}
	
	public class JoinEvent extends MatchEvent {
		
		public JoinEvent(int eventID) {
			super(eventID);
		}
		
		public String clientID;
		public String displayName;
		
	}
	
}



