package com.dn15.websocket.message;

public class HandshakeImplClient extends HandshakeImpl implements ClientHandshake {
	private String resourceDescriptor = "*";

	public HandshakeImplClient() {
	}

	public void setResourceDescriptor( String resourceDescriptor ) throws IllegalArgumentException {
		if(resourceDescriptor==null)
			throw new IllegalArgumentException( "http resource descriptor must not be null" );
		this.resourceDescriptor = resourceDescriptor;
	}

	public String getResourceDescriptor() {
		return resourceDescriptor;
	}
}
