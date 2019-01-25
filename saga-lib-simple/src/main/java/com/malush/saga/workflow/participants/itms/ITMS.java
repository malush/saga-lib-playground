package com.malush.saga.workflow.participants.itms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ITMS {

  private final Logger log = LoggerFactory.getLogger(ITMS.class);

  public enum Status {
    SOLD,
    ACTIVATED
  }

  private ConcurrentMap<String, Status> ticketStatuses = new ConcurrentHashMap();

  public void setTicketStatus(String requestId, String retailerId, Status status) {
    log.info("requestId = {}, retailerId = {}, status = {}", requestId, retailerId, status);
    ticketStatuses.putIfAbsent(retailerId, status);
  }

  public Status getTicketStatus(String retailerId) {
    return ticketStatuses.get(retailerId);
  }
}
