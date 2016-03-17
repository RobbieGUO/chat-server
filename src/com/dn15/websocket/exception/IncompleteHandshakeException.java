package com.dn15.websocket.exception;

public class IncompleteHandshakeException extends RuntimeException {

	private static final long serialVersionUID = 7906596804233893092L;
	private int newsize;

	public IncompleteHandshakeException( int newsize ) {
		this.newsize = newsize;
	}

	public IncompleteHandshakeException() {
		this.newsize = 0;
	}

	public int getPreferedSize() {
		return newsize;
	}

}
