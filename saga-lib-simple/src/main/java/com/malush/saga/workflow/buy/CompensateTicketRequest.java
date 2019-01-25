package com.malush.saga.workflow.buy;

import com.malush.saga.workflow.participants.rms.reply.ChargeRetailerReply;

public class CompensateTicketRequest {
  public String requestId;
  public double amount;
  public String retailerId;

  public CompensateTicketRequest(String requestId, double amount, String retailerId) {
    this.requestId = requestId;
    this.amount = amount;
    this.retailerId = retailerId;
  }

  public CompensateTicketRequest(ChargeRetailerReply reply) {
    this.requestId = reply.requestId;
    this.amount = reply.amount;
    this.retailerId = reply.retailerId;
  }

}
