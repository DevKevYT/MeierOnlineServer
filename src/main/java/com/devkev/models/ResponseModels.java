package com.devkev.models;

public interface ResponseModels {

	public class CreateMatchResponse {
		
		public String sessionID;
		public String matchID;
		public String clientID;
		public String displayName;
		public int coins;
		
	}
	
	public class JoinMatchResponse {
		
		public ClientModel currentTurn;
		public String sessionID;
		public String matchID;
		
		public ClientModel[] joinedClients;
		
	}
	
	public class RollDiceResponse {
		
		public int dieValues;
		public int absolueValue;
		
		public ClientModel[] joinedClients;
		
	}
	
	public class ServerInfoResponse {
		
		public int playersPlaying;
		public int matchesInProgress;
		public String version;
		
	}
}
