<script setup lang="ts">
import { Calendar } from '@element-plus/icons-vue'
import shuttlecock from '@/assets/images/login-shuttlecock.webp'

const features = [
  { title: '生产计划', lines: ['从工单下达到排产，', '合理安排生产任务。'], icon: 'calendar' },
  { title: '现场执行', lines: ['汇集工序进度与报工', '信息，实时掌握现场。'], icon: 'layers' },
  { title: '质量追溯', lines: ['关联批次与检验记录，', '实现全流程可追溯。'], icon: 'shield' },
]
</script>

<template>
  <section class="login-intro" aria-labelledby="intro-title">
    <div class="login-intro__product" aria-hidden="true">
      <span class="login-intro__orbit"></span>
      <img :src="shuttlecock" alt="" width="1312" height="1199" decoding="async" />
    </div>
    <div class="login-intro__content">
      <p class="login-intro__eyebrow">羽毛球生产制造执行系统 <span>MES</span></p>
      <h1 id="intro-title">让生产更<span>高效</span>、更<span>透明</span></h1>
      <p class="login-intro__description">从工单下达到成品追溯，<br />统一管理生产、工艺、质量与现场执行。</p>
      <ul class="login-intro__features" aria-label="系统功能介绍">
        <li v-for="feature in features" :key="feature.title" class="feature-card">
          <span class="feature-card__icon" aria-hidden="true">
            <el-icon v-if="feature.icon === 'calendar'"><Calendar /></el-icon>
            <svg v-else-if="feature.icon === 'layers'" viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 11 13-8 13 8-13 8-13-8Z" /><path d="m3 17 13 8 13-8M3 23l13 8 13-8" transform="translate(0 -2)" /></svg>
            <svg v-else viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 2 28 7v9c0 7-7 12-12 15C11 28 4 23 4 16V7L16 2Z" /><path d="m10 16 4 4 8-8" /></svg>
          </span>
          <h2>{{ feature.title }}</h2>
          <p>{{ feature.lines[0] }}<br />{{ feature.lines[1] }}</p>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.login-intro { position: relative; min-width: 0; color: #162c39; }
.login-intro__product { position: absolute; top: calc(103 * var(--u)); left: calc(680 * var(--u)); width: calc(284 * var(--u)); pointer-events: none; }
.login-intro__product img { position: relative; display: block; width: 100%; height: auto; filter: drop-shadow(0 calc(14 * var(--u)) calc(16 * var(--u)) #2578791a); }
.login-intro__orbit { position: absolute; left: calc(-30 * var(--u)); bottom: calc(6 * var(--u)); width: calc(315 * var(--u)); height: calc(100 * var(--u)); border: calc(7 * var(--u)) solid #e0fff4dc; border-top-color: #effffb45; border-right-color: #d3ffdbbb; border-radius: 50%; transform: rotate(-21deg); }
.login-intro__content { position: relative; padding: calc(170 * var(--u)) 0 0 calc(96 * var(--u)); }
.login-intro__eyebrow { display: inline-flex; align-items: center; gap: calc(15 * var(--u)); height: calc(39 * var(--u)); margin: 0 0 calc(24 * var(--u)); padding: calc(3 * var(--u)) calc(4 * var(--u)) calc(3 * var(--u)) calc(22 * var(--u)); border-radius: 999px; background: linear-gradient(100deg, #ddf2f3, #eff9f9aa); color: #254953; font-size: calc(19 * var(--u)); font-weight: 600; line-height: 1.5; }
.login-intro__eyebrow > span { padding: calc(2 * var(--u)) calc(13 * var(--u)); border-radius: 999px; background: linear-gradient(120deg, #8bd180, #38a970); box-shadow: inset 0 1px 3px #ffffff7a, 0 3px 8px #3ca77c12; color: #fff; font-size: calc(19 * var(--u)); font-weight: 700; }
.login-intro h1 { width: max-content; margin: 0; font-size: calc(58 * var(--u)); font-weight: 900; font-style: italic; line-height: 1.3; letter-spacing: calc(-1.4 * var(--u)); }
.login-intro h1 > span { color: #148b74; }
.login-intro h1 > span:last-child { position: relative; color: #159480; }
.login-intro h1 > span:last-child::after { content: ''; position: absolute; bottom: calc(-17 * var(--u)); left: 0; width: 100%; height: calc(14 * var(--u)); border-top: calc(5 * var(--u)) solid #72c879; border-radius: 50%; transform: rotate(-6deg); }
.login-intro__description { margin: calc(17 * var(--u)) 0 0; color: #405869; font-size: calc(21 * var(--u)); font-weight: 500; line-height: calc(32 * var(--u)); }
.login-intro__features { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: calc(24 * var(--u)); width: calc(846 * var(--u)); margin: calc(53 * var(--u)) 0 0; padding: 0; list-style: none; }
.feature-card { height: calc(203 * var(--u)); padding: calc(25 * var(--u)) calc(39 * var(--u)); border: calc(2 * var(--u)) solid #ffffffc9; border-radius: calc(32 * var(--u)); background: linear-gradient(135deg, #ffffffef, #f5fcfbd9); box-shadow: 0 calc(12 * var(--u)) calc(28 * var(--u)) #366d7408; }
.feature-card__icon { display: grid; width: calc(50 * var(--u)); height: calc(50 * var(--u)); place-items: center; border-radius: calc(16 * var(--u)); color: #fff; background: radial-gradient(ellipse at 0 100%, #b0dd78, transparent 80%), linear-gradient(135deg, #6ec8a8, #129181); box-shadow: inset 0 1px 2px #ffffff35; }
.feature-card__icon .el-icon, .feature-card__icon svg { width: calc(29 * var(--u)); height: calc(29 * var(--u)); font-size: calc(29 * var(--u)); }
.feature-card h2 { margin: calc(10 * var(--u)) 0 calc(5 * var(--u)); color: #142833; font-size: calc(24 * var(--u)); line-height: calc(35 * var(--u)); font-weight: 700; }
.feature-card p { margin: 0; color: #40596b; font-size: calc(19 * var(--u)); font-weight: 500; line-height: calc(27 * var(--u)); white-space: nowrap; }
</style>
