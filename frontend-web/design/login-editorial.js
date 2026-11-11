const $ = (selector) => document.querySelector(selector)
const form = $('#auth-form')
const password = $('#password')
let registering = false
function clearErrors() {
  document.querySelectorAll('.error').forEach(el => { el.textContent = '' })
  document.querySelectorAll('[aria-invalid]').forEach(el => el.removeAttribute('aria-invalid'))
  $('#preview-status').textContent = ''
}
function toggleMode() {
  registering = !registering
  clearErrors()
  $('.auth').classList.toggle('is-register', registering)
  document.querySelectorAll('.register-only').forEach(el => {
    el.hidden = !registering
    el.querySelectorAll('input,select').forEach(input => { input.disabled = !registering })
    if (el.matches('.field')) el.querySelector('input').disabled = !registering
  })
  $('#auth-title').textContent = registering ? '注册工作账号' : '登录羽毛球 MES'
  $('#auth-eyebrow').textContent = registering ? 'JOIN YOUR TEAM' : 'WELCOME BACK'
  $('#auth-description').textContent = registering ? '填写基本信息，开启有序协作的每一天。' : '连接制造现场，让每一步生产井然有序。'
  $('#submit-label').textContent = registering ? '创建工作账号' : '立即登录'
  $('#switch-mode').textContent = registering ? '返回登录' : '注册工作账号'
  $('#separator-label').textContent = registering ? '已经有账号？' : '还没有账号？'
  $('#login-options').hidden = registering
  $('label[for="password"]').textContent = registering ? '设置密码' : '登录密码'
  password.autocomplete = registering ? 'new-password' : 'current-password'
  password.placeholder = registering ? '设置 6–32 位密码' : '请输入密码'
  password.value = ''
  password.type = 'password'
  $('#toggle-password').setAttribute('aria-label', '显示密码')
  $('#toggle-password').setAttribute('aria-pressed', 'false')
  $('#confirm-password').value = ''
}
$('#switch-mode').addEventListener('click', toggleMode)
$('#toggle-password').addEventListener('click', () => {
  const showing = password.type === 'password'
  password.type = showing ? 'text' : 'password'
  $('#toggle-password').setAttribute('aria-label', showing ? '隐藏密码' : '显示密码')
  $('#toggle-password').setAttribute('aria-pressed', String(showing))
})
for (const id of ['top-help', 'forgot-password']) {
  $('#' + id).addEventListener('click', () => $('#help-dialog').showModal())
}
form.addEventListener('submit', (event) => {
  event.preventDefault()
  clearErrors()
  let firstInvalid
  const error = (id, message, field) => {
    $('#' + id).textContent = message
    $(field).setAttribute('aria-invalid', 'true')
    firstInvalid ||= $(field)
  }
  if (!$('#user-no').value.trim()) error('user-error', '请输入您的工号', '#user-no')
  if (!password.value) error('password-error', '请输入密码', '#password')
  if (registering) {
    if (!$('#register-name').value.trim()) error('name-error', '请输入姓名', '#register-name')
    if (!$('#register-role').value) error('role-error', '请选择职位', '#register-role')
    if (password.value && (password.value.length < 6 || password.value.length > 32)) error('password-error', '密码需为 6–32 位', '#password')
    if (!$('#confirm-password').value || $('#confirm-password').value !== password.value) error('confirm-error', '请确认两次密码一致', '#confirm-password')
  }
  if (firstInvalid) return firstInvalid.focus()
  $('#preview-status').textContent = registering ? '注册表单已通过校验，此处为设计预览。' : '登录表单已通过校验，此处为设计预览。'
})
form.addEventListener('input', clearErrors)
form.addEventListener('change', clearErrors)
