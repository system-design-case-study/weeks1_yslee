package com.proximityservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "business")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Business {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, columnDefinition = "DECIMAL(10,7)")
    private Double latitude;

    @Column(nullable = false, columnDefinition = "DECIMAL(10,7)")
    private Double longitude;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String hours;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Business(String name, String address, Double latitude, Double longitude,
                    String category, String phone, String hours) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.phone = phone;
        this.hours = hours;
    }
}
