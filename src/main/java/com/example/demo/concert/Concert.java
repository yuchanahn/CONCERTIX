package com.example.demo.concert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "concerts")
public class Concert {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 5000)
    private String description;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(nullable = false)
    private int holdSeconds;

    @Column(nullable = false)
    private int maxTicketsPerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConcertStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private int version;

    @OrderBy("startsAt ASC")
    @OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConcertSession> sessions = new ArrayList<>();

    protected Concert() {
    }

    public Concert(String title, String description, String imageUrl, String venue, int holdSeconds, int maxTicketsPerUser) {
        this.id = UUID.randomUUID();
        this.status = ConcertStatus.PREPARING;
        this.createdAt = Instant.now();
        updateFields(title, description, imageUrl, venue, holdSeconds, maxTicketsPerUser);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getVenue() {
        return venue;
    }

    public int getHoldSeconds() {
        return holdSeconds;
    }

    public int getMaxTicketsPerUser() {
        return maxTicketsPerUser;
    }

    public ConcertStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ConcertSession> getSessions() {
        return sessions.stream()
                .sorted(Comparator.comparing(ConcertSession::getStartsAt))
                .toList();
    }

    public void updateDraft(String title, String description, String imageUrl, String venue, int holdSeconds,
            int maxTicketsPerUser, List<ConcertSession> replacementSessions) {
        requireDraft();
        updateFields(title, description, imageUrl, venue, holdSeconds, maxTicketsPerUser);
        sessions.clear();
        replacementSessions.forEach(this::addSession);
    }

    public void publish() {
        status = ConcertStatus.PUBLISHED;
    }

    public void requireDraft() {
        if (status != ConcertStatus.PREPARING) {
            throw new ConcertAlreadyPublishedException();
        }
    }

    private void updateFields(String title, String description, String imageUrl, String venue, int holdSeconds,
            int maxTicketsPerUser) {
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.venue = venue;
        this.holdSeconds = holdSeconds;
        this.maxTicketsPerUser = maxTicketsPerUser;
    }

    private void addSession(ConcertSession session) {
        session.attachTo(this);
        sessions.add(session);
    }
}
