package com.devkev.models;

import com.google.gson.Gson;

public interface MatchEvents {
	
	public abstract class MatchEvent {
		
		public final class Scope {
			public static final byte EVERYONE = 0;
			public static final byte SINGLE = 1;
		}
		
		public final int EVENT_ID;
		public final String eventName;
		
		public byte scope = Scope.EVERYONE; //0 = everyone, 1 = single client
		
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
		public ClientModel[] currentMembers;
		
		public JoinEvent(int eventID) {
			super(eventID, "match-join");
		}
	}
	
	public class LeaveEvent extends MatchEvent {

		public String displayName;
		public String clientID;
		public ClientModel[] currentMembers; //Members without the one who just left
		
		public LeaveEvent(int eventID) {
			super(eventID, "match-leave");
		}
		
	}
	
	public class HostPromotion extends MatchEvent {

		public String displayName;
		public String clientID;
		
		public HostPromotion(int eventID) {
			super(eventID, "host-promotion");
		}
		
	}
	
	//Secret event, only the one receives who's turn it is
	public class NewTurnDieValueEvent extends MatchEvent {

		public int dieValues;
		public int absolueValue;
		
		public NewTurnDieValueEvent(int eventID) {
			super(eventID, "new-turn-die-value");
			super.scope = Scope.SINGLE;
		}
		
	}
	
	//Tell everyone who's turn it is now and 
	public class NewTurnEvent extends MatchEvent {

		public String clientID;
		public String displayName;
		public String prevClientID;
		public String prevDisplayName;
		public int streak;
		
		public int toldDieAbsoluteValue;
		public int toldDieRoll;
		
		public NewTurnEvent(int eventID) {
			super(eventID, "new-turn");
		}
		
	}
	
	public class RoundFinishEvent extends MatchEvent {
		
		public boolean isMeyer = false;
		
		public int toldDieAbsoluteValue;
		public int toldDieRoll;
		
		public int actualDieAbsoluteValue;
		public int actualDieRoll;
		
		public int streak;
		
		public ClientModel loser;
		public ClientModel winner;
		
		public RoundFinishEvent(int eventID) {
			super(eventID, "finish-match");
		}
		
	}
	
	public class RoundCancelledEvent extends MatchEvent {

		public String reason;
		public ClientModel newTurn; //The one who starts the next round
		
		public RoundCancelledEvent(int eventID) {
			super(eventID, "match-cancelled");
		}
		
	}
	
}



