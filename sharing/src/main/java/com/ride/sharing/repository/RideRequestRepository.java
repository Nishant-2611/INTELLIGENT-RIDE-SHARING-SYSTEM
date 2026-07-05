package com.ride.sharing.repository;

import com.ride.sharing.model.RideRequest;
import com.ride.sharing.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {
    List<RideRequest> findByStatus(RequestStatus status);
    List<RideRequest> findByRiderIdAndStatus(Long riderId, RequestStatus status);
}
