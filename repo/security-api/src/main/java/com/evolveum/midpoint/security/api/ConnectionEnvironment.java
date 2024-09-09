/*
 * Copyright (c) 2016 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.security.api;

/**
 * @author semancik
 *
 */
public class ConnectionEnvironment {

    private String channel;
    private HttpConnectionInformation connectionInformation;
    /**
     * There are situations when we need to force the use of session ID other than the one derived from the HTTP session.
     * An example of this is REST service auditing. However, correct functioning of this mechanism requires that the
     * REST service does not use HTTP sessions at all; otherwise, both HTTP and artificial session IDs would get used
     * (task ID for login/logout and HTTP session ID for operations).
     */
    private String sessionIdOverride;

    private String sequenceIdentifier;
    private String moduleIdentifier;

    // probably not of much use
    public ConnectionEnvironment() {
    }

    public ConnectionEnvironment(String channel, HttpConnectionInformation connectionInformation) {
        this.channel = channel;
        this.connectionInformation = connectionInformation;
    }

    // This is not a constructor to make client realize there's some processing in it.
    public static ConnectionEnvironment create(String channel) {
        return new ConnectionEnvironment(channel, SecurityUtil.getCurrentConnectionInformation());
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSessionIdOverride() {
        return sessionIdOverride;
    }

    public void setSessionIdOverride(String sessionIdOverride) {
        this.sessionIdOverride = sessionIdOverride;
    }

    public HttpConnectionInformation getConnectionInformation() {
        return connectionInformation;
    }

    public String getRemoteHostAddress() {
        return connectionInformation != null ? connectionInformation.getRemoteHostAddress() : null;
    }

    public String getSessionId() {
        if (sessionIdOverride != null) {
            return sessionIdOverride;
        } else if (connectionInformation != null) {
            return connectionInformation.getSessionId();
        } else {
            return null;
        }
    }

    public String getSequenceIdentifier() {
        return sequenceIdentifier;
    }

    public void setSequenceIdentifier(String sequenceIdentifier) {
        this.sequenceIdentifier = sequenceIdentifier;
    }

    public String getModuleIdentifier() {
        return moduleIdentifier;
    }

    public void setModuleIdentifier(String moduleIdentifier) {
        this.moduleIdentifier = moduleIdentifier;
    }
}
