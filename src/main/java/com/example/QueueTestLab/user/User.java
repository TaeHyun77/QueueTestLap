package com.example.QueueTestLab.user;

import jakarta.persistence.*;
import lombok.Builder;

@Builder
@Table(name="users")
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String queueType;

    private String status;

    public void updateStatus(String status) {
        this.status = status;
    }
}
