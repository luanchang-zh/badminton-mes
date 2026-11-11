async page => {
  const context = await page.context().browser().newContext()
  const reference = await context.newPage(), preview = await context.newPage()
  const assert = (condition, message) => { if (!condition) throw Error(message) }
  const results = []
  const selectors = ['.login-page__canvas', '.login-intro h1', '.login-intro__features', '.feature-card', '.login-intro__product', '.login-page__auth']
  const geometry = p => p.evaluate(selectors => Object.fromEntries(selectors.map(selector => {
    const el = document.querySelector(selector), bounds = el.getBoundingClientRect(), css = getComputedStyle(el)
    return [selector, { x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height, font: parseFloat(css.fontSize) }]
  })), selectors)
  await preview.addInitScript(() => {
    window.__layoutTracks = []
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => {
      const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480
      const ctx = canvas.getContext('2d'); ctx.fillStyle = '#12372b'; ctx.fillRect(0, 0, 640, 480)
      const media = canvas.captureStream(10); window.__layoutTracks.push(...media.getTracks()); return media
    } })
  })
  await preview.route('http://127.0.0.1:5173/api/system/auth/face_login', async route => {
    await route.fulfill({ status: 502, contentType: 'text/plain', body: '' })
  })
  try {
    for (const [width, height] of [[1920, 920], [1440, 900], [1366, 768], [1920, 1080]]) {
      await reference.setViewportSize({ width, height }); await preview.setViewportSize({ width, height })
      await reference.goto('http://127.0.0.1/login'); await preview.goto('http://127.0.0.1:5173/login')
      await reference.locator('.login-page__auth').waitFor(); await preview.locator('.login-page__panel-brand').waitFor()
      for (const currentPage of [reference, preview]) {
        await currentPage.evaluate(async () => {
          await document.fonts.ready
          await Promise.all(Array.from(document.images, image => image.decode()))
        })
      }
      const original = await geometry(reference), current = await geometry(preview)
      let maxGeometryDelta = 0
      for (const selector of selectors) {
        for (const field of ['x', 'y', 'width', 'height', 'font']) {
          if (selector === '.login-page__auth' && field === 'height') continue
          const delta = Math.abs(original[selector][field] - current[selector][field])
          maxGeometryDelta = Math.max(maxGeometryDelta, delta)
          assert(delta < 0.6, `${width}x${height}: ${selector} ${field} differs by ${delta}`)
        }
      }
      const layout = await preview.evaluate(() => {
        const panel = document.querySelector('.login-page__auth'), register = document.querySelector('.login-page__register'), header = document.querySelector('.login-page__header')
        const p = panel.getBoundingClientRect(), b = register.getBoundingClientRect(), h = header.getBoundingClientRect()
        return { pageOverflow: document.documentElement.scrollWidth > innerWidth || document.documentElement.scrollHeight > innerHeight, panelOverflow: panel.scrollHeight - panel.clientHeight, bottom: p.bottom, registerBottom: b.bottom, headerBottom: h.bottom, panelTop: p.top, hasCapsule: parseFloat(getComputedStyle(header).borderRadius) > 100 }
      })
      assert(!layout.pageOverflow && layout.panelOverflow <= 1 && layout.bottom <= height && layout.registerBottom <= layout.bottom && layout.headerBottom < layout.panelTop && layout.hasCapsule, `Password layout invalid: ${JSON.stringify(layout)}`)
      if (width === 1920 && height === 920) await preview.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/layout-transfer/password-1920.png' })
      await preview.getByRole('tab', { name: '人脸登录', exact: true }).click()
      await preview.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
      if (width === 1920 && height === 920) await preview.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/layout-transfer/face-1920.png' })
      await preview.locator('.face-capture__login-error').waitFor()
      const faceFits = await preview.locator('.login-page__auth').evaluate(el => el.scrollHeight - el.clientHeight <= 1 && el.querySelector('.login-page__register').getBoundingClientRect().bottom <= el.getBoundingClientRect().bottom && el.getBoundingClientRect().bottom <= innerHeight)
      assert(faceFits, `${width}x${height}: face error layout must fit`)
      await preview.getByRole('button', { name: '注册工作账号', exact: true }).click()
      await preview.getByRole('heading', { name: '注册工作账号', exact: true }).waitFor()
      assert(await preview.getByRole('tab', { name: '人脸登录', exact: true }).count() === 0, 'Registration has no face login tab')
      const registerFits = await preview.locator('.login-page__auth').evaluate(el => el.scrollHeight - el.clientHeight <= 1 && el.getBoundingClientRect().bottom <= innerHeight)
      assert(registerFits, `${width}x${height}: registration must fit`)
      assert(await preview.evaluate(() => window.__layoutTracks.every(t => t.readyState === 'ended')), 'Leaving face login must stop camera')
      results.push({ viewport: [width, height], maxGeometryDeltaPx: maxGeometryDelta, passwordFaceAndRegistrationFit: true })
    }
    return { results, comparison: 'Read-only Docker page on port 80 versus development page on 5173; auth height grows for login tabs' }
  } finally { await context.close() }
}
