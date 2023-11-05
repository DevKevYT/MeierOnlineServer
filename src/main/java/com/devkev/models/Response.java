package com.devkev.models;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Response {
	
	//This annotation prevents fields with this annotation to be seraialized
	//Used in the custom BUILDER field
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface ExcludeFromSerialization
	{
	}
	
	public interface ResponseCodes {
		public static final int SUCCESS = 0;
		public static final int UNKNOWN_ERROR = 100;
		public static final int UNKNOWN_FORM_DATA = 101;
		public static final int INVALID_CLIENT_ID = 120;
		public static final int INVALID_SESSION_ID = 122; //Unknown/invalid session id or expired session id
		public static final int TOLD_DICE_VALUE_TOO_LOW = 130;
		public static final int USERNAME_TOO_LONG = 110;
		public static final int USERNAME_CONTAINS_INVALID_CHARS = 111;
		public static final int WAIT_FOR_OTHERS_TO_JOIN = 102;
		public static final int NOT_ENOUGH_CREDITS_FOR_MATCH_CREATION = 103;
		public static final int NOT_ENOUGH_CREDITS_FOR_MATCH_JOIN = 104;
		
	}
	
	public static Gson GSON = new GsonBuilder().addSerializationExclusionStrategy(new ExclusionStrategy() {
		
		@Override
		public boolean shouldSkipField(FieldAttributes f) {
			 return f.getAnnotation(ExcludeFromSerialization.class) != null;
		}
		
		@Override
		public boolean shouldSkipClass(Class<?> clazz) {
			return false;
		}
	}).create();
	
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
		return GSON.toJson(this);
	}
}
