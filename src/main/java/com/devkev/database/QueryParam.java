package com.devkev.database;

public class QueryParam<T> {

	private T data;
	
	private QueryParam() {}
	
	public static final QueryParam<String> of(String string) {
		QueryParam<String> p = new QueryParam<String>();
		p.data = string;
		return p;
	}
	
	public static final QueryParam<Integer> of(int integer) {
		QueryParam<Integer> p = new QueryParam<Integer>();
		p.data = integer;
		return p;
	}
	
	public static final QueryParam<Long> of(long value) {
		QueryParam<Long> p = new QueryParam<Long>();
		p.data = value;
		return p;
	}
	
	public T getData() {
		return data;
	}
}
