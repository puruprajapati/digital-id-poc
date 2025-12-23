package org.example.backend.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VerificationSessionStore {
  private final Map<String, VerificationSession> sessions = new ConcurrentHashMap<>();

  public Map<String, VerificationSession> getSessions() {
    return sessions;
  }

  public VerificationSession get(String sessionId) {
    return sessions.get(sessionId);
  }

  public void put(String sessionId, VerificationSession session) {
    sessions.put(sessionId, session);
  }

  public void remove(String sessionId) {
    sessions.remove(sessionId);
  }
}

