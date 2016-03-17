package com.dn15.websocketapi;

import com.dn15.websocket.Session;

/**
 * @author TonyHong
 * 
 */
public abstract class Endpoint {

    public abstract void onOpen(Session session);

    public void onClose(Session session, String closeReason) {
    }

    public void onError(Session session, Throwable thr) {
    }

}
