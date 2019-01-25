package com.malush.saga.workflow.participants.rms.reply;

public class CompensateRetailerReply extends RMSReply{
  public CompensateRetailerReply(boolean success, String requestId, double amount, String retailerId) {
    super(success, requestId, amount, retailerId);
  }
}
