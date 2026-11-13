package com.badminton.mes.module.report.service.impl.kanban;

import com.badminton.mes.module.report.controller.vo.RealtimeProductionRespVO;
import com.badminton.mes.module.report.dal.redis.KanbanRedisKeyConstants;
import com.badminton.mes.module.report.service.RealtimeProductionService;
import com.badminton.mes.module.report.service.kanban.KanbanSnapshotService.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 看板快照缓存命中、损坏回源、写入降级和已知范围测试。 @author 范家权 */
@ExtendWith(MockitoExtension.class)
class KanbanSnapshotServiceImplTest {

    @Mock
    private RealtimeProductionService realtimeService;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private KanbanSnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new KanbanSnapshotServiceImpl(realtimeService, redis, new ObjectMapper());
    }

    @Test
    void validCachedSnapshotReturnsWithoutQueryingRealtimeService() {
        String key = KanbanRedisKeyConstants.snapshotKey("line", 8L);
        when(values.get(key)).thenReturn("{\"version\":1,\"dataStatus\":\"FRESH\"}");

        Map<String, Object> result = service.get("line", 8L);

        assertThat(result).containsEntry("dataStatus", "FRESH");
        assertThat(result.get("version").toString()).isEqualTo("1");
        // 看板快照走 overviewForKanban：它不依赖登录上下文，不能被换成 overview
        verify(realtimeService, never()).overviewForKanban(org.mockito.ArgumentMatchers.any());
        assertThat(service.knownScopes()).containsExactly(new Scope("line", 8L));
    }

    @Test
    void corruptCachedSnapshotFallsBackAndRefreshesRequestedLine() {
        String key = KanbanRedisKeyConstants.snapshotKey("line", 8L);
        when(values.get(key)).thenReturn("{broken-json");
        when(realtimeService.overviewForKanban(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RealtimeProductionRespVO.Overview());

        Map<String, Object> result = service.get("line", 8L);

        ArgumentCaptor<com.badminton.mes.module.report.controller.vo.RealtimeReportQueryReqVO> captor =
                ArgumentCaptor.forClass(
                        com.badminton.mes.module.report.controller.vo.RealtimeReportQueryReqVO.class);
        verify(realtimeService).overviewForKanban(captor.capture());
        assertThat(captor.getValue().getLineId()).isEqualTo(8L);
        assertThat(captor.getValue().getWorkshopId()).isNull();
        assertThat(result).containsEntry("dataStatus", "FRESH");
        verify(values).set(eq(key), anyString(), eq(KanbanRedisKeyConstants.SNAPSHOT_TTL));
    }

    @Test
    void redisWriteFailureReturnsPartialSnapshotInsteadOfFailingDashboard() {
        when(realtimeService.overviewForKanban(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RealtimeProductionRespVO.Overview());
        doThrow(new IllegalStateException("redis unavailable")).when(values)
                .set(anyString(), anyString(), eq(KanbanRedisKeyConstants.SNAPSHOT_TTL));

        Map<String, Object> result = service.refresh("workshop", 6L);

        assertThat(result).containsEntry("dataStatus", "PARTIAL");
        assertThat(result.get("sourceWarnings").toString()).contains("Redis 快照写入失败");
        assertThat(service.knownScopes()).contains(new Scope("workshop", 6L));
    }
}
