async (page) => {
  const baseURL = /^https?:/.test(page.url()) ? new URL(page.url()).origin : 'http://127.0.0.1:5173'
  const context = await page.context().browser().newContext({ viewport: { width: 1366, height: 768 } })
  const p = await context.newPage()
  const checks = []
  const assert = (value, message) => { if (!value) throw new Error(message) }
  const user = { userId: 913, userNo: 'enrollment-test', userName: '采集验收', roleCodes: ['WORKER'] }
  let enrolled = false, mode = 'partial', uploads = 0, reenrollUploads = 0, lastBody = '', pendingUpload, allowed = true
  await p.addInitScript(user => {
    localStorage.clear()
    localStorage.setItem('mes_token', 'isolated-enrollment-test-session')
    localStorage.setItem('mes_user', JSON.stringify(user))
    window.__tracks = []
    window.__cameraMode = 'ready'
    window.__cameraCalls = 0
    window.__urls = new Set()
    const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL)
    URL.createObjectURL = blob => { const url = create(blob); window.__urls.add(url); return url }
    URL.revokeObjectURL = url => { window.__urls.delete(url); revoke(url) }
    const encode = HTMLCanvasElement.prototype.toBlob
    window.__encodeMode = 'ready'
    HTMLCanvasElement.prototype.toBlob = function (callback, ...args) {
      window.__encodedSize = [this.width, this.height]
      if (window.__encodeMode === 'hold') { window.__releaseEncode = () => encode.call(this, callback, ...args); return }
      return encode.call(this, callback, ...args)
    }
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => {
      window.__cameraCalls++
      if (window.__cameraMode === 'denied') throw new DOMException('denied', 'NotAllowedError')
      const canvas = document.createElement('canvas'); canvas.width = 1280; canvas.height = 960
      const ctx = canvas.getContext('2d'); ctx.fillStyle = '#d8e3ee'; ctx.fillRect(0, 0, 1280, 960)
      const stream = canvas.captureStream(10); window.__tracks.push(...stream.getTracks())
      if (window.__cameraMode === 'hold') return new Promise(resolve => { window.__releaseMedia = () => resolve(stream) })
      return stream
    } })
  }, user)
  await p.route(`${baseURL}/api/**`, async route => {
    const path = new URL(route.request().url()).pathname
    let data = [], code = '00000'
    if (path.endsWith('/auth/profile')) data = { ...user, roleNames: ['生产员工'] }
    if (path.endsWith('/face/profile')) data = { enrolled, faceLoginAllowed: allowed, faceCount: enrolled ? 2 : 0, maxFaces: 5, samples: [] }
    if (path.endsWith('/face/enroll') || path.endsWith('/face/reenroll')) {
      uploads++
      if (path.endsWith('/face/reenroll')) reenrollUploads++
      lastBody = route.request().postDataBuffer().toString('latin1')
      assert((lastBody.match(/name="images"/g) || []).length === 3, 'Exactly three JPEG files')
      assert((lastBody.match(/\xff\xd8\xff/g) || []).length >= 3, 'Real encoded JPEG magic')
      assert(lastBody.includes('name="consent"') && lastBody.includes('true'), 'Explicit consent')
      if (mode === 'hold') { pendingUpload = route; return }
      if (mode === 'duplicate') { enrolled = true; code = 'A0111'; data = null }
      else if (mode === 'failure-preserve' || mode === 'failure-cleared') {
        enrolled = mode === 'failure-preserve'
        await route.fulfill({ status: 502, contentType: 'application/json', body: JSON.stringify({ code: 'C0200', message: '采集服务超时，请重试', userTip: '采集服务超时，请重试', data: null }) })
        return
      }
      else if (mode === 'all-rejected') { enrolled = false; data = { acceptedCount: 0, faceCount: 0, rejectedImages: [{ index: 1, reason: '未检测到人脸' }] } }
      else { enrolled = true; data = { acceptedCount: 2, faceCount: 2, rejectedImages: [{ index: 2, reason: '光线不足' }] } }
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, message: code === '00000' ? 'ok' : '已经注册', userTip: null, data }) })
  })
  const stopped = () => p.evaluate(() => window.__tracks.every(t => t.readyState === 'ended'))
  const clean = () => p.evaluate(() => window.__urls.size === 0)
  const dialog = p.locator('.enroll-dialog')
  const open = async (expectEnrolled = false) => {
    await p.locator('.basic-layout__user').click()
    await p.getByRole('menuitem', { name: '人脸登录设置', exact: true }).click()
    await dialog.getByRole('button', { name: expectEnrolled ? '重新采集人脸' : '开始采集', exact: true }).waitFor()
  }
  const begin = async () => {
    await dialog.locator('.el-checkbox').click()
    await dialog.getByRole('button', { name: '开始采集', exact: true }).click()
  }
  const close = async () => {
    await p.keyboard.press('Escape')
    await dialog.waitFor({ state: 'hidden' })
  }
  const shoot = async (name, waitForEncoding = true) => {
    const button = dialog.getByRole('button', { name: `拍摄${name}`, exact: true })
    await button.waitFor()
    await p.waitForFunction(() => document.querySelector('.enroll-dialog video')?.videoWidth > 0)
    await button.click()
    if (waitForEncoding) await p.waitForFunction(() => !document.querySelector('.enroll-dialog .capture-controls .primary-action.is-loading'))
  }
  const finishPhotos = async (replacing = false) => { await shoot('正面'); await shoot('左转头'); await shoot('右转头'); await dialog.getByRole('button', { name: replacing ? '确认并更新' : '确认并开启', exact: true }).waitFor() }
  try {
    await p.goto(`${baseURL}/dashboard`)
    await open()
    assert(await p.evaluate(() => window.__cameraCalls) === 0, 'No camera before consent')
    assert(await dialog.getByRole('button', { name: '开始采集', exact: true }).isDisabled(), 'Consent gate')
    checks.push('No camera access before explicit consent')

    await p.evaluate(() => { window.__cameraMode = 'hold' }); await begin()
    await p.waitForFunction(() => typeof window.__releaseMedia === 'function')
    await close(); await p.evaluate(() => window.__releaseMedia())
    await p.waitForTimeout(80)
    assert(await stopped() && await clean(), 'Late media permission must stop returned tracks')
    checks.push('Closing a pending permission request releases its late stream')

    await p.evaluate(() => { window.__cameraMode = 'denied' }); await open(); await begin()
    await dialog.getByRole('alert').filter({ hasText: '摄像头权限未开启' }).waitFor()
    await p.evaluate(() => { window.__cameraMode = 'ready' })
    await dialog.getByRole('button', { name: '重新开启摄像头', exact: true }).click()
    await shoot('正面')
    assert(await p.evaluate(() => window.__urls.size) === 1, 'One sample in memory')
    await p.evaluate(() => { window.__encodeMode = 'hold' })
    await shoot('左转头', false); await p.waitForFunction(() => typeof window.__releaseEncode === 'function')
    await close(); await p.evaluate(() => window.__releaseEncode()); await p.waitForTimeout(80)
    assert(await stopped() && await clean() && uploads === 0, 'Late encoder must not retain photos or submit')
    checks.push('Permission retry works; closing during JPEG encoding cancels and revokes photos')

    await open(); await begin()
    await shoot('正面', false)
    await dialog.getByRole('alert').filter({ hasText: '照片采集失败' }).waitFor()
    await p.evaluate(() => window.__releaseEncode()); await p.waitForTimeout(80)
    assert(await stopped() && await clean(), 'Timed out encoding releases camera and ignores late callback')
    await close()
    checks.push('JPEG encoding timeout is recoverable and ignores a late callback')

    await p.evaluate(() => { window.__encodeMode = 'ready' }); await open(); await begin()
    await p.waitForFunction(() => document.querySelector('.enroll-dialog video')?.videoWidth > 0)
    await p.evaluate(() => window.__tracks.at(-1).dispatchEvent(new Event('ended')))
    await dialog.getByRole('alert').filter({ hasText: '摄像头连接已中断' }).waitFor()
    assert(await stopped(), 'Device disconnect stops stream')
    await dialog.getByRole('button', { name: '重新开启摄像头', exact: true }).click()
    checks.push('Camera disconnect offers a working retry')
    await finishPhotos()
    assert(await stopped(), 'Review releases camera')
    const before = await p.locator('.captured-photo img').evaluateAll(images => images.map(i => i.src))
    await dialog.getByRole('button', { name: '重拍左转头', exact: true }).click(); await shoot('左转头')
    const after = await p.locator('.captured-photo img').evaluateAll(images => images.map(i => i.src))
    assert(before[0] === after[0] && before[2] === after[2] && before[1] !== after[1], 'Retake only replaces selected photo')
    assert(await p.evaluate(() => window.__urls.size) === 3, 'Replaced URL revoked')
    const size = await p.evaluate(() => window.__encodedSize)
    assert(size[0] < size[1] && Math.max(...size) <= 960, 'Crop matches portrait viewport; max 960px')
    checks.push('Review stops camera; per-photo retake preserves others and revokes old URL')

    mode = 'hold'; await dialog.getByRole('button', { name: '确认并开启', exact: true }).click()
    await p.waitForFunction(() => document.querySelector('.enroll-dialog .review-confirm')?.disabled)
    await p.keyboard.press('Escape'); assert(await dialog.isVisible(), 'Cannot close in-flight submission')
    assert(await dialog.locator('.el-dialog__headerbtn').count() === 0, 'No close button during upload')
    enrolled = true
    await pendingUpload.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: '00000', message: 'ok', userTip: null, data: { acceptedCount: 2, faceCount: 2, rejectedImages: [{ index: 2, reason: '光线不足' }] } }) })
    await dialog.getByRole('heading', { name: '人脸登录已开启', exact: true }).waitFor()
    await dialog.getByText('已录入 2 张有效照片', { exact: true }).waitFor()
    await dialog.getByText('部分照片未录入：左转头照片：光线不足', { exact: true }).waitFor()
    assert(await stopped() && await clean(), 'Success clears all media and photo URLs')
    checks.push('Multipart has three real JPEGs; submission locked; partial success shows actual count and reasons')
    await dialog.getByRole('button', { name: '完成', exact: true }).click()

    await open(true)
    await p.screenshot({ path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/enrollment-recapture-settings.png', animations: 'disabled' })
    const cameraCalls = await p.evaluate(() => window.__cameraCalls)
    const uploadsBeforeRecapture = uploads
    await dialog.getByRole('button', { name: '重新采集人脸', exact: true }).click()
    await dialog.getByRole('button', { name: '开始采集', exact: true }).waitFor()
    assert(await dialog.getByRole('button', { name: '开始采集', exact: true }).isDisabled(), 'Recollection requires fresh consent')
    assert(await p.evaluate(() => window.__cameraCalls) === cameraCalls && uploads === uploadsBeforeRecapture && enrolled, 'Entering recollection keeps existing samples and waits for consent')
    await p.screenshot({ path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/enrollment-recapture-intro.png', animations: 'disabled' })
    await begin(); await shoot('正面')
    await dialog.getByRole('button', { name: '取消重新采集', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    assert(enrolled && uploads === uploadsBeforeRecapture && await stopped() && await clean(), 'Cancel preserves server samples and releases new media')
    checks.push('Enrolled users enter recollection from settings; fresh consent and cancel preserve old samples')

    await open(true)
    await dialog.getByRole('button', { name: '重新采集人脸', exact: true }).click()
    await begin(); await finishPhotos(true)
    const recaptureUrls = await p.locator('.captured-photo img').evaluateAll(images => images.map(i => i.src))
    for (const failure of ['failure-preserve', 'failure-cleared']) {
      mode = failure
      await dialog.getByRole('button', { name: '确认并更新', exact: true }).click()
      await dialog.getByRole('alert').filter({ hasText: '采集服务超时，请重试' }).waitFor()
      assert(await dialog.locator('.review-confirm').isEnabled(), 'Failed recollection remains retryable')
      assert(await dialog.getByRole('heading', { name: '人脸登录已开启', exact: true }).count() === 0, 'An old enrollment must not turn failure into success')
      const retainedUrls = await p.locator('.captured-photo img').evaluateAll(images => images.map(i => i.src))
      assert(JSON.stringify(retainedUrls) === JSON.stringify(recaptureUrls), 'Status refresh must preserve captured photos')
    }
    checks.push('Recollection failures refresh server status, keep photos for retry, and never report old samples as success')

    mode = 'all-rejected'
    await dialog.getByRole('button', { name: '确认并更新', exact: true }).click()
    await dialog.getByRole('alert').filter({ hasText: '正面照片：未检测到人脸' }).waitFor()
    assert(!enrolled && await p.evaluate(() => window.__urls.size) === 3, 'All rejected keeps new photos and returns to unenrolled state')
    checks.push('All-rejected recollection shows reasons and keeps the capture review for retry')

    mode = 'hold'
    await dialog.getByRole('button', { name: '确认并更新', exact: true }).click()
    await p.waitForFunction(() => document.querySelector('.enroll-dialog .review-confirm')?.disabled)
    await p.keyboard.press('Escape')
    assert(await dialog.isVisible() && await dialog.locator('.el-dialog__headerbtn').count() === 0, 'Recollection cannot close during upload')
    enrolled = true
    await pendingUpload.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: '00000', message: 'ok', userTip: null, data: { acceptedCount: 2, faceCount: 2, rejectedImages: [{ index: 3, reason: '光线不足' }] } }) })
    await dialog.getByRole('heading', { name: '人脸登录已开启', exact: true }).waitFor()
    await dialog.getByText('部分照片未录入：右转头照片：光线不足', { exact: true }).waitFor()
    assert(reenrollUploads === 4 && await stopped() && await clean(), 'All recollection submissions use reenroll; success cleans resources')
    await dialog.getByRole('button', { name: '完成', exact: true }).click()
    await open(true)
    await dialog.getByText('已录入 2 张有效照片', { exact: true }).waitFor()
    await dialog.getByRole('button', { name: '完成', exact: true }).click()
    checks.push('Recollection uses its endpoint with three JPEGs; upload locked, partial success and reopen show current count')

    enrolled = false; mode = 'duplicate'; await open(); await begin(); await finishPhotos()
    await dialog.getByRole('button', { name: '确认并开启', exact: true }).click()
    await dialog.getByRole('heading', { name: '人脸登录已开启', exact: true }).waitFor()
    checks.push('Duplicate registration refreshes server status and reconciles successful enrollment')
    await dialog.getByRole('button', { name: '完成', exact: true }).click()

    enrolled = false; allowed = false
    await p.locator('.basic-layout__user').click(); await p.getByRole('menuitem', { name: '人脸登录设置', exact: true }).click()
    await dialog.getByRole('heading', { name: '当前账号使用密码登录', exact: true }).waitFor()
    assert(await dialog.locator('video').count() === 0 && await dialog.locator('.el-checkbox').count() === 0, 'Disallowed user cannot open enrollment')
    checks.push('Server permission is respected before camera access')
    return { checks, uploads, reenrollUploads, mode: 'Real application, simulated API and camera; real JPEG encoding and lifecycle' }
  } finally { await context.close() }
}
