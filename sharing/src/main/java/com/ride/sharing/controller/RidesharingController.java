package com.ride.sharing.controller;

import com.ride.sharing.model.*;
import com.ride.sharing.repository.*;
import com.ride.sharing.service.RideSimulationService;
import com.ride.sharing.service.RidesharingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RidesharingController {

    @Autowired
    private RideSimulationService simulationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private DriverAvailabilityRepository driverAvailabilityRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RideMatchConflictRepository conflictRepository;

    @PostMapping("/simulation/run")
    public ResponseEntity<Map<String, Object>> runSimulation() {
        Map<String, Object> result = simulationService.runSimulation();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/vehicles")
    public ResponseEntity<List<Vehicle>> getVehicles() {
        return ResponseEntity.ok(vehicleRepository.findAll());
    }

    @GetMapping("/requests")
    public ResponseEntity<List<RideRequest>> getRequests() {
        return ResponseEntity.ok(rideRequestRepository.findAll());
    }

    @GetMapping("/availability")
    public ResponseEntity<List<DriverAvailability>> getAvailability() {
        return ResponseEntity.ok(driverAvailabilityRepository.findAll());
    }

    @GetMapping("/rides")
    public ResponseEntity<List<Ride>> getRides() {
        return ResponseEntity.ok(rideRepository.findAll());
    }

    @GetMapping("/payments")
    public ResponseEntity<List<Payment>> getPayments() {
        return ResponseEntity.ok(paymentRepository.findAll());
    }

    @GetMapping("/reviews")
    public ResponseEntity<List<Review>> getReviews() {
        return ResponseEntity.ok(reviewRepository.findAll());
    }

    @GetMapping("/conflicts")
    public ResponseEntity<List<RideMatchConflict>> getConflicts() {
        return ResponseEntity.ok(conflictRepository.findAll());
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        long ridersCount = userRepository.countByRole(UserRole.RIDER);
        long driversCount = userRepository.countByRole(UserRole.DRIVER);
        long vehiclesCount = vehicleRepository.count();
        long totalRequests = rideRequestRepository.count();
        long completedRides = rideRepository.findByStatus(RideStatus.COMPLETED).size();
        long totalConflicts = conflictRepository.count();
        long paymentSuccess = paymentRepository.findAll().stream().filter(p -> p.getStatus() == PaymentStatus.COMPLETED).count();
        long paymentFailure = paymentRepository.findAll().stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();

        long doubleRequestsConflict = conflictRepository.findAll().stream().filter(c -> c.getConflictType() == ConflictType.DOUBLE_REQUEST).count();
        long capacityConflict = conflictRepository.findAll().stream().filter(c -> c.getConflictType() == ConflictType.CAPACITY_EXCEEDED).count();
        long scarcityConflict = conflictRepository.findAll().stream().filter(c -> c.getConflictType() == ConflictType.DRIVER_SCARCITY).count();

        stats.put("ridersCount", ridersCount);
        stats.put("driversCount", driversCount);
        stats.put("vehiclesCount", vehiclesCount);
        stats.put("totalRequests", totalRequests);
        stats.put("completedRides", completedRides);
        stats.put("totalConflicts", totalConflicts);
        stats.put("paymentSuccess", paymentSuccess);
        stats.put("paymentFailure", paymentFailure);
        stats.put("doubleRequestsConflict", doubleRequestsConflict);
        stats.put("capacityConflict", capacityConflict);
        stats.put("scarcityConflict", scarcityConflict);

        // Include recent entities for quick display
        List<Ride> rides = rideRepository.findAll();
        Collections.reverse(rides);
        stats.put("recentRides", rides.stream().limit(10).toList());

        List<RideMatchConflict> conflicts = conflictRepository.findAll();
        Collections.reverse(conflicts);
        stats.put("recentConflicts", conflicts.stream().limit(10).toList());

        return ResponseEntity.ok(stats);
    }
}
