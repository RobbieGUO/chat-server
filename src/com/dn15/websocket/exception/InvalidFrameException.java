package com.dn15.websocket.exception;

import com.dn15.websocketapi.CloseReason.CloseCodes;

public class InvalidFrameException extends WsIOException {

	/**
	 * Serializable
	 */
	private static final long serialVersionUID = -9016496369828887591L;

	public InvalidFrameException() {
		super( CloseCodes.PROTOCOL_ERROR );
	}

	public InvalidFrameException( String arg0 ) {
		super( CloseCodes.PROTOCOL_ERROR, arg0 );
	}

	public InvalidFrameException( Throwable arg0 ) {
		super( CloseCodes.PROTOCOL_ERROR, arg0 );
	}

	public InvalidFrameException( String arg0 , Throwable arg1 ) {
		super( CloseCodes.PROTOCOL_ERROR, arg0, arg1 );
	}
}
