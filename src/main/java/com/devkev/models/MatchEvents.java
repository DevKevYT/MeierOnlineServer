package com.devkev.models;

import com.google.gson.Gson;

public interface MatchEvents {
	
	public abstract class MatchEvent {
		
		public final int EVENT_ID;
		public final String eventName;
		
		public MatchEvent(int eventID, String eventName) {
			this.EVENT_ID = eventID;
			this.eventName = eventName;
		}
		
		public String toString() {
			return new Gson().toJson(this);
		}
		
	}
	
	public class JoinEvent extends MatchEvent {
		
		public String clientID;
		public String displayName;
		
		public JoinEvent(int eventID) {
			super(eventID, "match-join");
		}
	}
	
	public class LeaveEvent extends MatchEvent {

		public String displayName;
		public String clientID;
		
		public LeaveEvent(int eventID) {
			super(eventID, "match-leave");
		}
		
	}
	
}



