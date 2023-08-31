package com.devkev.models;

import com.google.gson.Gson;

public class Response {
	
	public interface ResponseCodes {
		public static final int SUCCESS = 0;
		public static final int UNKNOWN_ERROR = 100;
		public static final int UNKNOWN_FORM_DATA = 101;
		public static final int INVALID_CLIENT_ID = 120;
		public static final int INVALID_SESSION_ID = 122; //Unknown/invalid session id or expired session id
		public static final int TOLD_DICE_VALUE_TOO_LOW = 130;
		public static final int USERNAME_TOO_LONG = 110;
		public static final int USERNAME_CONTAINS_INVALID_CHARS = 111;
		
	}
	
	public final int code;
	public final Object data;
	
	public Response(Object data, int responseCode) {
		this.data = data;
		this.code = responseCode;
	}
	
	public Response(Object data) {
		this.data = data;
		this.code = ResponseCodes.SUCCESS;
	}
	
	public String toString() {
		return new Gson().toJson(this);
	}
}
