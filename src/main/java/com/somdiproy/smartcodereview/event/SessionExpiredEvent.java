package com.somdiproy.smartcodereview.event;

import org.springframework.context.ApplicationEvent;

public class SessionExpiredEvent extends ApplicationEvent {
    private final String sessionId;
    
    public SessionExpiredEvent(Object source, String sessionId) {
        super(source);
        this.sessionId = sessionId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
}