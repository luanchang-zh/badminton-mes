async page => {
  const baseURL = /^https?:/.test(page.url()) ? new URL(page.url()).origin : 'http://127.0.0.1:5173'
  const browser = page.context().browser()
  const checks = [], traces = []
  const assert = (value, message) => { if (!value) throw new Error(message) }
  const user = { userId: 914, userNo: 'prompt-test', userName: '提示验收', roleCodes: ['OPERATOR'] }
  async function setup(viewport, reducedMotion = 'no-preference', restoreSession = false) {
    const context = await browser.newContext({ viewport, reducedMotion })
    const p = await context.newPage()
    const errors = []
    p.on('pageerror', error => errors.push(error.message))
    await p.addInitScript(({ user, restoreSession }) => {
      localStorage.clear()
      if (restoreSession) {
        localStorage.setItem('mes_token', 'isolated-prompt-session')
        localStorage.setItem('mes_user', JSON.stringify(user))
      }
      const Original = window.MutationObserver
      window.__promptBodyObservers = new Set()
      window.MutationObserver = class extends Original {
        observe(target, options) {
          if (target === document.body && options.childList && !options.subtree) window.__promptBodyObservers.add(this)
          return super.observe(target, options)
        }
        disconnect() { window.__promptBodyObservers.delete(this); return super.disconnect() }
      }
    }, { user, restoreSession })
    await p.route(`${baseURL}/api/**`, async route => {
      const path = new URL(route.request().url()).pathname
      let data = []
      if (path.endsWith('/auth/login')) {
        await p.evaluate(() => { window.__authRespondedAt = performance.now() })
        data = { ...user, token: 'isolated-prompt-session' }
      }
      if (path.endsWith('/auth/profile')) data = { ...user, roleNames: ['操作工'] }
      if (path.endsWith('/face/profile')) data = { enrolled: false, faceLoginAllowed: true, faceCount: 0, maxFaces: 5, samples: [] }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: '00000', message: '成功', data, userTip: null }) })
    })
    if (restoreSession) await p.goto(`${baseURL}/dashboard`)
    else {
      await p.goto(`${baseURL}/login`)
      await p.locator('#login-user-no').fill(user.userNo)
      await p.locator('#login-password').fill('test-password')
      await p.getByRole('button', { name: '登 录', exact: true }).click()
      await p.waitForURL('**/dashboard')
    }
    await p.locator('.face-prompt').waitFor()
    await p.waitForTimeout(300)
    return { context, p, errors }
  }
  const position = p => p.locator('.face-prompt').evaluate(el => el.getBoundingClientRect().top)
  const assertStack = async p => {
    const rects = await p.evaluate(() => {
      const welcome = document.querySelector('.el-message.is-center')
      const prompt = document.querySelector('.face-prompt')
      const a = welcome?.getBoundingClientRect(), b = prompt.getBoundingClientRect()
      return { bottom: a?.bottom, top: b.top, welcomeCenter: a && a.left + a.width / 2, promptCenter: b.left + b.width / 2, viewportCenter: innerWidth / 2, welcomeWidth: a?.width, promptWidth: b.width,
        welcomeBackground: welcome && getComputedStyle(welcome).backgroundColor, promptBackground: getComputedStyle(prompt).backgroundColor,
        welcomeRadius: welcome && getComputedStyle(welcome).borderRadius, promptRadius: getComputedStyle(prompt).borderRadius }
    })
    assert(rects.bottom !== undefined && Math.abs(rects.top - rects.bottom - 20) < 1, 'Welcome and face prompt must have a 20px gap')
    assert(Math.abs(rects.welcomeCenter - rects.viewportCenter) < 1 && Math.abs(rects.promptCenter - rects.viewportCenter) < 1, 'Both notices are centered on the viewport on the same vertical axis')
    assert(rects.welcomeWidth < rects.promptWidth && rects.welcomeBackground !== rects.promptBackground, 'Welcome keeps its compact native success style while face prompt keeps its panel style')
  }

  {
    const { context, p, errors } = await setup({ width: 1920, height: 920 })
    try {
      await assertStack(p)
      await p.screenshot({ path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/prompt-stack-before.png', animations: 'disabled' })
      const samples = await p.evaluate(async () => {
        const samples = []
        const start = performance.now()
        await new Promise(resolve => {
          const sample = () => {
            const welcome = document.querySelector('.mes-welcome-notice')
            samples.push({ ms: performance.now() - start, sinceLogin: performance.now() - window.__authRespondedAt, welcomeVisible: !!welcome && !welcome.classList.contains('el-message-fade-leave-active'), leaving: welcome?.classList.contains('el-message-fade-leave-active'), y: document.querySelector('.face-prompt').getBoundingClientRect().top })
            if (performance.now() - start < 2800) requestAnimationFrame(sample)
            else resolve()
          }
          requestAnimationFrame(sample)
        })
        return samples
      })
      const initial = samples[0].y, final = samples.at(-1).y
      const middle = samples.filter(sample => sample.y > 17 && sample.y < initial - 1)
      assert(initial > 60 && Math.abs(final - 16) < 1, 'Prompt must take welcome position after automatic dismissal')
      assert(middle.length >= 3, 'Movement must have visible intermediate positions')
      assert(samples.every((sample, index) => !index || sample.y <= samples[index - 1].y + .6), 'Movement must not jump back or overshoot')
      assert(samples.some(sample => sample.sinceLogin >= 1400 && sample.welcomeVisible), 'Welcome remains for two seconds')
      const closing = samples.find(sample => sample.leaving)
      assert(closing && closing.sinceLogin >= 1900 && closing.sinceLogin < 2500, 'Welcome starts closing at two seconds')
      await p.screenshot({ path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/prompt-stack-after.png', animations: 'disabled' })
      traces.push({ initial, final, intermediateFrames: middle.length, durationMs: middle.at(-1).ms - middle[0].ms, welcomeCloseMs: closing.sinceLogin })
      checks.push('1920x920: native compact green welcome and face panel share the viewport center; welcome closes at two seconds and face prompt moves smoothly')
      await p.getByRole('button', { name: '立即采集', exact: true }).click()
      await p.locator('.enroll-dialog .intro-consent').waitFor()
      await p.waitForTimeout(100)
      assert(await p.evaluate(() => window.__promptBodyObservers.size) === 0, 'Tracking must stop while capture dialog is open')
      assert(errors.length === 0, errors.join('\n'))
      checks.push('Moving prompt stays interactive; opening capture disconnects the message observer')
    } finally { await context.close() }
  }

  {
    const { context, p, errors } = await setup({ width: 1366, height: 768 })
    try {
      await assertStack(p)
      await p.locator('.basic-layout__collapse').click()
      await p.waitForTimeout(300)
      await assertStack(p)
      await p.locator('.basic-layout__collapse').click()
      await p.waitForTimeout(300)
      await assertStack(p)
      await p.locator('.el-message.is-center').hover()
      await p.waitForTimeout(500)
      await assertStack(p)
      await p.locator('.el-message.is-center').waitFor({ state: 'detached' })
      await p.waitForTimeout(350)
      assert(Math.abs(await position(p) - 16) < 1, 'Manual dismissal must also move prompt')
      checks.push('1366x768: both notices remain on the viewport center with sidebar expanded or collapsed; hovering never extends the two-second deadline')
      await p.getByRole('button', { name: '稍后', exact: true }).click()
      await p.locator('.face-prompt').waitFor({ state: 'hidden' })
      assert(await p.evaluate(() => window.__promptBodyObservers.size) === 0, 'Dismissing prompt must disconnect tracking')
      await p.locator('.basic-layout__user').click()
      await p.getByRole('menuitem', { name: '退出登录', exact: true }).click()
      await p.waitForURL('**/login')
      assert(await p.evaluate(() => window.__promptBodyObservers.size) === 0, 'Unmount must not retain tracking')
      assert(errors.length === 0, errors.join('\n'))
      checks.push('Dismiss and logout release tracking without observers left behind')
    } finally { await context.close() }
  }

  {
    const { context, p } = await setup({ width: 1366, height: 768 }, 'reduce')
    try {
      await assertStack(p)
      const duration = await p.locator('.face-prompt-position').evaluate(el => getComputedStyle(el).transitionDuration)
      assert(duration === '0s', 'Reduced-motion preference disables movement')
      await p.keyboard.press('Escape')
      await p.locator('.el-message.is-center').waitFor({ state: 'detached' })
      await p.waitForFunction(() => Math.abs(document.querySelector('.face-prompt').getBoundingClientRect().top - 16) < 1)
      checks.push('Reduced motion disables transitions while preserving final stacking position')
    } finally { await context.close() }
  }

  {
    const { context, p } = await setup({ width: 1920, height: 920 }, 'no-preference', true)
    try {
      assert(await p.locator('.el-message').count() === 0 && Math.abs(await position(p) - 16) < 1, 'Without welcome, face prompt must start in the first position')
      const brand = p.locator('.basic-layout__brand-icon')
      assert(await brand.evaluate(el => { const image = el.querySelector('img'); return image.complete && image.naturalWidth > 0 && el.getBoundingClientRect().width === 40 }), 'Sidebar image loads in a forty-pixel icon slot')
      assert(await brand.locator('img').evaluate(el => new URL(el.currentSrc).pathname.endsWith('.png')), 'Sidebar uses the adapted user-provided MES logo')
      await p.locator('.basic-layout__collapse').click()
      await p.waitForTimeout(300)
      assert(await brand.isVisible(), 'Collapsed sidebar retains brand icon')
      const alignment = await p.locator('.face-prompt').evaluate(el => Math.abs(el.getBoundingClientRect().x + el.getBoundingClientRect().width / 2 - innerWidth / 2))
      assert(alignment < 1, 'Notice remains centered on the viewport when sidebar collapses')
      await p.locator('.basic-layout__collapse').click()
      await p.waitForTimeout(300)
      await p.screenshot({ path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/brand-sidebar-and-notice.png', animations: 'disabled' })
      checks.push('Reloaded session without welcome places face prompt at 16px immediately')
      checks.push('User-provided MES logo loads and remains visible when collapsed; notices stay on the viewport center')
    } finally { await context.close() }
  }
  return { checks, traces, mode: 'Real login and Element Plus welcome; isolated account APIs, no biometric writes' }
}
