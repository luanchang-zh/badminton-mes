package com.badminton.mes.module.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.badminton.mes.common.core.PageResult;
import com.badminton.mes.common.enums.CommonStatusEnum;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.common.security.RoleCodeConstants;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.module.production.service.ProductionOrganizationReferenceQuery;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;
import com.badminton.mes.module.system.controller.vo.UserAssignmentReqVO;
import com.badminton.mes.module.system.controller.vo.UserPageReqVO;
import com.badminton.mes.module.system.controller.vo.UserRespVO;
import com.badminton.mes.module.system.controller.vo.UserSaveReqVO;
import com.badminton.mes.module.system.dal.entity.RoleEntity;
import com.badminton.mes.module.system.dal.entity.UserEntity;
import com.badminton.mes.module.system.dal.entity.UserRoleEntity;
import com.badminton.mes.module.system.dal.redis.LoginSessionRedisDAO;
import com.badminton.mes.module.system.dal.repository.RoleRepository;
import com.badminton.mes.module.system.dal.repository.UserRepository;
import com.badminton.mes.module.system.dal.repository.UserRoleRepository;
import com.badminton.mes.module.system.service.FaceService;
import com.badminton.mes.module.system.service.WechatUserBindingService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link UserServiceImpl} 单元测试。
 *
 * <p>覆盖用户 CRUD、角色对齐(复活已删除行)、状态变更、密码重置与分页查询，
 * 依赖全部 Mock。无事务上下文时 evictSessionAfterCommit 直接调用 Redis。
 *
 * @author 张竹灏
 * @date 2026/07/09
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final Long OPERATOR_ID = 9L;

    private static final Long USER_ID = 1L;

    private static final String USER_NO = "EMP001";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private LoginSessionRedisDAO loginSessionRedisDAO;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ProductionOrganizationReferenceQuery organizationReferenceQuery;

    @Mock
    private WechatUserBindingService wechatUserBindingService;

    @Mock
    private FaceService faceService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, userRoleRepository, roleRepository,
                loginSessionRedisDAO, passwordEncoder, organizationReferenceQuery,
                wechatUserBindingService, faceService, transactionManager);
        lenient().when(wechatUserBindingService.findActiveByUserId(any())).thenReturn(Optional.empty());
        lenient().when(wechatUserBindingService.findActiveByUserIds(anyCollection())).thenReturn(Map.of());
        lenient().when(wechatUserBindingService.findActiveUserIds()).thenReturn(Set.of());
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(OPERATOR_ID);
        loginUser.setUserNo("operator");
        loginUser.setRoleCodes(List.of(RoleCodeConstants.OPERATOR));
        SecurityContextHolder.set("test-token", loginUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("创建用户：密码哈希落库并分配角色")
    void createUserEncodesPasswordAndAssignsRoles() {
        UserSaveReqVO reqVO = buildSaveReqVO("initPass123", List.of(2L));
        when(userRepository.existsByUserNoAndDeletedFalse(USER_NO)).thenReturn(false);
        when(passwordEncoder.encode("initPass123")).thenReturn("$bcrypt-hash$");
        when(roleRepository.findByIdInAndDeletedFalse(Set.of(2L))).thenReturn(List.of(buildRole(2L, "PMC", 1)));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> {
            ((UserEntity) inv.getArgument(0)).setId(USER_ID);
            return inv.getArgument(0);
        });

        Long id = userService.createUser(reqVO);

        assertThat(id).isEqualTo(USER_ID);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$bcrypt-hash$");
        assertThat(captor.getValue().getStatus()).isEqualTo(CommonStatusEnum.ENABLED.getStatus());
    }

    @Test
    @DisplayName("创建用户：缺少初始密码抛 A0402")
    void createUserRejectsMissingPassword() {
        UserSaveReqVO reqVO = buildSaveReqVO(null, List.of(2L));

        assertThatThrownBy(() -> userService.createUser(reqVO))
                .isInstanceOfSatisfying(ServiceException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(SystemErrorCodeConstants.USER_INIT_PASSWORD_REQUIRED));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("创建用户：工号重复抛 A0506")
    void createUserRejectsDuplicateUserNo() {
        UserSaveReqVO reqVO = buildSaveReqVO("initPass123", List.of(2L));
        when(userRepository.existsByUserNoAndDeletedFalse(USER_NO)).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(reqVO))
                .isInstanceOfSatisfying(ServiceException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(SystemErrorCodeConstants.USER_NO_DUPLICATE));
    }

    @Test
    @DisplayName("创建用户：并发穿透查重由唯一索引兜底，转为 A0506")
    void createUserTranslatesUniqueConstraintViolation() {
        UserSaveReqVO reqVO = buildSaveReqVO("initPass123", List.of(2L));
        when(userRepository.existsByUserNoAndDeletedFalse(USER_NO)).thenReturn(false);
        when(passwordEncoder.encode("initPass123")).thenReturn("$hash$");
        when(roleRepository.findByIdInAndDeletedFalse(Set.of(2L))).thenReturn(List.of(buildRole(2L, "PMC", 1)));
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_no"));

        assertThatThrownBy(() -> userService.createUser(reqVO))
                .isInstanceOfSatisfying(ServiceException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(SystemErrorCodeConstants.USER_NO_DUPLICATE));
    }

    @Test
    @DisplayName("创建用户：角色不存在或已停用抛 A0402")
    void createUserRejectsInvalidRoles() {
        UserSaveReqVO reqVO = buildSaveReqVO("initPass123", List.of(2L, 99L));
        when(userRepository.existsByUserNoAndDeletedFalse(USER_NO)).thenReturn(false);
        // 只查到一个角色，期望两个 -> 数量不匹配
        when(roleRepository.findByIdInAndDeletedFalse(Set.of(2L, 99L))).thenReturn(List.of(buildRole(2L, "PMC", 1)));

        assertThatThrownBy(() -> userService.createUser(reqVO))
                .isInstanceOfSatisfying(ServiceException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(SystemErrorCodeConstants.USER_ROLE_INVALID));
    }

    @Test
    @DisplayName("创建用户：车间与产线层级不一致时拒绝")
    void createUserRejectsInvalidOrganizationAssignment() {
        UserSaveReqVO reqVO = buildSaveReqVO("initPass123", List.of(2L));
        reqVO.setWorkshopId(10L);
        reqVO.setLineId(20L);
        when(userRepository.existsByUserNoAndDeletedFalse(USER_NO)).thenReturn(false);
        when(roleRepository.findByIdInAndDeletedFalse(Set.of(2L)))
                .thenReturn(List.of(buildRole(2L, "PMC", 1)));
        when(organizationReferenceQuery.lockAndCheckAssignment(10L, 20L))
                .thenReturn(false);

        assertThatThrownBy(() -> userService.createUser(reqVO))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                SystemErrorCodeConstants.USER_ORGANIZATION_INVALID));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("修改用户：角色变化时提交后下线(无事务上下文直接调用)")
    void updateUserEvictsSessionWhenRolesChanged() {
        UserSaveReqVO reqVO = buildSaveReqVO(null, List.of(3L));
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        when(roleRepository.findByIdInAndDeletedFalse(Set.of(3L))).thenReturn(List.of(buildRole(3L, "OPERATOR", 1)));
        // 旧角色 [2]，新角色 [3]，角色集合不同 -> 触发下线
        when(userRoleRepository.findByUserIdAndDeletedFalse(USER_ID))
                .thenReturn(List.of(buildRelation(2L, false)));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(buildRelation(2L, false)));

        userService.updateUser(USER_ID, reqVO);

        verify(loginSessionRedisDAO).removeSessionByUserId(USER_ID);
    }

    @Test
    @DisplayName("职位分配：既有 ADMIN 在请求职位为空时仍被保留，并允许只调组织")
    void updateAssignmentPreservesExistingAdminWhenRequestedRolesEmpty() {
        UserAssignmentReqVO reqVO = new UserAssignmentReqVO();
        reqVO.setRoleIds(List.of());
        reqVO.setWorkshopId(10L);
        reqVO.setLineId(20L);
        UserRoleEntity adminRelation = buildRelation(1L, false);
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        when(organizationReferenceQuery.lockAndCheckAssignment(10L, 20L)).thenReturn(true);
        when(userRoleRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(List.of(adminRelation));
        lenient().when(roleRepository.findByIdInAndDeletedFalse(Set.of(1L)))
                .thenReturn(List.of(buildRole(1L, "ADMIN", 1)));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(adminRelation));

        userService.updateUserAssignment(USER_ID, reqVO);

        verify(userRepository).save(any(UserEntity.class));
        verify(userRoleRepository, never()).saveAll(any());
        verify(loginSessionRedisDAO).removeSessionByUserId(USER_ID);
    }

    @Test
    @DisplayName("职位分配：非管理员请求 ADMIN 时拒绝")
    void updateAssignmentRejectsRequestedAdminRole() {
        UserAssignmentReqVO reqVO = new UserAssignmentReqVO();
        reqVO.setRoleIds(List.of(1L));
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        when(roleRepository.findByIdInAndDeletedFalse(Set.of(1L)))
                .thenReturn(List.of(buildRole(1L, "ADMIN", 1)));

        assertThatThrownBy(() -> userService.updateUserAssignment(USER_ID, reqVO))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(SystemErrorCodeConstants.USER_ADMIN_ROLE_PROTECTED));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("职位分配：管理员可以授予 ADMIN")
    @SuppressWarnings("unchecked")
    void updateAssignmentAllowsAdminToAssignAdminRole() {
        LoginUser loginUser = SecurityContextHolder.getRequiredLoginUser();
        loginUser.setRoleCodes(List.of(RoleCodeConstants.ADMIN));
        UserAssignmentReqVO reqVO = new UserAssignmentReqVO();
        reqVO.setRoleIds(List.of(1L));
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        when(roleRepository.findByIdInAndDeletedFalse(Set.of(1L)))
                .thenReturn(List.of(buildRole(1L, RoleCodeConstants.ADMIN, 1)));
        when(userRoleRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(List.of());
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        userService.updateUserAssignment(USER_ID, reqVO);

        verify(userRoleRepository).saveAll(argThat(relations -> {
            List<UserRoleEntity> savedRelations = new ArrayList<>();
            relations.forEach(savedRelations::add);
            return savedRelations.size() == 1 && Long.valueOf(1L).equals(savedRelations.getFirst().getRoleId());
        }));
    }

    @Test
    @DisplayName("职位分配：非 ADMIN 用户不能清空全部职位")
    void updateAssignmentRejectsEmptyRolesForNonAdmin() {
        UserAssignmentReqVO reqVO = new UserAssignmentReqVO();
        reqVO.setRoleIds(List.of());
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        when(userRoleRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> userService.updateUserAssignment(USER_ID, reqVO))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(SystemErrorCodeConstants.USER_ROLE_REQUIRED));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("修改用户：角色不变时不触发下线")
    void updateUserDoesNotEvictWhenRolesUnchanged() {
        UserSaveReqVO reqVO = buildSaveReqVO(null, List.of(2L));
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        when(roleRepository.findByIdInAndDeletedFalse(Set.of(2L))).thenReturn(List.of(buildRole(2L, "PMC", 1)));
        when(userRoleRepository.findByUserIdAndDeletedFalse(USER_ID))
                .thenReturn(List.of(buildRelation(2L, false)));

        userService.updateUser(USER_ID, reqVO);

        verify(loginSessionRedisDAO, never()).removeSessionByUserId(any());
    }

    @Test
    @DisplayName("修改用户：用户不存在抛 A0201")
    void updateUserRejectsMissingUser() {
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(USER_ID, buildSaveReqVO(null, List.of(2L))))
                .isInstanceOfSatisfying(ServiceException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(SystemErrorCodeConstants.USER_NOT_EXISTS));
    }

    @Test
    @DisplayName("删除用户：逻辑删除并下线，清理授权关系")
    void deleteUserLogicDeletesAndEvicts() {
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));

        userService.deleteUser(USER_ID);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getDeleted()).isTrue();
        verify(userRoleRepository).logicDeleteByUserId(USER_ID);
        verify(loginSessionRedisDAO).removeSessionByUserId(USER_ID);
        // 人脸在事务外先删，数据库写操作在之后的事务里
        InOrder order = inOrder(faceService, transactionManager, userRepository);
        order.verify(faceService).removeEnrollment(USER_ID);
        order.verify(transactionManager).getTransaction(any());
        order.verify(userRepository).save(any());
    }

    @Test
    @DisplayName("删除用户：人脸服务不可用时返回 C0001，用户不被删除")
    void deleteUserFailsWhenFaceServiceDown() {
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        doThrow(new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE))
                .when(faceService).removeEnrollment(USER_ID);

        assertThatThrownBy(() -> userService.deleteUser(USER_ID))
                .isInstanceOfSatisfying(ServiceException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE));
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).logicDeleteByUserId(any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    @DisplayName("删除用户：不能删除当前登录账号")
    void deleteUserRejectsSelfDeletion() {
        assertThatThrownBy(() -> userService.deleteUser(OPERATOR_ID))
                .isInstanceOfSatisfying(ServiceException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(SystemErrorCodeConstants.USER_OPERATE_SELF_FORBIDDEN));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("停用用户：触发下线")
    void disableUserEvictsSession() {
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));

        userService.updateUserStatus(USER_ID, CommonStatusEnum.DISABLED.getStatus());

        verify(loginSessionRedisDAO).removeSessionByUserId(USER_ID);
    }

    @Test
    @DisplayName("启用用户：不触发下线")
    void enableUserDoesNotEvict() {
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(0)));

        userService.updateUserStatus(USER_ID, CommonStatusEnum.ENABLED.getStatus());

        verify(loginSessionRedisDAO, never()).removeSessionByUserId(any());
    }

    @Test
    @DisplayName("启用用户：所属车间或产线不可用时拒绝")
    void enableUserRejectsInvalidOrganizationAssignment() {
        UserEntity user = buildUser(0);
        user.setWorkshopId(10L);
        user.setLineId(20L);
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(user));
        when(organizationReferenceQuery.lockAndCheckAssignment(10L, 20L))
                .thenReturn(false);

        assertThatThrownBy(() -> userService.updateUserStatus(
                USER_ID, CommonStatusEnum.ENABLED.getStatus()))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                SystemErrorCodeConstants.USER_ORGANIZATION_INVALID));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("停用用户：不能停用当前登录账号")
    void disableUserRejectsSelfDisable() {
        assertThatThrownBy(() -> userService.updateUserStatus(OPERATOR_ID, CommonStatusEnum.DISABLED.getStatus()))
                .isInstanceOfSatisfying(ServiceException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(SystemErrorCodeConstants.USER_OPERATE_SELF_FORBIDDEN));
    }

    @Test
    @DisplayName("重置密码：编码落库并下线")
    void resetPasswordEncodesAndEvicts() {
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        when(passwordEncoder.encode("newPass456")).thenReturn("$new-hash$");

        userService.resetPassword(USER_ID, "newPass456");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$new-hash$");
        verify(loginSessionRedisDAO).removeSessionByUserId(USER_ID);
    }

    @Test
    @DisplayName("查询用户详情：回填角色并脱敏手机号")
    void getUserReturnsMaskedMobileAndRoles() {
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(buildUser(1)));
        when(userRoleRepository.findByUserIdInAndDeletedFalse(List.of(USER_ID)))
                .thenReturn(List.of(buildRelation(2L, false)));
        when(roleRepository.findByIdInAndDeletedFalse(anyCollection()))
                .thenReturn(List.of(buildRole(2L, "PMC", 1)));

        UserRespVO respVO = userService.getUser(USER_ID);

        assertThat(respVO.getUserNo()).isEqualTo(USER_NO);
        assertThat(respVO.getMobile()).isEqualTo("139****1219");
        assertThat(respVO.getRoleCodes()).containsExactly("PMC");
    }

    @Test
    @DisplayName("分页查询：count 为 0 直接返回空页，不执行列表查询")
    void getUserPageReturnsEmptyWhenCountZero() {
        UserPageReqVO reqVO = new UserPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        when(userRepository.count(any(Specification.class))).thenReturn(0L);

        PageResult<UserRespVO> result = userService.getUserPage(reqVO);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(userRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("分页查询：正常返回列表，手机号脱敏")
    @SuppressWarnings("unchecked")
    void getUserPageReturnsListWithMaskedMobile() {
        UserPageReqVO reqVO = new UserPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        when(userRepository.count(any(Specification.class))).thenReturn(1L);
        UserEntity user = buildUser(1);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(userRoleRepository.findByUserIdInAndDeletedFalse(List.of(USER_ID)))
                .thenReturn(List.of(buildRelation(2L, false)));
        when(roleRepository.findByIdInAndDeletedFalse(anyCollection()))
                .thenReturn(List.of(buildRole(2L, "PMC", 1)));

        PageResult<UserRespVO> result = userService.getUserPage(reqVO);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getMobile()).isEqualTo("139****1219");
    }

    @Test
    @DisplayName("分页查询：按角色筛选无命中用户时返回空页")
    void getUserPageReturnsEmptyWhenRoleFilterMatchesNoUser() {
        UserPageReqVO reqVO = new UserPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setRoleId(5L);
        when(userRoleRepository.findByRoleIdAndDeletedFalse(5L)).thenReturn(List.of());

        PageResult<UserRespVO> result = userService.getUserPage(reqVO);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
    }

    @Test
    @DisplayName("分页查询：筛选已绑定但当前无绑定用户时直接返回空页")
    void getUserPageReturnsEmptyWhenWechatBoundFilterHasNoMatches() {
        UserPageReqVO reqVO = new UserPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setWechatBound(true);
        when(wechatUserBindingService.findActiveUserIds()).thenReturn(Set.of());

        PageResult<UserRespVO> result = userService.getUserPage(reqVO);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(userRepository, never()).count(any(Specification.class));
    }

    @Test
    @DisplayName("分页查询：请求页码超过总页数时回退到最后一页")
    @SuppressWarnings("unchecked")
    void getUserPageFallsBackToLastPage() {
        UserPageReqVO reqVO = new UserPageReqVO();
        reqVO.setPageNo(99);
        reqVO.setPageSize(10);
        // 12 条记录，pageSize=10 -> 总 2 页，请求第 99 页应回退到第 2 页
        when(userRepository.count(any(Specification.class))).thenReturn(12L);
        UserEntity user = buildUser(1);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        // loadRolesByUserIds 查到空关系后直接返回，不查角色
        when(userRoleRepository.findByUserIdInAndDeletedFalse(anyCollection()))
                .thenReturn(List.of());

        PageResult<UserRespVO> result = userService.getUserPage(reqVO);

        assertThat(result.getPageNo()).isEqualTo(2);
    }

    /**
     * 构造创建/修改请求 VO。
     *
     * @param password 初始密码，修改时传 null
     * @param roleIds  角色 id 列表
     * @return 请求 VO
     */
    private UserSaveReqVO buildSaveReqVO(String password, List<Long> roleIds) {
        UserSaveReqVO reqVO = new UserSaveReqVO();
        reqVO.setUserNo(USER_NO);
        reqVO.setUserName("张三");
        reqVO.setPassword(password);
        reqVO.setMobile("13912341219");
        reqVO.setRoleIds(roleIds);
        return reqVO;
    }

    /**
     * 构造用户实体。
     *
     * @param status 状态(1 启用 0 停用)
     * @return 用户实体
     */
    private UserEntity buildUser(int status) {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setUserNo(USER_NO);
        user.setUserName("张三");
        user.setPassword("$old-hash$");
        user.setMobile("13912341219");
        user.setStatus(status);
        user.setDeleted(false);
        return user;
    }

    /**
     * 构造角色实体。
     *
     * @param id       角色主键
     * @param roleCode 角色编码
     * @param status   状态(1 启用 0 停用)
     * @return 角色实体
     */
    private RoleEntity buildRole(Long id, String roleCode, int status) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode + "名称");
        role.setStatus(status);
        role.setDeleted(false);
        return role;
    }

    /**
     * 构造用户角色关系实体。
     *
     * @param roleId  角色 id
     * @param deleted 是否已逻辑删除
     * @return 关系实体
     */
    private UserRoleEntity buildRelation(Long roleId, boolean deleted) {
        UserRoleEntity relation = new UserRoleEntity();
        relation.setId(roleId);
        relation.setUserId(USER_ID);
        relation.setRoleId(roleId);
        relation.setDeleted(deleted);
        return relation;
    }
}
