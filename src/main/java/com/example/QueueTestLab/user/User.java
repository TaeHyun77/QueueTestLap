package com.example.QueueTestLab.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Table(name="users")
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String queueType;

    private String status;

    @Builder
    public User(String userId, String queueType, String status) {
        this.userId = userId;
        this.queueType = queueType;
        this.status = status;
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}
