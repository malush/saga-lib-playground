package com.malush.saga.workflow.participants.rms;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RMS {

  private final Logger log = LoggerFactory.getLogger(RMS.class);

  public void chargeRetailerCMD(String requestId, double amount, String retailerId) {
    log.info("requestId = {}, amount = {}, retailerId = {}", requestId, amount, retailerId);
  }

  public void compensateRetailerCMD(String requestId, double amount, String retailerId) {
    log.info("requestId = {}, amount = {}, retailerId = {}", requestId, amount, retailerId);
  }
}
