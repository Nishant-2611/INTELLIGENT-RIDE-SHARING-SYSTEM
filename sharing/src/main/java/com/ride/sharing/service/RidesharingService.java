package com.ride.sharing.service;

import com.ride.sharing.model.*;
import com.ride.sharing.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RidesharingService {

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

    // Proximity calculation (Euclidean distance)
    public double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        return Math.sqrt(Math.pow(lat1 - lat2, 2) + Math.pow(lng1 - lng2, 2)) * 100.0; // scale to km-like value
    }

    @Transactional
    public User createUser(String name, String email, String phone, UserRole role) {
        User user = User.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .role(role)
                .rating(5.0)
                .balance(role == UserRole.RIDER ? 100.0 : 0.0)
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public Vehicle createVehicle(Long driverId, String make, String model, String licensePlate, Integer capacity, VehicleType type) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        if (driver.getRole() != UserRole.DRIVER) {
            throw new IllegalArgumentException("User is not a driver");
        }
        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .make(make)
                .model(model)
                .licensePlate(licensePlate)
                .capacity(capacity)
                .vehicleType(type)
                .build();
        vehicle = vehicleRepository.save(vehicle);

        // Initialize availability
        DriverAvailability availability = DriverAvailability.builder()
                .driver(driver)
                .currentLatitude(12.9716 + (Math.random() - 0.5) * 0.1) // Bangalore center default + offset
                .currentLongitude(77.5946 + (Math.random() - 0.5) * 0.1)
                .isAvailable(true)
                .lastUpdatedAt(LocalDateTime.now())
                .build();
        driverAvailabilityRepository.save(availability);

        return vehicle;
    }

    @Transactional
    public void updateDriverLocation(Long driverId, double lat, double lng, boolean isAvailable) {
        DriverAvailability availability = driverAvailabilityRepository.findByDriverId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver availability not found"));
        availability.setCurrentLatitude(lat);
        availability.setCurrentLongitude(lng);
        availability.setIsAvailable(isAvailable);
        availability.setLastUpdatedAt(LocalDateTime.now());
        driverAvailabilityRepository.save(availability);
    }

    @Transactional
    public RideRequest requestRide(Long riderId, double startLat, double startLng, double endLat, double endLng, VehicleType requestedType, int passengerCount) {
        User rider = userRepository.findById(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found"));

        // Double Request Conflict check
        List<RideRequest> activeRequests = rideRequestRepository.findByRiderIdAndStatus(riderId, RequestStatus.PENDING);
        List<Ride> activeRides = rideRepository.findByRiderId(riderId).stream()
                .filter(r -> r.getStatus() == RideStatus.ACCEPTED || r.getStatus() == RideStatus.IN_PROGRESS)
                .collect(Collectors.toList());

        if (!activeRequests.isEmpty() || !activeRides.isEmpty()) {
            // Log conflict
            RideRequest conflictRequest = RideRequest.builder()
                    .rider(rider)
                    .startLatitude(startLat)
                    .startLongitude(startLng)
                    .endLatitude(endLat)
                    .endLongitude(endLng)
                    .requestedType(requestedType)
                    .status(RequestStatus.CANCELLED)
                    .passengerCount(passengerCount)
                    .requestedAt(LocalDateTime.now())
                    .build();
            conflictRequest = rideRequestRepository.save(conflictRequest);

            RideMatchConflict conflict = RideMatchConflict.builder()
                    .request(conflictRequest)
                    .conflictType(ConflictType.DOUBLE_REQUEST)
                    .details("Rider " + rider.getName() + " (ID: " + riderId + ") already has active requests or rides in progress.")
                    .resolvedAt(LocalDateTime.now())
                    .resolutionAction("REJECTED_NEW_REQUEST")
                    .build();
            conflictRepository.save(conflict);

            return conflictRequest;
        }

        RideRequest request = RideRequest.builder()
                .rider(rider)
                .startLatitude(startLat)
                .startLongitude(startLng)
                .endLatitude(endLat)
                .endLongitude(endLng)
                .requestedType(requestedType)
                .status(RequestStatus.PENDING)
                .passengerCount(passengerCount)
                .requestedAt(LocalDateTime.now())
                .build();
        return rideRequestRepository.save(request);
    }

    @Transactional
    public List<Ride> matchPendingRequests() {
        List<RideRequest> pendingRequests = rideRequestRepository.findByStatus(RequestStatus.PENDING);
        // Sort by requested time (FIFO)
        pendingRequests.sort(Comparator.comparing(RideRequest::getRequestedAt));

        List<Ride> matchesCreated = new ArrayList<>();
        Set<Long> assignedDriverIdsThisBatch = new HashSet<>();

        for (RideRequest request : pendingRequests) {
            // Find all drivers currently available
            List<DriverAvailability> availableDrivers = driverAvailabilityRepository.findByIsAvailable(true).stream()
                    .filter(d -> !assignedDriverIdsThisBatch.contains(d.getDriver().getId()))
                    .collect(Collectors.toList());

            if (availableDrivers.isEmpty()) {
                // DRIVER SCARCITY conflict
                request.setStatus(RequestStatus.EXPIRED);
                rideRequestRepository.save(request);

                RideMatchConflict conflict = RideMatchConflict.builder()
                        .request(request)
                        .conflictType(ConflictType.DRIVER_SCARCITY)
                        .details("No drivers available at all in the system.")
                        .resolvedAt(LocalDateTime.now())
                        .resolutionAction("EXPIRED_REQUEST")
                        .build();
                conflictRepository.save(conflict);
                continue;
            }

            // Find matching vehicles and drivers
            List<DriverWithVehicleAndDistance> candidates = new ArrayList<>();
            for (DriverAvailability availability : availableDrivers) {
                Optional<Vehicle> vehicleOpt = vehicleRepository.findByDriverId(availability.getDriver().getId());
                if (vehicleOpt.isPresent()) {
                    Vehicle vehicle = vehicleOpt.get();
                    double dist = calculateDistance(availability.getCurrentLatitude(), availability.getCurrentLongitude(),
                            request.getStartLatitude(), request.getStartLongitude());

                    candidates.add(new DriverWithVehicleAndDistance(availability, vehicle, dist));
                }
            }

            // Filter by requested vehicle type
            List<DriverWithVehicleAndDistance> typeFiltered = candidates.stream()
                    .filter(c -> c.vehicle.getVehicleType() == request.getRequestedType())
                    .collect(Collectors.toList());

            if (typeFiltered.isEmpty()) {
                // Vehicle type not available, log matching conflict but maybe try fallback to any available vehicle
                // First let's log CAPACITY/TYPE conflict
                request.setStatus(RequestStatus.EXPIRED);
                rideRequestRepository.save(request);

                RideMatchConflict conflict = RideMatchConflict.builder()
                        .request(request)
                        .conflictType(ConflictType.DRIVER_SCARCITY)
                        .details("No drivers with requested vehicle type (" + request.getRequestedType() + ") available.")
                        .resolvedAt(LocalDateTime.now())
                        .resolutionAction("EXPIRED_REQUEST_NO_TYPE_MATCH")
                        .build();
                conflictRepository.save(conflict);
                continue;
            }

            // Filter by capacity
            List<DriverWithVehicleAndDistance> capacityFiltered = typeFiltered.stream()
                    .filter(c -> c.vehicle.getCapacity() >= request.getPassengerCount())
                    .collect(Collectors.toList());

            if (capacityFiltered.isEmpty()) {
                // CAPACITY EXCEEDED conflict
                request.setStatus(RequestStatus.EXPIRED);
                rideRequestRepository.save(request);

                // Driver with type match is available, but doesn't have capacity
                DriverWithVehicleAndDistance bestCapacityCandidate = typeFiltered.stream()
                        .min(Comparator.comparingDouble(c -> c.distance))
                        .orElse(null);

                RideMatchConflict conflict = RideMatchConflict.builder()
                        .request(request)
                        .driver(bestCapacityCandidate != null ? bestCapacityCandidate.availability.getDriver() : null)
                        .conflictType(ConflictType.CAPACITY_EXCEEDED)
                        .details("Rider requires capacity " + request.getPassengerCount() + 
                                 ", but nearest driver (" + (bestCapacityCandidate != null ? bestCapacityCandidate.availability.getDriver().getName() : "N/A") +
                                 ") only supports capacity " + (bestCapacityCandidate != null ? bestCapacityCandidate.vehicle.getCapacity() : "N/A") + ".")
                        .resolvedAt(LocalDateTime.now())
                        .resolutionAction("EXPIRED_REQUEST_INSUFFICIENT_CAPACITY")
                        .build();
                conflictRepository.save(conflict);
                continue;
            }

            // Sort by proximity
            capacityFiltered.sort(Comparator.comparingDouble(c -> c.distance));

            // Select the closest one
            DriverWithVehicleAndDistance matchCandidate = capacityFiltered.get(0);
            
            // Check if closest driver was target of conflict resolution
            // e.g. if we had multiple riders competing, the closest one gets him, others will have driver scarcity recorded.
            // In our sequential batch matching, since we sorted requests by FIFO and we exclude already matched drivers (via assignedDriverIdsThisBatch),
            // if a subsequent request also wanted this driver but he is now assigned, they get the next closest driver.
            // Let's log if the driver scarcity caused a rider to get their 2nd or 3rd choice driver instead of their 1st!
            if (candidates.size() > 0) {
                candidates.sort(Comparator.comparingDouble(c -> c.distance));
                DriverWithVehicleAndDistance absoluteClosest = candidates.get(0);
                if (!absoluteClosest.availability.getDriver().getId().equals(matchCandidate.availability.getDriver().getId())) {
                    // Absolute closest driver was busy / scarcity!
                    RideMatchConflict conflict = RideMatchConflict.builder()
                            .request(request)
                            .driver(absoluteClosest.availability.getDriver())
                            .conflictType(ConflictType.DRIVER_SCARCITY)
                            .details("Absolute closest driver " + absoluteClosest.availability.getDriver().getName() + 
                                     " was unavailable/assigned. Routing to next nearest driver: " + matchCandidate.availability.getDriver().getName())
                            .resolvedAt(LocalDateTime.now())
                            .resolutionAction("ROUTED_TO_NEXT_NEAREST_DRIVER")
                            .build();
                    conflictRepository.save(conflict);
                }
            }

            // Create Ride
            double distance = calculateDistance(request.getStartLatitude(), request.getStartLongitude(),
                    request.getEndLatitude(), request.getEndLongitude());
            
            // Rate factors
            double rate = 2.0; // sedan
            if (request.getRequestedType() == VehicleType.SUV) rate = 3.5;
            else if (request.getRequestedType() == VehicleType.LUXURY) rate = 5.0;
            
            double fare = Math.max(10.0, distance * rate); // Minimum fare $10.0

            Ride ride = Ride.builder()
                    .request(request)
                    .rider(request.getRider())
                    .driver(matchCandidate.availability.getDriver())
                    .vehicle(matchCandidate.vehicle)
                    .status(RideStatus.ACCEPTED)
                    .startLatitude(request.getStartLatitude())
                    .startLongitude(request.getStartLongitude())
                    .endLatitude(request.getEndLatitude())
                    .endLongitude(request.getEndLongitude())
                    .fare(fare)
                    .startTime(LocalDateTime.now())
                    .build();

            ride = rideRepository.save(ride);

            // Update request status
            request.setStatus(RequestStatus.MATCHED);
            rideRequestRepository.save(request);

            // Update driver availability
            matchCandidate.availability.setIsAvailable(false);
            driverAvailabilityRepository.save(matchCandidate.availability);

            assignedDriverIdsThisBatch.add(matchCandidate.availability.getDriver().getId());
            matchesCreated.add(ride);
        }

        return matchesCreated;
    }

    @Transactional
    public void startRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));
        if (ride.getStatus() == RideStatus.ACCEPTED) {
            ride.setStatus(RideStatus.IN_PROGRESS);
            rideRepository.save(ride);
        }
    }

    @Transactional
    public void completeRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));
        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.CANCELLED) {
            return;
        }

        ride.setStatus(RideStatus.COMPLETED);
        ride.setEndTime(LocalDateTime.now());
        rideRepository.save(ride);

        // Process Payment
        User rider = ride.getRider();
        User driver = ride.getDriver();
        double fare = ride.getFare();

        PaymentStatus paymentStatus = PaymentStatus.COMPLETED;
        if (rider.getBalance() >= fare) {
            rider.setBalance(rider.getBalance() - fare);
            driver.setBalance(driver.getBalance() + fare);
            userRepository.save(rider);
            userRepository.save(driver);
        } else {
            // Insufficient balance, fail payment but complete ride (adds debt or fails transaction)
            // Let's deduct anyway or fail payment
            paymentStatus = PaymentStatus.FAILED;
            // Record conflict
            RideMatchConflict conflict = RideMatchConflict.builder()
                    .request(ride.getRequest())
                    .driver(driver)
                    .conflictType(ConflictType.DOUBLE_REQUEST) // reuse for payment conflict
                    .details("Payment failed. Rider " + rider.getName() + " has insufficient balance ($" + 
                             rider.getBalance() + ") for fare ($" + fare + ").")
                    .resolvedAt(LocalDateTime.now())
                    .resolutionAction("PAYMENT_FAILED_INSUFFICIENT_BALANCE")
                    .build();
            conflictRepository.save(conflict);
        }

        Payment payment = Payment.builder()
                .ride(ride)
                .payer(rider)
                .amount(fare)
                .status(paymentStatus)
                .paymentMethod(PaymentMethod.WALLET)
                .paymentTime(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        // Make driver available at the destination
        DriverAvailability availability = driverAvailabilityRepository.findByDriverId(driver.getId())
                .orElseThrow(() -> new IllegalArgumentException("Driver availability not found"));
        availability.setIsAvailable(true);
        availability.setCurrentLatitude(ride.getEndLatitude());
        availability.setCurrentLongitude(ride.getEndLongitude());
        availability.setLastUpdatedAt(LocalDateTime.now());
        driverAvailabilityRepository.save(availability);

        // Generate Rating & Review
        // Rider rating of Driver
        int riderRating = 3 + (int)(Math.random() * 3); // 3, 4, or 5
        String[] riderComments = {"Great driver!", "Safe and clean car.", "Arrived on time.", "Polite behavior.", "Excellent route choice."};
        Review riderReview = Review.builder()
                .ride(ride)
                .reviewer(rider)
                .reviewee(driver)
                .rating(riderRating)
                .comment(riderComments[(int)(Math.random() * riderComments.length)])
                .createdAt(LocalDateTime.now())
                .build();
        reviewRepository.save(riderReview);

        // Driver rating of Rider
        int driverRating = 4 + (int)(Math.random() * 2); // 4 or 5
        String[] driverComments = {"Polite passenger.", "Cooperative and on time.", "Nice conversation.", "Highly recommended.", "Great experience."};
        Review driverReview = Review.builder()
                .ride(ride)
                .reviewer(driver)
                .reviewee(rider)
                .rating(driverRating)
                .comment(driverComments[(int)(Math.random() * driverComments.length)])
                .createdAt(LocalDateTime.now())
                .build();
        reviewRepository.save(driverReview);

        // Update user average ratings
        updateAverageRating(rider.getId());
        updateAverageRating(driver.getId());
    }

    private void updateAverageRating(Long userId) {
        List<Review> reviews = reviewRepository.findByRevieweeId(userId);
        if (!reviews.isEmpty()) {
            double sum = 0;
            for (Review r : reviews) sum += r.getRating();
            double avg = sum / reviews.size();
            User user = userRepository.findById(userId).orElseThrow();
            user.setRating(avg);
            userRepository.save(user);
        }
    }

    // Candidate helper class
    private static class DriverWithVehicleAndDistance {
        final DriverAvailability availability;
        final Vehicle vehicle;
        final double distance;

        DriverWithVehicleAndDistance(DriverAvailability availability, Vehicle vehicle, double distance) {
            this.availability = availability;
            this.vehicle = vehicle;
            this.distance = distance;
        }
    }
}
