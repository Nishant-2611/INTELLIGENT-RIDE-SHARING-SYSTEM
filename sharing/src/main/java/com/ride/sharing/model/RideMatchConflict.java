package com.ride.sharing.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ride_match_conflicts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideMatchConflict {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "request_id", nullable = false, foreignKey = @ForeignKey(name = "fk_conflicts_request"))
    private RideRequest request;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id", nullable = true, foreignKey = @ForeignKey(name = "fk_conflicts_driver"))
    private User driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_type", nullable = false)
    private ConflictType conflictType;

    @Column(nullable = false, length = 500)
    private String details;

    @Column(name = "resolved_at", nullable = false)
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_action", nullable = false)
    private String resolutionAction;
}
