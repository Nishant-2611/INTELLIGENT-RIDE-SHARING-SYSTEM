package com.ride.sharing.service;

import com.ride.sharing.model.*;
import com.ride.sharing.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RideSimulationService {

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

    @Autowired
    private RidesharingService ridesharingService;

    @Transactional
    public void resetDatabase() {
        conflictRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        rideRepository.deleteAllInBatch();
        rideRequestRepository.deleteAllInBatch();
        driverAvailabilityRepository.deleteAllInBatch();
        vehicleRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Transactional
    public Map<String, Object> runSimulation() {
        // 1. Reset Database
        resetDatabase();

        Map<String, Object> report = new LinkedHashMap<>();
        List<String> logs = new ArrayList<>();
        logs.add("Database cleared.");

        // 2. Create 30 Riders
        List<User> riders = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            double initialBalance = 100.0;
            // Let some riders have very low balance to trigger payment failures later
            if (i == 5 || i == 15 || i == 25) {
                initialBalance = 5.0; // very low balance
            }
            User rider = User.builder()
                    .name("Rider " + i)
                    .email("rider" + i + "@rideshare.com")
                    .phone("+1555000" + String.format("%02d", i))
                    .role(UserRole.RIDER)
                    .rating(5.0)
                    .balance(initialBalance)
                    .build();
            riders.add(userRepository.save(rider));
        }
        logs.add("Created 30 Rider profiles.");

        // 3. Create 20 Drivers & Vehicles
        List<User> drivers = new ArrayList<>();
        List<Vehicle> vehicles = new ArrayList<>();
        String[] makes = {"Toyota", "Honda", "Hyundai", "Ford", "Tesla", "BMW"};
        String[] models = {"Camry", "Civic", "Elantra", "Explorer", "Model 3", "5 Series"};
        
        for (int i = 1; i <= 20; i++) {
            User driver = User.builder()
                    .name("Driver " + i)
                    .email("driver" + i + "@rideshare.com")
                    .phone("+1555111" + String.format("%02d", i))
                    .role(UserRole.DRIVER)
                    .rating(5.0)
                    .balance(0.0)
                    .build();
            driver = userRepository.save(driver);
            drivers.add(driver);

            // Determine vehicle type, capacity
            VehicleType type;
            int capacity;
            if (i % 5 == 0) {
                type = VehicleType.LUXURY;
                capacity = 4;
            } else if (i % 3 == 0) {
                type = VehicleType.SUV;
                capacity = 6;
            } else {
                type = VehicleType.SEDAN;
                capacity = 4;
            }

            Vehicle vehicle = Vehicle.builder()
                    .driver(driver)
                    .make(makes[i % makes.length])
                    .model(models[i % models.length])
                    .licensePlate("ABC" + (1000 + i))
                    .capacity(capacity)
                    .vehicleType(type)
                    .build();
            vehicles.add(vehicleRepository.save(vehicle));

            // Initialize availability
            // Spread drivers around Bangalore Central (lat: 12.9716, lng: 77.5946)
            double latOffset = (i - 10) * 0.005; // ~500m increments
            double lngOffset = ((i % 5) - 2) * 0.005;
            DriverAvailability availability = DriverAvailability.builder()
                    .driver(driver)
                    .currentLatitude(12.9716 + latOffset)
                    .currentLongitude(77.5946 + lngOffset)
                    .isAvailable(true)
                    .lastUpdatedAt(LocalDateTime.now())
                    .build();
            driverAvailabilityRepository.save(availability);
        }
        logs.add("Created 20 Driver profiles and registered their vehicles with availability status.");

        // 4. Simulate 55 Requests in 3 stages to test matching & conflicts
        int requestIdCounter = 1;
        int totalRequestsSimulated = 0;

        // --- STAGE 1: Normal and Clean Matches (20 requests) ---
        logs.add("--- STAGE 1: Simulating 20 standard requests (Normal Operations) ---");
        for (int i = 0; i < 20; i++) {
            User rider = riders.get(i);
            // Request standard ride matching nearest driver
            double startLat = 12.9716 + (Math.random() - 0.5) * 0.04;
            double startLng = 77.5946 + (Math.random() - 0.5) * 0.04;
            double endLat = startLat + (Math.random() - 0.5) * 0.05;
            double endLng = startLng + (Math.random() - 0.5) * 0.05;
            VehicleType type = (i % 4 == 0) ? VehicleType.SUV : VehicleType.SEDAN;
            
            ridesharingService.requestRide(rider.getId(), startLat, startLng, endLat, endLng, type, 2);
            totalRequestsSimulated++;
        }
        
        List<Ride> stage1Matches = ridesharingService.matchPendingRequests();
        logs.add("Stage 1 Match Processed. Matches created: " + stage1Matches.size());
        
        // Start and complete Stage 1 rides
        for (Ride ride : stage1Matches) {
            ridesharingService.startRide(ride.getId());
            ridesharingService.completeRide(ride.getId());
        }
        logs.add("Stage 1 rides completed and payments processed.");


        // --- STAGE 2: Capacity Limits & Double Requests (15 requests) ---
        logs.add("--- STAGE 2: Simulating 15 requests to test Double Request & Capacity constraints ---");
        
        // Let's create intentional DOUBLE REQUESTS (rider 20 sends 3 requests back-to-back)
        User doubleRider = riders.get(20);
        logs.add("Simulating Double Request conflict: Rider " + doubleRider.getName() + " making 3 concurrent requests.");
        for (int i = 0; i < 3; i++) {
            ridesharingService.requestRide(doubleRider.getId(), 12.9716, 77.5946, 12.9916, 77.6146, VehicleType.SEDAN, 2);
            totalRequestsSimulated++;
        }

        // Let's create intentional CAPACITY EXCEEDED REQUESTS (Requesting 8 passengers when SUV capacity is 6)
        User capacityRider = riders.get(21);
        logs.add("Simulating Capacity Exceeded conflict: Rider " + capacityRider.getName() + " requesting ride for 8 passengers.");
        ridesharingService.requestRide(capacityRider.getId(), 12.9716, 77.5946, 12.9916, 77.6146, VehicleType.SUV, 8);
        totalRequestsSimulated++;

        // Add 11 standard requests to fill Stage 2
        for (int i = 0; i < 11; i++) {
            User rider = riders.get(i + 5); // reuse riders 5 to 15
            double startLat = 12.9716 + (Math.random() - 0.5) * 0.04;
            double startLng = 77.5946 + (Math.random() - 0.5) * 0.04;
            double endLat = startLat + (Math.random() - 0.5) * 0.05;
            double endLng = startLng + (Math.random() - 0.5) * 0.05;
            VehicleType type = (i % 2 == 0) ? VehicleType.SUV : VehicleType.SEDAN;
            
            ridesharingService.requestRide(rider.getId(), startLat, startLng, endLat, endLng, type, 1);
            totalRequestsSimulated++;
        }

        List<Ride> stage2Matches = ridesharingService.matchPendingRequests();
        logs.add("Stage 2 Match Processed. Matches created: " + stage2Matches.size());
        
        // Start and complete Stage 2 rides
        for (Ride ride : stage2Matches) {
            ridesharingService.startRide(ride.getId());
            ridesharingService.completeRide(ride.getId());
        }
        logs.add("Stage 2 rides completed and payments processed.");


        // --- STAGE 3: High Density & Driver Scarcity & Payment Failures (20 requests) ---
        logs.add("--- STAGE 3: Simulating 20 requests in a highly congested area with low available drivers ---");
        
        // Make 15 drivers unavailable temporarily to force driver scarcity
        List<DriverAvailability> allAvailability = driverAvailabilityRepository.findAll();
        for (int i = 0; i < 15; i++) {
            DriverAvailability av = allAvailability.get(i);
            av.setIsAvailable(false);
            driverAvailabilityRepository.save(av);
        }
        logs.add("Artificially set 15 drivers to unavailable to force Driver Scarcity.");

        // Fire 20 requests sequentially in a short timeframe
        for (int i = 0; i < 20; i++) {
            User rider = riders.get(i + 10); // riders 10 to 30
            // Request luxury or SUV
            double startLat = 12.9716 + (Math.random() - 0.5) * 0.01; // highly concentrated
            double startLng = 77.5946 + (Math.random() - 0.5) * 0.01;
            double endLat = startLat + (Math.random() - 0.5) * 0.05;
            double endLng = startLng + (Math.random() - 0.5) * 0.05;
            VehicleType type = (i % 3 == 0) ? VehicleType.LUXURY : VehicleType.SEDAN;
            
            ridesharingService.requestRide(rider.getId(), startLat, startLng, endLat, endLng, type, 2);
            totalRequestsSimulated++;
        }

        List<Ride> stage3Matches = ridesharingService.matchPendingRequests();
        logs.add("Stage 3 Match Processed. Matches created due to driver scarcity: " + stage3Matches.size());
        
        // Start and complete Stage 3 rides
        for (Ride ride : stage3Matches) {
            ridesharingService.startRide(ride.getId());
            ridesharingService.completeRide(ride.getId());
        }
        logs.add("Stage 3 rides completed and payments processed (including triggered balance failures).");

        // Make all drivers available again at the end of simulation
        for (DriverAvailability av : driverAvailabilityRepository.findAll()) {
            av.setIsAvailable(true);
            driverAvailabilityRepository.save(av);
        }
        logs.add("Simulation complete. Restored default driver availabilities.");

        // 5. Gather Database Statistics
        long ridersCount = userRepository.countByRole(UserRole.RIDER);
        long driversCount = userRepository.countByRole(UserRole.DRIVER);
        long vehiclesCount = vehicleRepository.count();
        long totalRequests = rideRequestRepository.count();
        long completedRides = rideRepository.findByStatus(RideStatus.COMPLETED).size();
        long paymentSuccess = paymentRepository.findAll().stream().filter(p -> p.getStatus() == PaymentStatus.COMPLETED).count();
        long paymentFailure = paymentRepository.findAll().stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
        long totalConflicts = conflictRepository.count();

        // Count conflicts by type
        long doubleRequestsConflict = conflictRepository.findAll().stream().filter(c -> c.getConflictType() == ConflictType.DOUBLE_REQUEST).count();
        long capacityConflict = conflictRepository.findAll().stream().filter(c -> c.getConflictType() == ConflictType.CAPACITY_EXCEEDED).count();
        long scarcityConflict = conflictRepository.findAll().stream().filter(c -> c.getConflictType() == ConflictType.DRIVER_SCARCITY).count();

        report.put("ridersCount", ridersCount);
        report.put("driversCount", driversCount);
        report.put("vehiclesCount", vehiclesCount);
        report.put("totalRequestsSimulated", totalRequestsSimulated);
        report.put("totalRequestsStored", totalRequests);
        report.put("completedRidesCount", completedRides);
        report.put("paymentSuccessCount", paymentSuccess);
        report.put("paymentFailureCount", paymentFailure);
        report.put("totalConflictsCount", totalConflicts);
        report.put("doubleRequestsConflictCount", doubleRequestsConflict);
        report.put("capacityConflictCount", capacityConflict);
        report.put("scarcityConflictCount", scarcityConflict);
        report.put("simulationLogs", logs);

        return report;
    }
}
