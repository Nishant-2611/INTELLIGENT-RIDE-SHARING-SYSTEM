package com.ride.sharing.repository;

import com.ride.sharing.model.DriverAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverAvailabilityRepository extends JpaRepository<DriverAvailability, Long> {
    Optional<DriverAvailability> findByDriverId(Long driverId);
    List<DriverAvailability> findByIsAvailable(Boolean isAvailable);
}
