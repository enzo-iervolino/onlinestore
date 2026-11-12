package com.delta.store.order.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="web_order_mail_jobs")
public class OrderMailJob {
    @Id public Long orderId;
    public int attempts;
    public boolean delivered;
    @Column(nullable=false) public LocalDateTime nextAttempt;
    @Column(length=200) public String lastError;
}
