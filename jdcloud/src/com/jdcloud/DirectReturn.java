package com.jdcloud;

@SuppressWarnings("serial")
public class DirectReturn extends RuntimeException {
	public Object retVal;
	public DirectReturn() {}
	public DirectReturn(Object retVal) {
		this.retVal = retVal;
	}
}
