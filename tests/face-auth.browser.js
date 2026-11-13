async (page) => {
  const assert = (value, message) => { if (!value) throw new Error(message) }
  const checks = []
  const baseURL = /^https?:/.test(page.url()) ? new URL(page.url()).origin : 'http://127.0.0.1:5173'
  await page.unrouteAll({ behavior: 'wait' })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.addInitScript(() => {
    window.__faceTracks = []
    window.__cameraDenied = false
    const realCreate = URL.createObjectURL.bind(URL)
    const realRevoke = URL.revokeObjectURL.bind(URL)
    window.__faceUrls = new Set()
    URL.createObjectURL = blob => { const url = realCreate(blob); window.__faceUrls.add(url); return url }
    URL.revokeObjectURL = url => { window.__faceUrls.delete(url); realRevoke(url) }
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => {
      if (window.__cameraDenied) throw new DOMException('denied', 'NotAllowedError')
      const canvas = document.createElement('canvas')
      canvas.width = 640; canvas.height = 480
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#d4e8dd'; ctx.fillRect(0, 0, 640, 480)
      ctx.fillStyle = '#57766b'; ctx.font = '30px sans-serif'; ctx.fillText('TEST CAMERA', 210, 240)
      const stream = canvas.captureStream(10)
      window.__faceTracks.push(...stream.getTracks())
      return stream
    } })
  })
  let enrolled = false, reject = true, deniedLogin = true, failStatus = false
  const calls = []
  const user = { userId: 901, userNo: 'face-ui-test', userName: '界面验收', roleCodes: ['WORKER'] }
  await page.route(`${baseURL}/api/**`, async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    calls.push({ path, method: request.method(), body: request.postData() })
    let data = null, code = '00000', userTip = null
    if (path.endsWith('/registration_roles')) data = [{ id: 3, roleCode: 'WORKER', roleName: '生产员工', status: 1, remark: null }]
    else if (path.endsWith('/register')) data = 901
    else if (path.endsWith('/auth/login')) data = { ...user, token: 'test-face-ui-session' }
    else if (path.endsWith('/auth/profile')) data = { ...user, roleNames: ['生产员工'] }
    else if (path.endsWith('/face/profile')) {
      if (failStatus) { code = 'B0001'; userTip = '状态服务暂不可用' }
      data = { enrolled, faceLoginAllowed: true, faceCount: enrolled ? 3 : 0, maxFaces: 5, samples: [] }
    } else if (path.endsWith('/face/enroll')) {
      const body = request.postDataBuffer().toString('latin1')
      assert((body.match(/name="images"/g) || []).length === 3, 'Enrollment must send three image parts')
      assert(body.includes('name="consent"') && body.includes('true'), 'Consent must be sent')
      if (reject) data = { acceptedCount: 0, faceCount: 0, rejectedImages: [{ index: 1, reason: '面部不够清晰' }, { index: 2, reason: '光线不足' }] }
      else { enrolled = true; data = { acceptedCount: 3, faceCount: 3, rejectedImages: [] } }
    } else if (path.endsWith('/auth/face_login')) {
      const body = request.postDataBuffer().toString('latin1')
      assert((body.match(/name="image"/g) || []).length === 1, 'Login must send one image part')
      if (deniedLogin) { code = 'A0200'; userTip = '未识别到已注册的人脸，请重试' }
      else data = { ...user, token: 'test-face-login-session' }
    } else if (path.endsWith('/face/enrollment') && request.method() === 'DELETE') enrolled = false
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, message: userTip || 'ok', userTip, data }) })
  })
  await page.goto(`${baseURL}/login`)
  await page.evaluate(() => localStorage.clear())
  await page.reload()
  await page.evaluate(() => { window.__cameraDenied = true })
  await page.getByRole('tab', { name: '人脸登录', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '摄像头权限未开启' }).waitFor()
  checks.push('Permission refusal is actionable; no login request')
  await page.getByRole('tab', { name: '密码登录', exact: true }).click()
  await page.getByRole('button', { name: '登 录', exact: true }).click()
  await page.getByText('请输入工号', { exact: true }).waitFor()
  await page.getByText('请输入密码', { exact: true }).waitFor()
  checks.push('Password required validation retained')
  await page.getByRole('button', { name: '注册工作账号', exact: true }).click()
  assert(await page.getByRole('tab', { name: '人脸登录', exact: true }).count() === 0, 'Registration must not show face login')
  assert(await page.locator('video').count() === 0, 'Registration must not mount camera')
  await page.locator('#register-user-no').fill(user.userNo)
  await page.locator('#register-user-name').fill(user.userName)
  await page.locator('.register-form .el-select__wrapper').click()
  await page.getByRole('option', { name: '生产员工' }).click()
  await page.locator('#register-password').fill('test-password-123')
  await page.locator('#register-confirm-password').fill('test-password-123')
  await page.getByRole('button', { name: '注 册', exact: true }).click()
  await page.waitForURL('**/dashboard')
  await page.getByRole('button', { name: '立即采集', exact: true }).waitFor()
  checks.push('Registration unchanged; auto login opens dashboard with top prompt')
  await page.waitForTimeout(3200)
  await page.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/dashboard-prompt.png' })
  await page.getByRole('button', { name: '稍后', exact: true }).click()
  await page.locator('.basic-layout__user').click()
  await page.getByRole('menuitem', { name: '人脸登录设置', exact: true }).click()
  const dialog = page.locator('.enroll-dialog')
  await dialog.waitFor()
  await dialog.getByRole('button', { name: '开始采集', exact: true }).waitFor()
  assert(await dialog.getByRole('button', { name: '开始采集', exact: true }).isDisabled(), 'Camera must be gated by consent')
  await page.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/enrollment-consent.png' })
  await dialog.locator('.el-checkbox').click()
  await page.evaluate(() => { window.__cameraDenied = false })
  await dialog.getByRole('button', { name: '开始采集', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
  await dialog.getByRole('button', { name: '拍摄正面', exact: true }).click()
  await dialog.getByRole('button', { name: '拍摄左转头', exact: true }).click()
  await dialog.getByRole('button', { name: '拍摄右转头', exact: true }).click()
  await dialog.getByRole('button', { name: '确认并开启', exact: true }).click()
  await dialog.getByRole('alert').filter({ hasText: '面部不够清晰' }).waitFor()
  assert(await dialog.getByText('人脸登录已开启', { exact: true }).count() === 0, 'Zero accepted must not show success')
  checks.push('Consent gate; three JPEG parts; rejected samples show reasons without success')
  reject = false
  await dialog.getByRole('button', { name: '确认并开启', exact: true }).click()
  await dialog.getByRole('heading', { name: '人脸登录已开启', exact: true }).waitFor()
  assert(await page.evaluate(() => window.__faceTracks.every(t => t.readyState === 'ended') && window.__faceUrls.size === 0), 'Capture streams and URLs must be released on success')
  await page.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/enrollment-success.png' })
  await dialog.getByRole('button', { name: '完成', exact: true }).click()
  assert(await page.getByRole('button', { name: '立即采集', exact: true }).count() === 0, 'Prompt must disappear after enrollment')
  await page.reload()
  await page.locator('.basic-layout__user').waitFor()
  await page.waitForTimeout(500)
  assert(await page.getByRole('button', { name: '立即采集', exact: true }).count() === 0, 'Enrolled status must survive page reload via backend')
  checks.push('Server success opens confirmation; prompt stays hidden after reload; media released')
  await page.locator('.basic-layout__user').click()
  await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
  await page.waitForURL('**/login')
  await page.getByRole('tab', { name: '人脸登录', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
  await page.locator('.face-capture__error').filter({ hasText: '未识别' }).waitFor()
  assert(new URL(page.url()).pathname === '/login', 'Rejected face must stay logged out')
  deniedLogin = false
  await page.getByRole('button', { name: '重新验证', exact: true }).click()
  await page.waitForURL('**/dashboard')
  assert(await page.evaluate(() => localStorage.getItem('mes_token')) === 'test-face-login-session', 'Face token must come from API')
  checks.push('Rejected login stays logged out; verified login uses server token and enters dashboard')
  await page.locator('.basic-layout__user').click()
  await page.getByRole('menuitem', { name: '人脸登录设置', exact: true }).click()
  await dialog.getByRole('button', { name: '关闭人脸登录并删除特征', exact: true }).click()
  await page.getByRole('button', { name: '关闭并删除', exact: true }).click()
  await dialog.locator('.el-checkbox').waitFor()
  assert(!enrolled, 'Disable must call backend delete')
  await dialog.locator('.el-checkbox').click()
  await dialog.getByRole('button', { name: '开始采集', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
  await dialog.getByRole('button', { name: '暂不开启', exact: true }).click()
  await page.waitForTimeout(300)
  assert(await page.evaluate(() => window.__faceTracks.every(t => t.readyState === 'ended')), 'Closing dialog must stop camera')
  checks.push('Delete enrollment works; closing active camera stops tracks')
  failStatus = true
  await page.locator('.basic-layout__user').click()
  await page.getByRole('menuitem', { name: '人脸登录设置', exact: true }).click()
  await dialog.getByRole('button', { name: '重新加载', exact: true }).waitFor()
  failStatus = false
  await dialog.getByRole('button', { name: '重新加载', exact: true }).click()
  await dialog.locator('.el-checkbox').waitFor()
  checks.push('Face status failure can retry')
  await dialog.locator('.el-checkbox').click()
  await dialog.getByRole('button', { name: '开始采集', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  assert(await page.evaluate(() => window.__faceTracks.every(t => t.readyState === 'ended')), 'Backgrounding must release camera')
  await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: false }) })
  await page.setViewportSize({ width: 1366, height: 768 })
  assert(await dialog.evaluate(el => el.getBoundingClientRect().bottom <= innerHeight), 'Dialog must fit short desktop')
  await dialog.getByRole('button', { name: '暂不开启', exact: true }).click()
  await page.locator('.basic-layout__user').click()
  await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
  await page.waitForURL('**/login')
  await page.getByRole('tab', { name: '人脸登录', exact: true }).click()
  assert(await page.locator('.login-page__auth').evaluate(el => el.getBoundingClientRect().bottom <= innerHeight), 'Face login panel must fit short desktop')
  await page.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/face-login-1366.png' })
  await page.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
  await page.getByRole('tab', { name: '密码登录', exact: true }).click()
  assert(await page.evaluate(() => window.__faceTracks.every(t => t.readyState === 'ended') && window.__faceUrls.size === 0), 'Switching login mode must release camera and photos')
  await page.getByRole('tab', { name: '人脸登录', exact: true }).click()
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.screenshot({ animations: 'disabled', path: '/home/liuhan/StudyDir/ProfessionalPractice/badminton-mes/docs/design/login/face/face-login.png' })
  await page.getByRole('tab', { name: '密码登录', exact: true }).click()
  checks.push('Backgrounding and mode switch release cameras; 1366x768 login and dialog fit')

  return { checks, registrationRequests: calls.filter(c => c.path.endsWith('/register')).length, enrollmentRequests: calls.filter(c => c.path.endsWith('/face/enroll')).length, faceLoginRequests: calls.filter(c => c.path.endsWith('/face_login')).length, mode: 'API and camera simulated in isolated Playwright browser' }
}
