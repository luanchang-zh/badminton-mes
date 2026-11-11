// Review-only interactions. The production login continues to use the Vue store.
const form = document.querySelector('#login-form')
const userNo = document.querySelector('#user-no')
const password = document.querySelector('#password')
const passwordToggle = document.querySelector('#password-toggle')
const submit = document.querySelector('#login-submit')
const submitLabel = document.querySelector('#submit-label')
const feedback = document.querySelector('#preview-feedback')
const helpToggle = document.querySelector('#help-toggle')
const help = document.querySelector('#account-help')
const capsHint = document.querySelector('#caps-hint')
let feedbackTimer
let loginTimer

if (new URLSearchParams(location.search).has('presentation')) {
  document.body.dataset.presentation = ''
}

function notify(message) {
  clearTimeout(feedbackTimer)
  feedback.textContent = message
  feedback.hidden = false
  feedbackTimer = setTimeout(() => { feedback.hidden = true }, 4500)
}

function validate(input, message) {
  const invalid = !input.value.trim()
  const shell = input.closest('.input-shell')
  const error = document.getElementById(input.getAttribute('aria-describedby').split(' ')[0])
  input.setAttribute('aria-invalid', String(invalid))
  shell.classList.toggle('is-invalid', invalid)
  error.textContent = invalid ? message : ''
  return !invalid
}

userNo.addEventListener('input', () => validate(userNo, '请输入工号'))
password.addEventListener('input', () => validate(password, '请输入密码'))
password.addEventListener('keyup', (event) => {
  capsHint.hidden = !event.getModifierState('CapsLock')
  password.setAttribute('aria-describedby', capsHint.hidden ? 'password-error' : 'password-error caps-hint')
})
password.addEventListener('blur', () => {
  capsHint.hidden = true
  password.setAttribute('aria-describedby', 'password-error')
})

passwordToggle.addEventListener('click', () => {
  const showing = password.type === 'password'
  password.type = showing ? 'text' : 'password'
  passwordToggle.textContent = showing ? '隐藏' : '显示'
  passwordToggle.setAttribute('aria-label', showing ? '隐藏密码' : '显示密码')
  passwordToggle.setAttribute('aria-pressed', String(showing))
})

helpToggle.addEventListener('click', () => {
  help.hidden = !help.hidden
  helpToggle.setAttribute('aria-expanded', String(!help.hidden))
})

document.querySelector('#fill-demo').addEventListener('click', () => {
  if (submit.disabled) return
  userNo.value = 'demo'
  password.value = 'demo'
  validate(userNo, '请输入工号')
  validate(password, '请输入密码')
  notify('已填入演示账号，可体验登录按钮与表单反馈。')
  submit.focus()
})

form.addEventListener('submit', (event) => {
  event.preventDefault()
  if (submit.disabled) return
  const validUser = validate(userNo, '请输入工号')
  const validPassword = validate(password, '请输入密码')
  if (!validUser || !validPassword) {
    ;(validUser ? password : userNo).focus()
    return
  }
  submit.disabled = true
  submit.setAttribute('aria-busy', 'true')
  submitLabel.textContent = '正在登录…'
  loginTimer = setTimeout(() => {
    submit.disabled = false
    submit.removeAttribute('aria-busy')
    submitLabel.textContent = '登录工作台'
    notify('设计稿交互预览完成。实际登录请使用底部的「查看当前登录页」。')
  }, 900)
})

// pagehide covers close/navigation and BFCache; restore the disabled state before
// caching so returning to the preview always leaves a usable form.
window.addEventListener('pagehide', () => {
  clearTimeout(feedbackTimer)
  clearTimeout(loginTimer)
  feedback.hidden = true
  submit.disabled = false
  submit.removeAttribute('aria-busy')
  submitLabel.textContent = '登录工作台'
})
