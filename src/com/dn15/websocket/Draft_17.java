package com.dn15.websocket;

import com.dn15.websocket.exception.InvalidHandshakeException;
import com.dn15.websocket.message.ClientHandshake;


public class Draft_17 extends Draft_10 {
	@Override
	public HandshakeState acceptHandshake( ClientHandshake handshakedata ) throws InvalidHandshakeException {
		int v = readVersion( handshakedata );
		if( v == 13 )
			return HandshakeState.MATCHED;
		return HandshakeState.NOT_MATCHED;
	}

	@Override
	public ClientHandshake postProcessHandshakeRequest( ClientHandshake request ) {
		super.postProcessHandshakeRequest( request );
		request.put( "Sec-WebSocket-Version", "13" );// overwriting the previous
		return request;
	}

	@Override
	public WebSocketProtocol copyInstance() {
		return new Draft_17();
	}

}
