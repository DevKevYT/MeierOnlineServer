package com.devkev.server;

import com.devkev.devscript.raw.Block;
import com.devkev.devscript.raw.Command;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Process;

public class Commands extends Library {

	public Commands() {
		super("cmd");
	}

	@Override
	public Command[] createLib() {
		return new Command[] {
				new Command("", "", "") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						return null;
					}
				}
		};
	}

	@Override
	public void scriptExit(Process arg0, int arg1, String arg2) {
	}

	@Override
	public void scriptImport(Process arg0) {
	}

}
