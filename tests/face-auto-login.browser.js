async (page) => {
  const context = await page.context().browser().newContext({ viewport: { width: 1504, height: 1046 } })
  const p = await context.newPage()
  const assert = (value, message) => { if (!value) throw new Error(message) }
  const checks = []
  let mode = 'success', requests = 0, pendingRoute
  const user = { userId: 902, userNo: 'face-auto-test', userName: '自动验证验收', roleCodes: ['WORKER'], token: 'auto-verified-session' }
  const success = data => ({ code: '00000', message: 'ok', userTip: null, data })
  await p.addInitScript(() => {
    localStorage.clear()
    window.__tracks = []
    window.__encodedSizes = []
    window.__stallEncode = false
    const play = HTMLMediaElement.prototype.play
    HTMLMediaElement.prototype.play = async function () { await play.call(this); window.__cameraReadyAt = performance.now() }
    const encode = HTMLCanvasElement.prototype.toBlob
    HTMLCanvasElement.prototype.toBlob = function (callback, ...args) {
      window.__encodedSizes.push({ width: this.width, height: this.height })
      if (window.__stallEncode) { window.__releaseEncode = () => encode.call(this, callback, ...args); return }
      return encode.call(this, callback, ...args)
    }
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => {
      const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480
      const context = canvas.getContext('2d'); context.fillStyle = '#11231b'; context.fillRect(0, 0, 640, 480)
      const stream = canvas.captureStream(10);
      stream.getTracks().forEach(track => { const stop = track.stop.bind(track); track.stop = () => { window.__stoppedAt = performance.now(); stop() } })
      window.__tracks.push(...stream.getTracks()); return stream
    } })
  })
  await p.route('http://127.0.0.1:5173/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/face_login')) {
      requests++
      const body = route.request().postDataBuffer().toString('latin1')
      assert((body.match(/name="image"/g) || []).length === 1, 'One automatically sampled image must reach backend')
      if (mode === 'unavailable') { await route.fulfill({ status: 502, contentType: 'text/plain', body: '' }); return }
      if (mode === 'hold') { pendingRoute = route; return }
      if (mode === 'quality' && requests === 1 || mode === 'bad') {
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 'A0700', message: '照片不合格', userTip: '请将面部放入轮廓', data: null }) }); return
      }
      if (mode === 'reject') {
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 'A0200', message: '未通过', userTip: '人脸识别未通过', data: null }) }); return
      }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(success(user)) }); return
    }
    const data = path.endsWith('/face/profile') ? { enrolled: true, faceLoginAllowed: true, faceCount: 2, samples: [] } : []
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(success(data)) })
  })
  const fresh = async () => {
    await p.goto('http://127.0.0.1:5173/login')
    await p.getByRole('tab', { name: '人脸登录', exact: true }).waitFor()
    requests = 0; pendingRoute = undefined
  }
  const begin = () => p.getByRole('tab', { name: '人脸登录', exact: true }).click()
  const stopped = () => p.evaluate(() => window.__tracks.every(track => track.readyState === 'ended'))
  try {
    await fresh()
    let start = Date.now(); await begin()
    assert(await p.getByRole('button', { name: /拍摄验证照片|验证并登录/ }).count() === 0, 'No manual capture or confirm buttons')
    const contour = await p.locator('.face-capture__head-contour').getAttribute('d')
    assert(contour.includes('25 105') && contour.includes('175 105') && contour.includes('100 222'), 'Contour must include both ears and chin')
    await p.waitForURL('**/dashboard')
    const successMs = Date.now() - start
    assert(successMs < 5000 && requests === 1, 'Automatic success must complete under five seconds')
    const imageSize = await p.evaluate(() => window.__encodedSizes[0])
    assert(imageSize.width < 640 && imageSize.height < 480, 'Only the guide region should be sampled')
    assert(await stopped(), 'Success must close camera')
    checks.push({ scene: 'Automatic login without any capture click', elapsedMs: successMs, imageSize })

    mode = 'quality'; await fresh(); start = Date.now(); await begin(); await p.waitForURL('**/dashboard')
    assert(requests === 2 && Date.now() - start < 5000, 'Rejected quality frame should automatically resample within same deadline')
    checks.push({ scene: 'Automatic resample after bad frame', requests, elapsedMs: Date.now() - start })

    mode = 'bad'; await fresh(); await begin(); await p.locator('.face-capture__error').waitFor(); await p.waitForTimeout(800)
    assert(requests === 3 && await stopped(), 'Bad frames must stop after three samples')
    assert(await p.locator('.el-message--error').count() === 0, 'Intermediate errors should not create global toast spam')
    checks.push({ scene: 'Bad frames bounded at three; camera stopped; no toast spam' })

    mode = 'reject'; await fresh(); await begin(); await p.locator('.face-capture__error').filter({ hasText: '识别未通过' }).waitFor(); await p.waitForTimeout(500)
    assert(requests === 1 && await stopped(), 'Identity rejection must not automatically retry')
    checks.push({ scene: 'Identity rejection stops immediately' })

    mode = 'unavailable'; await fresh(); await p.setViewportSize({ width: 1190, height: 884 }); await begin()
    await p.locator('.face-capture__login-error').filter({ hasText: '无法连接登录服务' }).waitFor()
    const layout = await p.locator('.login-page__auth').evaluate(el => {
      const panel = el.getBoundingClientRect(), button = el.querySelector('.login-page__register').getBoundingClientRect()
      return { overflow: el.scrollHeight - el.clientHeight, buttonBottom: button.bottom, panelBottom: panel.bottom }
    })
    assert(layout.overflow <= 1 && layout.buttonBottom <= layout.panelBottom, '502 error must not overflow panel or hide registration: ' + JSON.stringify(layout))
    await p.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/backend-unavailable.png' })
    assert(requests === 1 && await stopped(), 'Unavailable backend must stop camera and avoid retry loop')
    checks.push({ scene: '502 reports unavailable login service; registration remains visible', layout })
    await p.setViewportSize({ width: 1504, height: 1046 })


    mode = 'hold'; await fresh(); start = Date.now(); await begin(); await p.locator('.face-capture__error').filter({ hasText: '超时' }).waitFor()
    const timeoutMs = Date.now() - start
    assert(timeoutMs < 5000 && await stopped(), 'Hung request must stop inside five-second round')
    await pendingRoute.fulfill({ contentType: 'application/json', body: JSON.stringify(success(user)) }).catch(() => {})
    await p.waitForTimeout(250)
    assert(await p.evaluate(() => localStorage.getItem('mes_token')) === null && new URL(p.url()).pathname === '/login', 'Late success must not establish session')
    checks.push({ scene: 'Slow response timeout and late response ignored', elapsedMs: timeoutMs })

    await fresh(); await p.evaluate(() => { window.__stallEncode = true }); start = Date.now(); await begin()
    await p.locator('.face-capture__error').filter({ hasText: '5秒' }).waitFor()
    const encodeTimeoutMs = await p.evaluate(() => window.__stoppedAt - window.__cameraReadyAt)
    assert(encodeTimeoutMs < 5000 && requests === 0 && await stopped(), 'Entire sample/encode stage deadline: ' + JSON.stringify({ encodeTimeoutMs, requests, stopped: await stopped() }))
    await p.evaluate(() => window.__releaseEncode?.()); await p.waitForTimeout(150)
    assert(requests === 0, 'Late canvas callback must not submit after timeout')
    checks.push({ scene: 'Five-second deadline includes stalled image encoding', elapsedMs: encodeTimeoutMs })

    await fresh(); await begin(); await p.waitForFunction(() => document.querySelector('.face-capture__auto-status')?.textContent.includes('正在验证'))
    await p.getByRole('tab', { name: '密码登录', exact: true }).click(); await p.waitForTimeout(150)
    assert(await stopped(), 'Mode switch must cancel camera and request')
    await pendingRoute?.fulfill({ contentType: 'application/json', body: JSON.stringify(success(user)) }).catch(() => {})
    await p.waitForTimeout(150)
    assert(await p.evaluate(() => localStorage.getItem('mes_token')) === null, 'Cancelled round cannot save late token')
    checks.push({ scene: 'Mode switch aborts in-flight request; no late session' })

    await fresh(); await begin(); await p.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
    await p.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/auto-face-contour.png' })
    await p.getByRole('tab', { name: '密码登录', exact: true }).click()
    return { checks, mode: 'Simulated camera and API; actual browser timers and cancellation' }
  } finally { await context.close() }
}
