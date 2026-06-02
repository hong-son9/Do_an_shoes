package com.phs.application.repository;

import com.phs.application.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    User findByEmail(String email);

    @Query(value = """
    SELECT *
    FROM users u
    WHERE COALESCE(u.full_name,'') LIKE CONCAT('%', ?1, '%')
      AND COALESCE(u.phone,'') LIKE CONCAT('%', ?2, '%')
      AND COALESCE(u.email,'') LIKE CONCAT('%', ?3, '%')
    """,
            nativeQuery = true)
    Page<User> adminListUserPages(
            String fullName,
            String phone,
            String email,
            Pageable pageable);

    // Dem so tai khoan co role ADMIN — dung de chan demote admin cuoi cung.
    @Query(value = "SELECT COUNT(*) FROM users WHERE JSON_CONTAINS(roles, '\"ADMIN\"')", nativeQuery = true)
    long countAdmins();

}
