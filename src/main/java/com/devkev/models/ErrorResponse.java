package com.devkev.models;

public class ErrorResponse extends Response {

	public String errorMessage;
	
	public ErrorResponse(Object data, int code, String errorMessage) {
		super(data, code);
		this.errorMessage = errorMessage;
	}

}
