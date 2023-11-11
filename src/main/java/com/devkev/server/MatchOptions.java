package com.devkev.server;

//Holds static Match Data like ID, options etc,
public class MatchOptions {
	
	public enum GameMode {
		
		EASY((byte) 0), 				// No coins
		STAKE_AT_ROUND_START((byte) 1), // Coin stake at every round start
		STAKE_INCREASE((byte) 2); 		// Stake increases when the strak increases
		
		public byte numeric;
		GameMode(byte numeric) {
			this.numeric = numeric;
		}
		
		//Converts a numeric gamemode to the enum value
		//Unknown numeric returns "EASY" (No stake)
		public static final GameMode of(byte numeric) {
			for(GameMode gm : GameMode.values()) {
				if(gm.numeric == numeric)
					return gm;
			}
			return EASY;
		}
	}
	
	public boolean allowHints = true;
	public GameMode gameMode = GameMode.EASY;
	
	public MatchOptions() {
	}
}