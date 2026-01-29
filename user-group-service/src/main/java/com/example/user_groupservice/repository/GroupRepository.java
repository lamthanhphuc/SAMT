package com.example.user_groupservice.repository;

import com.example.user_groupservice.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Group entity.
 * Note: @SQLRestriction on Group entity automatically filters soft-deleted records.
 */
@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {
    
    /**
     * Find group by ID (soft-deleted groups are automatically filtered).
     */
    Optional<Group> findById(UUID id);
    
    /**
     * Check if group name already exists in the given semester.
     */
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Group g WHERE g.groupName = :groupName AND g.semester = :semester")
    boolean existsByGroupNameAndSemester(@Param("groupName") String groupName, 
                                         @Param("semester") String semester);
    
    /**
     * Find all groups with pagination.
     */
    Page<Group> findAll(Pageable pageable);
    
    /**
     * Find groups by semester.
     */
    Page<Group> findBySemester(String semester, Pageable pageable);
    
    /**
     * Find groups by lecturer ID.
     */
    Page<Group> findByLecturerId(UUID lecturerId, Pageable pageable);
    
    /**
     * Find groups by semester and lecturer ID.
     */
    @Query("SELECT g FROM Group g WHERE " +
           "(:semester IS NULL OR g.semester = :semester) AND " +
           "(:lecturerId IS NULL OR g.lecturer.id = :lecturerId)")
    Page<Group> findByFilters(@Param("semester") String semester,
                              @Param("lecturerId") UUID lecturerId,
                              Pageable pageable);
}
