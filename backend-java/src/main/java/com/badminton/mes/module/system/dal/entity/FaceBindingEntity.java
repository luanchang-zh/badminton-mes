package com.badminton.mes.module.system.dal.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 人脸登录与 MES 用户绑定实体。
 *
 * <p>只记录用户与 InsightFace Person 的对应关系，不保存照片和特征向量。
 * 表上的 {@code active_user_id}、{@code active_person_id} 是数据库生成列，仅供唯一索引使用，
 * 实体不映射，避免 Hibernate 尝试写入生成列。
 *
 * @author xxy-lh
 * @date 2026/11/08
 */
@Data
@Entity
@DynamicInsert
@Table(name = "sys_face_binding")
public class FaceBindingEntity {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MES 用户主键 */
    @Column(name = "user_id")
    private Long userId;

    /** InsightFace Collection id */
    @Column(name = "collection_id")
    private String collectionId;

    /** InsightFace Person id，固定为 user-{userId} */
    @Column(name = "person_id")
    private String personId;

    /** 用户勾选人脸信息处理同意的时间 */
    @Column(name = "consent_time")
    private LocalDateTime consentTime;

    /** 最近一次人脸登录成功时间 */
    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", insertable = false)
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @Column(name = "is_deleted", columnDefinition = "tinyint unsigned")
    private Boolean deleted;
}
