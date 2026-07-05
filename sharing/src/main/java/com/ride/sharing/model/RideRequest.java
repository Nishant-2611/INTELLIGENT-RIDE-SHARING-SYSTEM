package com.ride.sharing.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ride_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rider_id", nullable = false, foreignKey = @ForeignKey(name = "fk_requests_rider"))
    private User rider;

    @Column(name = "start_latitude", nullable = false)
    private Double startLatitude;

    @Column(name = "start_longitude", nullable = false)
    private Double startLongitude;

    @Column(name = "end_latitude", nullable = false)
    private Double endLatitude;

    @Column(name = "end_longitude", nullable = false)
    private Double endLongitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_type", nullable = false)
    private VehicleType requestedType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status;

    @Column(name = "passenger_count", nullable = false)
    private Integer passengerCount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

}
