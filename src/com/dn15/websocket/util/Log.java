package com.dn15.websocket.util;

public class Log {
    private static final int logLevel = 1;

    public static final void e(Exception e) {
        e.printStackTrace();
    }

    public static final void i(String i) {
        if (logLevel > 0)
            System.out.println(i);
    }

}
