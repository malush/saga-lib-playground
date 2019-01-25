package com.malush.saga.workflow.buy;

public class SellTicketRequest {
  public String requestId;
  public String retailerId;
  public double amount;

  public SellTicketRequest(String requestId, String retailerId, double amount) {
    this.requestId = requestId;
    this.retailerId = retailerId;
    this.amount = amount;
  }
}
