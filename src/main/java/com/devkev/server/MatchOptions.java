package com.devkev.server;

//Holds static Match Data like ID, options etc,
public class MatchOptions {
	
	public boolean allowHints = true;
	public boolean setStakeAtRoundStart = true; //TODO maybe define a "gamemode". If false, stake starts at 0 but draws the minimumstake for every turn until the winner is chosen 
	public boolean useStake = true; //If false, you can play for "fun" (Without coins)
	
	public MatchOptions() {
	}
}