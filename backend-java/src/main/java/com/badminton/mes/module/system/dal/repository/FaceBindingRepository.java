package com.badminton.mes.module.system.dal.repository;

import java.util.Optional;

import com.badminton.mes.module.system.dal.entity.FaceBindingEntity;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
