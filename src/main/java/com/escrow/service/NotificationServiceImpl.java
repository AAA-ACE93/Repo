package com.escrow.service;

import com.escrow.domain.entity.Notification;
import com.escrow.domain.entity.User;
import com.escrow.dto.NotificationResponse;
import com.escrow.exception.ResourceNotFoundException;
import com.escrow.mapper.NotificationMapper;
import com.escrow.repository.NotificationRepository;
import com.escrow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public void sendNotification(User user, String title, String message, String notificationType, UUID referenceId) {
        if (user == null) {
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .notificationType(notificationType)
                .readStatus(false)
                .referenceId(referenceId)
                .build();

        notificationRepository.save(notification);

        // Simulated email notification dispatch
        log.info("[EMAIL NOTIFICATION] To: {} | Subject: {} | Body: {}", user.getEmail(), title, message);
    }

    @Override
    public Page<NotificationResponse> getUserNotifications(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    public long getUnreadCount(String userEmail) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return notificationRepository.countByUserIdAndReadStatusFalse(user.getId());
    }
}
