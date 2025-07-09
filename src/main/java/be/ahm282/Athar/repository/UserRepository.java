package be.ahm282.Athar.repository;

import be.ahm282.Athar.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Derived query methods
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByIsActiveTrue();

    List<User> findByFirstNameContainingIgnoreCase(String firstName);

    List<User> findByLastNameContainingIgnoreCase(String lastName);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Custom JPQL queries
    @Query("SELECT u FROM User u WHERE u.firstName = :firstName AND u.lastName = :lastName")
    List<User> findByFullName(@Param("firstName") String firstName, @Param("lastName") String lastName);

    @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.email LIKE %:keyword%")
    List<User> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();

    // Native SQL queries (if needed)
    @Query(value = "SELECT * FROM users WHERE created_at > datetime('now', '-30 days')", nativeQuery = true)
    List<User> findRecentUsers();
}