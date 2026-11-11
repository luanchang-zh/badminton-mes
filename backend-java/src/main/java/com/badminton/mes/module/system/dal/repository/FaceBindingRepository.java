package com.badminton.mes.module.system.dal.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import com.badminton.mes.module.system.dal.entity.FaceBindingEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人脸登录绑定 Repository。
 *
 * @author xxy-lh
 * @date 2026/11/08
 */
public interface FaceBindingRepository extends JpaRepository<FaceBindingEntity, Long> {

    /**
     * 查询用户的有效绑定。数据库唯一索引保证同一用户最多一条。
     *
     * @param userId 用户主键
     * @return 有效绑定
     */
    Optional<FaceBindingEntity> findByUserIdAndDeletedFalse(Long userId);

    /**
     * 按 Person 查询有效绑定，人脸登录核对搜索结果使用。数据库唯一索引保证同一 Collection 下最多一条。
     *
     * @param collectionId Collection id
     * @param personId Person id
     * @return 有效绑定
     */
    Optional<FaceBindingEntity> findByCollectionIdAndPersonIdAndDeletedFalse(String collectionId, String personId);

    /** 只更新仍有效的绑定，避免登录结束时回写旧实体，覆盖并发关闭产生的删除标记。 */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FaceBindingEntity binding
            SET binding.lastLoginTime = :loginTime
            WHERE binding.id = :id AND binding.deleted = false
            """)
    int updateLastLoginTimeIfActive(@Param("id") Long id, @Param("loginTime") LocalDateTime loginTime);
}
