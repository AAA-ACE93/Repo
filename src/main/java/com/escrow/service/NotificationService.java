package com.escrow.service;

import com.escrow.domain.entity.User;
import com.escrow.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {
    void sendNotification(User user, String title, String message, String notificationType, UUID referenceId);
    Page<NotificationResponse> getUserNotifications(String userEmail, Pageable pageable);
    long getUnreadCount(String userEmail);
}
