package com.badminton.mes.module.system.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.badminton.mes.common.core.PageResult;
import com.badminton.mes.common.enums.CommonStatusEnum;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.RoleCodeConstants;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.common.util.DesensitizeUtils;
import com.badminton.mes.module.production.service.ProductionOrganizationReferenceQuery;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;
import com.badminton.mes.module.system.controller.vo.UserPageReqVO;
import com.badminton.mes.module.system.controller.vo.UserAssignmentReqVO;
import com.badminton.mes.module.system.controller.vo.UserRespVO;
import com.badminton.mes.module.system.controller.vo.UserSaveReqVO;
import com.badminton.mes.module.system.dal.entity.RoleEntity;
import com.badminton.mes.module.system.dal.entity.UserEntity;
import com.badminton.mes.module.system.dal.entity.UserRoleEntity;
import com.badminton.mes.module.system.dal.entity.WechatUserBindingEntity;
import com.badminton.mes.module.system.dal.redis.LoginSessionRedisDAO;
import com.badminton.mes.module.system.dal.repository.RoleRepository;
import com.badminton.mes.module.system.dal.repository.UserRepository;
import com.badminton.mes.module.system.dal.repository.UserRoleRepository;
import com.badminton.mes.module.system.dal.repository.UserSpecifications;
import com.badminton.mes.module.system.service.FaceService;
import com.badminton.mes.module.system.service.UserService;
import com.badminton.mes.module.system.service.WechatUserBindingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 系统用户 Service 实现。
 *
 * <p>停用/删除/重置密码/调整角色等会削弱既有会话权限的操作，
 * 一律按用户强制下线，避免旧会话继续持有原权限。
 *
 * @author 张竹灏
 * @date 2026/07/09
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;

    private final UserRoleRepository userRoleRepository;

    private final RoleRepository roleRepository;

    private final LoginSessionRedisDAO loginSessionRedisDAO;

    private final PasswordEncoder passwordEncoder;

    private final ProductionOrganizationReferenceQuery organizationReferenceQuery;

    private final WechatUserBindingService wechatUserBindingService;

    private final FaceService faceService;

    /** 删除用户需要先在事务外调用人脸服务，再开启数据库事务，不能用方法级 @Transactional */
    private final TransactionTemplate transactionTemplate;

    /**
     * 构造器注入，依赖不可变。
     *
     * @param userRepository       用户 Repository
     * @param userRoleRepository   用户角色关系 Repository
     * @param roleRepository       角色 Repository
     * @param loginSessionRedisDAO 登录会话 DAO
     * @param passwordEncoder      密码编码器
     * @param organizationReferenceQuery 生产组织引用查询契约
     * @param wechatUserBindingService 微信绑定 Service
     * @param faceService          人脸服务，删除用户时同步删除 InsightFace Person
     * @param transactionManager   事务管理器
     */
    public UserServiceImpl(UserRepository userRepository, UserRoleRepository userRoleRepository,
                           RoleRepository roleRepository, LoginSessionRedisDAO loginSessionRedisDAO,
                           PasswordEncoder passwordEncoder,
                           ProductionOrganizationReferenceQuery organizationReferenceQuery,
                           WechatUserBindingService wechatUserBindingService,
                           FaceService faceService, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.loginSessionRedisDAO = loginSessionRedisDAO;
        this.passwordEncoder = passwordEncoder;
        this.organizationReferenceQuery = organizationReferenceQuery;
        this.wechatUserBindingService = wechatUserBindingService;
        this.faceService = faceService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserSaveReqVO reqVO) {
        // 创建与修改共用 VO，初始密码仅创建时必填，由此在 Service 校验
        if (!StringUtils.hasText(reqVO.getPassword())) {
            throw new ServiceException(SystemErrorCodeConstants.USER_INIT_PASSWORD_REQUIRED);
        }
        if (userRepository.existsByUserNoAndDeletedFalse(reqVO.getUserNo())) {
            throw new ServiceException(SystemErrorCodeConstants.USER_NO_DUPLICATE);
        }
        validateRolesUsable(reqVO.getRoleIds());
        validateOrganization(reqVO.getWorkshopId(), reqVO.getLineId());

        UserEntity user = new UserEntity();
        user.setUserNo(reqVO.getUserNo());
        user.setUserName(reqVO.getUserName());
        user.setPassword(passwordEncoder.encode(reqVO.getPassword()));
        user.setMobile(reqVO.getMobile());
        user.setWorkshopId(reqVO.getWorkshopId());
        user.setLineId(reqVO.getLineId());
        user.setStatus(CommonStatusEnum.ENABLED.getStatus());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 并发创建或工号被已删除用户占用时，由唯一索引 uk_user_no 兜底
            throw new ServiceException(SystemErrorCodeConstants.USER_NO_DUPLICATE);
        }
        replaceUserRoles(user.getId(), reqVO.getRoleIds());

        logger.info("[创建用户] id: {}, userNo: {}, operator: {}",
                user.getId(), user.getUserNo(), SecurityContextHolder.getRequiredLoginUserId());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserSaveReqVO reqVO) {
        UserEntity user = validateUserExists(id);
        validateRolesUsable(reqVO.getRoleIds());
        validateOrganization(reqVO.getWorkshopId(), reqVO.getLineId());

        // 工号不允许修改，密码走专用接口，两个字段均忽略
        user.setUserName(reqVO.getUserName());
        user.setMobile(reqVO.getMobile());
        user.setWorkshopId(reqVO.getWorkshopId());
        user.setLineId(reqVO.getLineId());
        userRepository.save(user);

        Set<Long> oldRoleIds = userRoleRepository.findByUserIdAndDeletedFalse(id).stream()
                .map(UserRoleEntity::getRoleId)
                .collect(Collectors.toSet());
        Set<Long> newRoleIds = new LinkedHashSet<>(reqVO.getRoleIds());
        if (!oldRoleIds.equals(newRoleIds)) {
            replaceUserRoles(id, newRoleIds);
            // 角色变化后旧会话仍持有原角色，提交后下线让新角色重新登录生效
            evictSessionAfterCommit(id);
        }
        logger.info("[修改用户] id: {}, operator: {}", id, SecurityContextHolder.getRequiredLoginUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserAssignment(Long id, UserAssignmentReqVO reqVO) {
        UserEntity user = validateUserExists(id);
        boolean canAssignAdmin = currentUserHasRole(RoleCodeConstants.ADMIN);
        Set<Long> requestedRoleIds = new LinkedHashSet<>(reqVO.getRoleIds());
        validateAssignableRoles(requestedRoleIds, canAssignAdmin);
        validateOrganization(reqVO.getWorkshopId(), reqVO.getLineId());

        Set<Long> targetRoleIds = new LinkedHashSet<>(requestedRoleIds);
        List<UserRoleEntity> activeRelations = userRoleRepository.findByUserIdAndDeletedFalse(id);
        Set<Long> activeRoleIds = activeRelations.stream()
                .map(UserRoleEntity::getRoleId)
                .collect(Collectors.toSet());
        if (!canAssignAdmin && !activeRoleIds.isEmpty()) {
            roleRepository.findByIdInAndDeletedFalse(activeRoleIds).stream()
                    .filter(role -> RoleCodeConstants.ADMIN.equals(role.getRoleCode()))
                    .map(RoleEntity::getId)
                    .forEach(targetRoleIds::add);
        }
        if (targetRoleIds.isEmpty()) {
            throw new ServiceException(SystemErrorCodeConstants.USER_ROLE_REQUIRED);
        }

        user.setWorkshopId(reqVO.getWorkshopId());
        user.setLineId(reqVO.getLineId());
        userRepository.save(user);
        replaceUserRoles(id, targetRoleIds);
        evictSessionAfterCommit(id);
        logger.info("[调整用户职位与组织] id: {}, operator: {}",
                id, SecurityContextHolder.getRequiredLoginUserId());
    }

    /**
     * 删除用户。
     *
     * <p>不加方法级事务：InsightFace 读超时最长 30 秒，放在事务里会一直占着数据库连接。
     * 顺序为校验 → 事务外删除人脸 Person 和绑定 → {@link TransactionTemplate} 内执行原有写操作。
     * 人脸服务不可用时直接返回 C0001，避免账号已删、特征仍留在人脸库；
     * 人脸已删、数据库写操作回滚时，用户仍在，绑定由查询时自愈，管理员重试即可。
     */
    @Override
    public void deleteUser(Long id) {
        validateNotCurrentUser(id);
        validateUserExists(id);
        faceService.removeEnrollment(id);
        transactionTemplate.executeWithoutResult(status -> {
            UserEntity user = validateUserExists(id);
            user.setDeleted(true);
            userRepository.save(user);
            // 授权关系随用户失效
            userRoleRepository.logicDeleteByUserId(id);
            evictSessionAfterCommit(id);
            logger.info("[删除用户] id: {}, userNo: {}, operator: {}",
                    id, user.getUserNo(), SecurityContextHolder.getRequiredLoginUserId());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long id, Integer status) {
        if (CommonStatusEnum.DISABLED.getStatus().equals(status)) {
            validateNotCurrentUser(id);
        }
        UserEntity user = validateUserExists(id);
        if (CommonStatusEnum.ENABLED.getStatus().equals(status)) {
            validateOrganization(user.getWorkshopId(), user.getLineId());
        }
        user.setStatus(status);
        userRepository.save(user);
        if (CommonStatusEnum.DISABLED.getStatus().equals(status)) {
            // 停用即强制下线
            evictSessionAfterCommit(id);
        }
        logger.info("[变更用户状态] id: {}, status: {}, operator: {}",
                id, status, SecurityContextHolder.getRequiredLoginUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        UserEntity user = validateUserExists(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // 重置后强制下线，持旧凭据的会话立即失效
        evictSessionAfterCommit(id);
        logger.info("[重置密码] id: {}, operator: {}", id, SecurityContextHolder.getRequiredLoginUserId());
    }

    @Override
    public UserRespVO getUser(Long id) {
        UserEntity user = validateUserExists(id);
        Map<Long, List<RoleEntity>> rolesByUser = loadRolesByUserIds(List.of(id));
        WechatUserBindingEntity binding = wechatUserBindingService.findActiveByUserId(id).orElse(null);
        return toRespVO(user, rolesByUser.getOrDefault(id, List.of()), binding);
    }

    @Override
    public PageResult<UserRespVO> getUserPage(UserPageReqVO reqVO) {
        Set<Long> includedUserIds = null;
        if (reqVO.getRoleId() != null) {
            includedUserIds = userRoleRepository.findByRoleIdAndDeletedFalse(reqVO.getRoleId()).stream()
                    .map(UserRoleEntity::getUserId)
                    .collect(Collectors.toSet());
            if (includedUserIds.isEmpty()) {
                return PageResult.empty(reqVO.getPageNo(), reqVO.getPageSize());
            }
        }
        Set<Long> excludedUserIds = null;
        if (reqVO.getWechatBound() != null) {
            Set<Long> boundUserIds = wechatUserBindingService.findActiveUserIds();
            if (Boolean.TRUE.equals(reqVO.getWechatBound())) {
                if (boundUserIds.isEmpty()) {
                    return PageResult.empty(reqVO.getPageNo(), reqVO.getPageSize());
                }
                if (includedUserIds == null) {
                    includedUserIds = new HashSet<>(boundUserIds);
                } else {
                    includedUserIds.retainAll(boundUserIds);
                }
                if (includedUserIds.isEmpty()) {
                    return PageResult.empty(reqVO.getPageNo(), reqVO.getPageSize());
                }
            } else if (!boundUserIds.isEmpty()) {
                excludedUserIds = boundUserIds;
            }
        }
        Specification<UserEntity> specification = UserSpecifications.page(
                reqVO, includedUserIds, excludedUserIds);
        // 先 count：总数为 0 直接返回空页，省一次列表查询(SQL-005)
        long total = userRepository.count(specification);
        if (total == 0) {
            return PageResult.empty(reqVO.getPageNo(), reqVO.getPageSize());
        }

        // 请求页码超过总页数时按最后一页返回(API-009)
        int pageSize = reqVO.getPageSize();
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        int pageNo = Math.min(reqVO.getPageNo(), totalPages);
        PageRequest pageRequest = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<UserEntity> page = userRepository.findAll(specification, pageRequest);

        List<Long> userIds = page.getContent().stream().map(UserEntity::getId).toList();
        Map<Long, List<RoleEntity>> rolesByUser = loadRolesByUserIds(userIds);
        Map<Long, WechatUserBindingEntity> bindingsByUser = wechatUserBindingService.findActiveByUserIds(userIds);
        List<UserRespVO> list = page.getContent().stream()
                .map(user -> toRespVO(user, rolesByUser.getOrDefault(user.getId(), List.of()),
                        bindingsByUser.get(user.getId())))
                .toList();
        return PageResult.of(list, total, pageNo, pageSize);
    }

    /**
     * 校验用户存在且未删除。
     *
     * @param id 用户主键
     * @return 用户实体
     */
    private UserEntity validateUserExists(Long id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ServiceException(SystemErrorCodeConstants.USER_NOT_EXISTS));
    }

    /**
     * 禁止对当前登录账号执行停用/删除，防止管理员自锁后无人恢复。
     *
     * @param id 目标用户主键
     */
    private void validateNotCurrentUser(Long id) {
        if (SecurityContextHolder.getRequiredLoginUserId().equals(id)) {
            throw new ServiceException(SystemErrorCodeConstants.USER_OPERATE_SELF_FORBIDDEN);
        }
    }

    /**
     * 校验待分配角色全部存在、未删除且启用。
     *
     * @param roleIds 角色主键列表
     */
    private void validateRolesUsable(List<Long> roleIds) {
        Set<Long> distinctIds = new HashSet<>(roleIds);
        List<RoleEntity> roles = roleRepository.findByIdInAndDeletedFalse(distinctIds);
        boolean allEnabled = roles.size() == distinctIds.size() && roles.stream()
                .allMatch(role -> CommonStatusEnum.ENABLED.getStatus().equals(role.getStatus()));
        if (!allEnabled) {
            throw new ServiceException(SystemErrorCodeConstants.USER_ROLE_INVALID);
        }
    }

    /**
     * 校验职位分配请求中的角色均存在且启用，并保护 ADMIN 角色。
     *
     * @param roleIds 目标角色主键集合
     * @param canAssignAdmin 当前用户是否可修改 ADMIN
     */
    private void validateAssignableRoles(Collection<Long> roleIds, boolean canAssignAdmin) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<RoleEntity> roles = roleRepository.findByIdInAndDeletedFalse(roleIds);
        boolean allEnabled = roles.size() == roleIds.size() && roles.stream()
                .allMatch(role -> CommonStatusEnum.ENABLED.getStatus().equals(role.getStatus()));
        if (!allEnabled) {
            throw new ServiceException(SystemErrorCodeConstants.USER_ROLE_INVALID);
        }
        boolean containsAdmin = roles.stream()
                .anyMatch(role -> RoleCodeConstants.ADMIN.equals(role.getRoleCode()));
        if (containsAdmin && !canAssignAdmin) {
            throw new ServiceException(SystemErrorCodeConstants.USER_ADMIN_ROLE_PROTECTED);
        }
    }

    /**
     * 判断当前登录用户是否拥有指定角色。
     *
     * @param roleCode 角色编码
     * @return 拥有角色时返回 true
     */
    private boolean currentUserHasRole(String roleCode) {
        List<String> roleCodes = SecurityContextHolder.getRequiredLoginUser().getRoleCodes();
        return roleCodes != null && roleCodes.contains(roleCode);
    }

    /**
     * 校验用户所属车间与产线存在、启用且层级一致。
     *
     * @param workshopId 车间主键，可空
     * @param lineId 产线主键，可空
     */
    private void validateOrganization(Long workshopId, Long lineId) {
        if (workshopId == null && lineId == null) {
            return;
        }
        if (!organizationReferenceQuery.lockAndCheckAssignment(workshopId, lineId)) {
            throw new ServiceException(SystemErrorCodeConstants.USER_ORGANIZATION_INVALID);
        }
    }

    /**
     * 把用户角色关系对齐到目标集合。
     *
     * <p>uk_user_role 唯一键覆盖已逻辑删除的行，重新授予历史角色必须
     * "复活"旧行(deleted 置回 false)而不能新插入，否则触发唯一键冲突；
     * 不在目标集合中的有效行逻辑删除，全新角色才插入新行。
     *
     * @param userId      用户主键
     * @param newRoleIds 目标角色 id 集合
     */
    private void replaceUserRoles(Long userId, Collection<Long> newRoleIds) {
        Set<Long> pendingIds = new LinkedHashSet<>(newRoleIds);
        List<UserRoleEntity> changed = new ArrayList<>();
        for (UserRoleEntity relation : userRoleRepository.findByUserId(userId)) {
            boolean shouldHave = pendingIds.remove(relation.getRoleId());
            boolean currentlyDeleted = Boolean.TRUE.equals(relation.getDeleted());
            if (shouldHave && currentlyDeleted) {
                relation.setDeleted(false);
                changed.add(relation);
            } else if (!shouldHave && !currentlyDeleted) {
                relation.setDeleted(true);
                changed.add(relation);
            }
        }
        for (Long roleId : pendingIds) {
            UserRoleEntity relation = new UserRoleEntity();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            changed.add(relation);
        }
        if (!changed.isEmpty()) {
            userRoleRepository.saveAll(changed);
        }
    }

    /**
     * 批量装载多个用户的角色，避免分页列表逐行查询。
     *
     * @param userIds 用户主键集合
     * @return userId → 角色列表(按 id 升序)，无角色的用户不在 Map 中
     */
    private Map<Long, List<RoleEntity>> loadRolesByUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRoleEntity> relations = userRoleRepository.findByUserIdInAndDeletedFalse(userIds);
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> roleIds = relations.stream().map(UserRoleEntity::getRoleId).collect(Collectors.toSet());
        Map<Long, RoleEntity> roleById = roleRepository.findByIdInAndDeletedFalse(roleIds).stream()
                .collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        Map<Long, List<RoleEntity>> rolesByUser = relations.stream()
                .filter(relation -> roleById.containsKey(relation.getRoleId()))
                .collect(Collectors.groupingBy(UserRoleEntity::getUserId,
                        Collectors.mapping(relation -> roleById.get(relation.getRoleId()), Collectors.toList())));
        rolesByUser.values().forEach(roles -> roles.sort(Comparator.comparing(RoleEntity::getId)));
        return rolesByUser;
    }

    /**
     * 实体转响应 VO，手机号脱敏(SEC-002)，密码不出参。
     *
     * @param user  用户实体
     * @param roles 用户角色列表(按 id 升序)
     * @return 响应 VO
     */
    private UserRespVO toRespVO(UserEntity user, List<RoleEntity> roles,
                                WechatUserBindingEntity binding) {
        UserRespVO respVO = new UserRespVO();
        respVO.setId(user.getId());
        respVO.setUserNo(user.getUserNo());
        respVO.setUserName(user.getUserName());
        respVO.setMobile(DesensitizeUtils.maskMobile(user.getMobile()));
        respVO.setWorkshopId(user.getWorkshopId());
        respVO.setLineId(user.getLineId());
        respVO.setStatus(user.getStatus());
        respVO.setRoleIds(roles.stream().map(RoleEntity::getId).toList());
        respVO.setRoleCodes(roles.stream().map(RoleEntity::getRoleCode).toList());
        respVO.setRoleNames(roles.stream().map(RoleEntity::getRoleName).toList());
        respVO.setWechatBound(binding != null);
        respVO.setCreateTime(user.getCreateTime());
        return respVO;
    }

    /**
     * 事务提交后再删除会话：避免事务回滚时数据未变却误踢用户，
     * 与 WorkOrderServiceImpl 缓存失效保持同一范式。
     * 无事务上下文时直接删除。
     *
     * @param userId 待下线用户主键
     */
    private void evictSessionAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            loginSessionRedisDAO.removeSessionByUserId(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                loginSessionRedisDAO.removeSessionByUserId(userId);
            }
        });
    }
}
