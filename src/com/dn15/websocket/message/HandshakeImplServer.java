package com.dn15.websocket.message;

public class HandshakeImplServer extends HandshakeImpl implements ServerHandshake {
	private short httpstatus;
	private String httpstatusmessage;

	public HandshakeImplServer() {
	}

	public String getHttpStatusMessage() {
		return httpstatusmessage;
	}

	public short getHttpStatus() {
		return httpstatus;
	}

	public void setHttpStatusMessage( String message ) {
		this.httpstatusmessage = message;
	}

	public void setHttpStatus( short status ) {
		httpstatus = status;
	}

}
