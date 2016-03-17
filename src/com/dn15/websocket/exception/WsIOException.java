/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.dn15.websocket.exception;

import java.io.IOException;

import com.dn15.websocket.message.CloseMessage;
import com.dn15.websocketapi.CloseReason;
import com.dn15.websocketapi.CloseReason.CloseCode;
import com.dn15.websocketapi.CloseReason.CloseCodes;

/**
 * Allows the WebSocket implementation to throw an {@link IOException} that
 * includes a {@link CloseReason} specific to the error that can be passed back
 * to the client.
 */
public class WsIOException extends IOException {

    private static final long serialVersionUID = 1L;

    private final CloseReason closeReason;
    
    private Throwable exception;

    public WsIOException(CloseReason closeReason) {
        this.closeReason = closeReason;
    }
    
    public WsIOException(CloseCodes closeMessage) {
        closeReason = new CloseReason(closeMessage, "");
    }
    
    public WsIOException(CloseCodes closeMessage, String reasonPhrase) {
        closeReason = new CloseReason(closeMessage, reasonPhrase);
    }
    
    public WsIOException(CloseCodes closeMessage, Throwable t) {
        closeReason = new CloseReason(closeMessage, "");
        exception = t;
    }
    
    public WsIOException(CloseCodes closeMessage, String reasonPhrase, Throwable t) {
        closeReason = new CloseReason(closeMessage, reasonPhrase);
        exception = t;
    }
    
    public CloseReason getCloseReason() {
        return closeReason;
    }
    
    public CloseCode getCloseCode() {
        return closeReason.getCloseCode();
    }

    public Throwable getException() {
        return exception;
    }
}
