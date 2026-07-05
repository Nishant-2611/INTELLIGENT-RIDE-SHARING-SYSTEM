package com.ride.sharing.repository;

import com.ride.sharing.model.User;
import com.ride.sharing.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByRole(UserRole role);
    long countByRole(UserRole role);
}
