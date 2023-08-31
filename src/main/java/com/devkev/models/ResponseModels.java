package com.devkev.models;

public interface ResponseModels {

	public class CreateMatchResponse {
		
		public String sessionID;
		public String matchID;
		public String clientID;
		public String displayName;
		
	}
	
	public class JoinMatchResponse {
		
		public ClientModel currentTurn;
		public String sessionID;
		public ClientModel[] joinedClients;
		public String matchID;
		
	}
	
	public class RollDiceResponse {
		
		public int dieValues;
		public int absolueValue;
		
	}
	
	public class ServerInfoResponse {
		
		public int playersPlaying;
		public int matchesInProgress;
		
	}
}
