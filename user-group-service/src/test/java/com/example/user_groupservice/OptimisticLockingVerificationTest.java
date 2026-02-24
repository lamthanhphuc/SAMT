package com.example.user_groupservice;

import com.example.user_groupservice.entity.Group;
import com.example.user_groupservice.repository.GroupRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OPTIMISTIC LOCKING VERIFICATION TEST
 *
 * Kiểm tra @Version có hoạt động đúng trong Group entity.
 *
 * TEST CHECKLIST:
 * ✅ 1. SQL có chứa "WHERE version=?" không
 * ✅ 2. Version có tự động tăng sau mỗi update không
 * ✅ 3. Concurrent update có throw exception không
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "logging.level.org.hibernate.SQL=DEBUG",
    "logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE",
    "logging.level.org.hibernate.orm.jdbc.bind=TRACE",
    "spring.flyway.enabled=false"
})
@Slf4j
public class OptimisticLockingVerificationTest {

    @Autowired
    private GroupRepository groupRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long testGroupId;

    @BeforeEach
    void setup() {
        log.info("\n\n");
        log.info("========================================");
        log.info("   OPTIMISTIC LOCKING VERIFICATION");
        log.info("========================================");

        // Create test data
        Group group = Group.builder()
                .groupName("OPTIMISTIC-LOCK-TEST")
                .semesterId(1L)
                .lecturerId(100L)
                .build();

        Group saved = groupRepository.saveAndFlush(group);
        testGroupId = saved.getId();

        log.info("✓ Test group created: id={}, version={}", saved.getId(), saved.getVersion());
        entityManager.clear(); // Clear persistence context
    }

    /**
     * ✅ TEST 1: Kiểm tra SQL UPDATE có "WHERE version=?" không
     *
     * CÁCH ĐÁNH GIÁ:
     * - Xem console log Hibernate SQL
     * - Phải có dạng: update groups set ..., version=? where id=? and version=?
     * - Nếu thiếu "and version=?" → FAIL
     */
    @Test
    void test1_VerifySqlContainsVersionInWhereClause() {
        log.info("\n");
        log.info("==========================================");
        log.info("TEST 1: SQL UPDATE có 'WHERE version=?' không");
        log.info("==========================================");

        // Load entity
        Group group = groupRepository.findById(testGroupId).orElseThrow();
        Integer versionBeforeUpdate = group.getVersion();

        log.info("Loaded group: id={}, version={}, name='{}'",
                group.getId(), versionBeforeUpdate, group.getGroupName());

        // Update entity
        group.setGroupName("UPDATED-NAME");

        log.info("\n⚠️⚠️⚠️ XEM SQL DƯỚI ĐÂY - PHẢI CÓ 'where id=? and version=?' ⚠️⚠️⚠️\n");

        Group updated = groupRepository.saveAndFlush(group);

        log.info("\n✓ Update completed: version changed from {} to {}",
                versionBeforeUpdate, updated.getVersion());

        log.info("\n========================================");
        log.info("⚠️ KIỂM TRA LOG Ở TRÊN:");
        log.info("   - Nếu thấy: 'update groups ... where id=? and version=?' → PASS ✅");
        log.info("   - Nếu chỉ thấy: 'update groups ... where id=?' → FAIL ❌");
        log.info("========================================\n");

        assertNotNull(updated.getVersion());
        assertTrue(updated.getVersion() > versionBeforeUpdate,
                "Version should increment");
    }

    /**
     * ✅ TEST 2: Version có tự động tăng không
     */
    @Test
    void test2_VersionIncrementsAutomatically() {
        log.info("\n");
        log.info("==========================================");
        log.info("TEST 2: Version có tự động tăng không");
        log.info("==========================================");

        Group group = groupRepository.findById(testGroupId).orElseThrow();
        Integer v1 = group.getVersion();
        log.info("Initial version: {}", v1);

        // Update 1
        group.setGroupName("UPDATE-1");
        group = groupRepository.saveAndFlush(group);
        Integer v2 = group.getVersion();
        log.info("After update 1: version={}", v2);

        // Update 2
        group.setGroupName("UPDATE-2");
        group = groupRepository.saveAndFlush(group);
        Integer v3 = group.getVersion();
        log.info("After update 2: version={}", v3);

        // Verify
        assertNotNull(v1);
        assertNotNull(v2);
        assertNotNull(v3);
        assertTrue(v2 > v1, "Version should increment after first update");
        assertTrue(v3 > v2, "Version should increment after second update");

        log.info("✅ PASS: Version increments correctly: {} → {} → {}", v1, v2, v3);
    }

    /**
     * ✅ TEST 3: Concurrent update có throw exception không
     *
     * MÔ PHỎNG:
     * - 2 threads cùng load entity (cùng version)
     * - Thread 1 update trước → thành công, version tăng
     * - Thread 2 update sau → PHẢI THROW EXCEPTION
     */
    @Test
    void test3_ConcurrentUpdateThrowsException() {
        log.info("\n");
        log.info("==========================================");
        log.info("TEST 3: Concurrent Update Test");
        log.info("==========================================");

        // Clear context để đảm bảo clean state
        entityManager.clear();

        // Simulate 2 concurrent transactions
        Group entity1 = groupRepository.findById(testGroupId).orElseThrow();
        entityManager.detach(entity1); // Detach để mô phỏng separate transaction

        Group entity2 = groupRepository.findById(testGroupId).orElseThrow();
        entityManager.detach(entity2); // Detach để mô phỏng separate transaction

        log.info("Both entities loaded: version={}", entity1.getVersion());

        // Transaction 1: Update và commit
        entity1.setGroupName("UPDATED-BY-T1");
        Group saved1 = groupRepository.saveAndFlush(entity1);
        log.info("✓ Transaction 1 completed: new version={}", saved1.getVersion());

        entityManager.clear();

        // Transaction 2: Try to update (should fail vì version cũ)
        entity2.setGroupName("UPDATED-BY-T2");

        log.info("\n⚠️ Attempting update with stale version...");

        Exception exception = assertThrows(
            Exception.class,
            () -> {
                groupRepository.saveAndFlush(entity2);
                log.error("❌❌❌ FAIL: No exception thrown!");
            }
        );

        log.info("✅ Exception thrown: {}", exception.getClass().getSimpleName());
        log.info("   Message: {}", exception.getMessage());

        boolean isOptimisticLockException =
            exception instanceof jakarta.persistence.OptimisticLockException ||
            exception instanceof org.springframework.orm.ObjectOptimisticLockingFailureException;

        assertTrue(isOptimisticLockException,
                "Exception should be OptimisticLockException, but got: " + exception.getClass());

        log.info("✅ PASS: Optimistic Lock working correctly!");
        log.info("   Exception type: {}", exception.getClass().getSimpleName());
    }

    @AfterEach
    void cleanup() {
        log.info("==========================================\n\n");
    }
}

