package com.devkev.models;

public interface ResponseModels {

	public class CreateMatchResponse {
		
		public String sessionID;
		public String matchID;
		public String clientID;
		public String displayName;
		
	}
	
	public class JoinMatchResponse {
		
		public String sessionID;
		public ClientModel[] joinedClients;
		public String matchID;
		
	}
}
