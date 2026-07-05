package com.ride.sharing.repository;

import com.ride.sharing.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByDriverId(Long driverId);
    Optional<Vehicle> findByLicensePlate(String licensePlate);
}
