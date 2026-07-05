package com.ride.sharing.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_availability")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverAvailability {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_availability_driver"))
    private User driver;

    @Column(name = "current_latitude", nullable = false)
    private Double currentLatitude;

    @Column(name = "current_longitude", nullable = false)
    private Double currentLongitude;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;
}
