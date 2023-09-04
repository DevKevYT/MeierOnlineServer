package com.devkev.models;

import com.devkev.server.Match.MatchLeaveReasons;
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
		public String currentTurnID;
		public ClientModel[] currentMembers;
		
		public JoinEvent(int eventID) {
			super(eventID, "match-join");
		}
	}
	
	public class LeaveEvent extends MatchEvent {

		public String displayName;
		public String clientID;
		public ClientModel[] currentMembers; //Members without the one who just left
		
		public MatchLeaveReasons reason;
		
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
	
	//Tell everyone who's turn it is now and 
	public class NewTurnEvent extends MatchEvent {

		public int currentWins;
		public int currentLosses;
		public String clientID;
		public String displayName;
		
		public int prevWins;
		public int prevLosses;
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
		
		public boolean callengeBecauseAFK = false;
		public boolean isMeyer = false;
		
		public int toldDieAbsoluteValue;
		public int toldDieRoll;
		
		public int actualDieAbsoluteValue;
		public int actualDieRoll;
		
		public int streak;
		
		public ClientModel loser;
		public ClientModel winner;
		
		public int currentRound;
		
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
	
	public class TurnTimeoutEvent extends MatchEvent {

		public ClientModel currentTurn; //the person who was afk
		public ClientModel nextPerson; 
		
		//The person who's turn it was will just get skipped. 
		//If there are only two persons in the match, the match gets finished and the afk dude is the loser
		
		public TurnTimeoutEvent(int eventID) {
			super(eventID, "turn-timeout");
		}
		
	}
	
}



