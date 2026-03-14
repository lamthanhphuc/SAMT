package com.example.user_groupservice.repository;

import com.example.user_groupservice.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Group entity.
 * CRITICAL: ID type is Long (not UUID), semesterId is Long (not String semester)
 * Note: @SQLRestriction on Group entity automatically filters soft-deleted records.
 */
@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    
    /**
     * Find group by ID (soft-deleted groups are automatically filtered by @SQLRestriction).
     * DO NOT use existsById() - it bypasses @SQLRestriction!
     */
    Optional<Group> findById(Long id);
    
    /**
     * Explicit query to find by ID and not deleted.
     * Use this for explicit control.
     */
    @Query("SELECT g FROM Group g WHERE g.id = :id AND g.deletedAt IS NULL")
    Optional<Group> findByIdAndNotDeleted(@Param("id") Long id);
    
    /**
     * Check if group name already exists in the given semester.
     * IMPORTANT: Uses semesterId (Long), not semester (String)
     */
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Group g WHERE g.groupName = :groupName AND g.semesterId = :semesterId " +
           "AND g.deletedAt IS NULL")
    boolean existsByGroupNameAndSemesterId(@Param("groupName") String groupName, 
                                           @Param("semesterId") Long semesterId);
    
    /**
     * Find all groups with pagination (soft-deleted automatically filtered).
     */
    Page<Group> findAll(Pageable pageable);
    
    /**
     * Find groups by semester ID.
     */
    @Query("SELECT g FROM Group g WHERE g.semesterId = :semesterId AND g.deletedAt IS NULL")
    Page<Group> findAllBySemesterId(@Param("semesterId") Long semesterId, Pageable pageable);
    
    /**
     * Find groups by semester ID (list, no pagination).
     */
    @Query("SELECT g FROM Group g WHERE g.semesterId = :semesterId AND g.deletedAt IS NULL")
    List<Group> findAllBySemesterId(@Param("semesterId") Long semesterId);
    
    /**
     * Find groups by lecturer ID.
     */
    @Query("SELECT g FROM Group g WHERE g.lecturerId = :lecturerId AND g.deletedAt IS NULL")
    Page<Group> findAllByLecturerId(@Param("lecturerId") Long lecturerId, Pageable pageable);
    
    /**
     * Find groups by lecturer ID (list, no pagination).
     */
    @Query("SELECT g FROM Group g WHERE g.lecturerId = :lecturerId AND g.deletedAt IS NULL")
    List<Group> findAllByLecturerId(@Param("lecturerId") Long lecturerId);
    
    /**
     * Find groups by filters (semester ID and/or lecturer ID).
     */
    @Query("SELECT g FROM Group g WHERE " +
           "(:semesterId IS NULL OR g.semesterId = :semesterId) AND " +
           "(:lecturerId IS NULL OR g.lecturerId = :lecturerId) AND " +
           "g.deletedAt IS NULL")
    Page<Group> findByFilters(@Param("semesterId") Long semesterId,
                              @Param("lecturerId") Long lecturerId,
                              Pageable pageable);
    
    /**
     * Check if a lecturer supervises a student (student is member of any group taught by lecturer).
     * Used for LECTURER authorization - ensures privacy by restricting access to only supervised students.
     * 
     * @param lecturerId ID of the lecturer
     * @param studentId ID of the student
     * @return true if lecturer supervises this student in any group, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Group g " +
           "JOIN UserSemesterMembership usm ON g.id = usm.groupId " +
           "WHERE g.lecturerId = :lecturerId " +
           "AND usm.id.userId = :studentId " +
           "AND g.deletedAt IS NULL " +
           "AND usm.deletedAt IS NULL")
    boolean existsByLecturerAndStudent(@Param("lecturerId") Long lecturerId, 
                                        @Param("studentId") Long studentId);
}
