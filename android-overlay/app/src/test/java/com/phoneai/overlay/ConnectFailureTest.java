package com.phoneai.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

public class ConnectFailureTest {
    @Test
    public void connectProblemsCountAsUnreachable() {
        assertTrue(FileTools.isConnectFailure(new java.net.ConnectException("Connection refused")));
        assertTrue(FileTools.isConnectFailure(new java.net.NoRouteToHostException("No route")));
        assertTrue(FileTools.isConnectFailure(new java.net.SocketTimeoutException("connect timed out")));
        assertTrue(FileTools.isConnectFailure(new IOException("failed to connect to /172.23.4.118 (port 47321) from /172.23.2.182 (port 40000) after 4000ms")));
        assertTrue(FileTools.isConnectFailure(new IOException("wrapped", new java.net.ConnectException("refused"))));
    }

    @Test
    public void midTransferFailuresDoNot() {
        assertFalse(FileTools.isConnectFailure(new java.net.SocketTimeoutException("Read timed out")));
        assertFalse(FileTools.isConnectFailure(new IOException("Connection reset")));
        assertFalse(FileTools.isConnectFailure(new IOException("upload size mismatch")));
        assertFalse(FileTools.isConnectFailure(new IOException("")));
        assertFalse(FileTools.isConnectFailure(null));
    }
}
