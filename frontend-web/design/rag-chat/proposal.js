(() => {
  const $ = id => document.getElementById(id);
  const esc = text => String(text).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const sampleAnswer = '<p>开机前，建议按以下顺序完成设备点检。<button class="cite-inline" data-source>[1]</button></p><ol><li><strong>电源与安全装置：</strong>检查电源、急停按钮和防护装置，确认均处于正常状态。</li><li><strong>传动机构：</strong>检查传动带、链条及紧固件，确认没有松动或异常磨损。</li><li><strong>气路与作业区域：</strong>确认气压正常，清理台面及设备周围的杂物。</li><li><strong>空载试运行：</strong>急停复位后先空载运行，观察有无异响和异常振动。</li></ol><p>发现传动带松动、气压异常或防护装置缺失时，先停止开机并报修，处理完成后再恢复作业。<button class="cite-inline" data-source>[1]</button></p>';
  const threads = [
    { id:1, title:'装配线设备点检', time:'今天 09:42', messages:[{role:'user',text:'装配线开机前需要做哪些检查？'},{role:'assistant',html:sampleAnswer,status:'COMPLETED',source:true}] },
    { id:2, title:'工序报工与质量追溯', time:'昨天 16:20', messages:[{role:'user',text:'报工时需要保留哪些追溯信息？'},{role:'assistant',text:'报工时应保留工单编号、工序、作业人、设备与批次信息，并记录合格数量和不良原因。可以继续询问某一项的填写要求。',status:'COMPLETED',source:false}] },
    { id:3, title:'设备异常停机处理', time:'昨天 10:15', messages:[{role:'user',text:'设备出现异常振动时应如何处理？'},{role:'assistant',text:'先停止设备并隔离现场，记录异常现象后提交报修。恢复运行前应完成检查和试运行。',status:'COMPLETED',source:false}] }
  ];
  let selected = 1, state='answer', timer=null, generating=null, toastTimer=null, sourceTrigger=null;
  let nextId=4;
  let menuThreadId=null, dialogState=null, dialogFocusId=null, lastRailKey='';
  const current = () => threads.find(t=>t.id===selected);
  function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,1800);}
  function setSource(open){$('source-panel').hidden=!open;$('source-backdrop').hidden=!open||innerWidth>1550;$('source-toggle').setAttribute('aria-expanded',String(open));if(open){$('source-panel').setAttribute('role',innerWidth<=1550?'dialog':'complementary');if(innerWidth<=1550){$('source-panel').setAttribute('aria-modal','true');$('source-close').focus();}}else{$('source-panel').removeAttribute('aria-modal');if(sourceTrigger?.isConnected)sourceTrigger.focus();}}
  function rail(){
    const term=$('search').value.trim();
    const key=JSON.stringify([selected,term,threads.map(t=>[t.id,t.title,t.time])]);
    if(key===lastRailKey)return;
    lastRailKey=key;
    $('conversations').innerHTML=threads.filter(t=>t.title.includes(term)).map(t=>`
      <div class="conversation-row ${t.id===selected?'active':''}" data-row="${t.id}">
        <button class="conversation-select" data-thread="${t.id}" ${t.id===selected?'aria-current="true"':''}><strong>${esc(t.title)}</strong><span>${esc(t.time)}</span></button>
        <button class="thread-menu-button" data-thread-menu="${t.id}" aria-label="${esc(t.title)}的会话操作" title="会话操作" aria-haspopup="menu" aria-expanded="false" aria-controls="thread-menu"><svg class="ui-icon" aria-hidden="true"><use href="#icon-more"></use></svg></button>
      </div>`).join('')||'<p class="muted" style="padding:12px;font-size:12px">没有匹配的对话</p>';
  }
  function restoreThreadFocus(id){
    (document.querySelector(`[data-thread-menu="${id}"]`)||$('new-chat')).focus();
  }
  function closeThreadMenu(restore=false){
    const id=menuThreadId;
    $('thread-menu').hidden=true;
    document.querySelectorAll('.thread-menu-button[aria-expanded="true"]').forEach(b=>b.setAttribute('aria-expanded','false'));
    document.querySelectorAll('.conversation-row.menu-open').forEach(row=>row.classList.remove('menu-open'));
    menuThreadId=null;
    if(restore&&id!==null)restoreThreadFocus(id);
  }
  function openThreadMenu(id,trigger,focusFirst=false){
    if(menuThreadId===id&&!$('thread-menu').hidden){closeThreadMenu(true);return;}
    closeThreadMenu();menuThreadId=id;
    trigger.setAttribute('aria-expanded','true');trigger.closest('.conversation-row').classList.add('menu-open');
    const box=trigger.getBoundingClientRect();const menu=$('thread-menu');menu.hidden=false;
    menu.style.left=`${Math.max(8,Math.min(box.right-132,innerWidth-140))}px`;
    menu.style.top=`${Math.max(8,Math.min(box.bottom+4,innerHeight-menu.offsetHeight-8))}px`;
    if(focusFirst)menu.querySelector('button').focus();
  }
  function openThreadDialog(kind,id){
    const thread=threads.find(t=>t.id===id);if(!thread)return;
    closeThreadMenu();dialogState={kind,id};dialogFocusId=id;
    const rename=kind==='rename';const input=$('thread-title-input');
    $('thread-dialog-title').textContent=rename?'重命名对话':'删除对话';
    $('rename-field').hidden=!rename;input.disabled=!rename;input.required=rename;input.value=thread.title;input.setCustomValidity('');
    $('thread-dialog-message').hidden=rename;$('thread-dialog-message').textContent=`确认删除“${thread.title}”及其中的消息？`;
    $('thread-dialog-confirm').textContent=rename?'保存':'删除';$('thread-dialog-confirm').className=rename?'primary':'danger';
    document.querySelector('.more').open=false;$('thread-dialog').showModal();
    if(rename){input.focus();input.select();}else $('thread-dialog-cancel').focus();
  }
  function render(){
    const thread=current();$('chat-title').textContent=thread?.title||'新对话';$('chat-subtitle').textContent=thread?.time||'今天';
    $('service-notice').hidden=state!=='unavailable';$('service-notice').innerHTML='知识库服务暂未连接，已保存的对话仍可查看。<button id="reconnect" type="button">重新连接</button>';
    const disabled=state==='unbound'||state==='unavailable'||state==='emptyKnowledge';$('question').disabled=disabled;$('send').disabled=disabled;$('mode').disabled=disabled;
    $('source-toggle').disabled=!thread?.messages.some(m=>m.source);$('stop').hidden=!generating;$('send').hidden=!!generating;
    $('generation-status').textContent=generating?'正在生成回答…':'';
    if(state==='unbound'){$('messages').innerHTML='<div class="empty disabled-state"><div class="empty-symbol">▱</div><h2>知识库尚未开通</h2><p>请联系管理员开通知识库，开通后即可查询工艺、设备和质量资料。</p></div>';setSource(false);rail();return;}
    if(state==='emptyKnowledge'){$('messages').innerHTML='<div class="empty disabled-state"><div class="empty-symbol">▤</div><h2>知识库还没有文档</h2><p>请联系知识库管理员添加工艺、点检或质量资料，完成处理后即可提问。</p></div>';setSource(false);rail();return;}
    if(!thread?.messages.length){$('messages').innerHTML='<div class="empty"><div class="empty-symbol">▱</div><h2>有什么生产问题需要查询？</h2><p>从已收录的文档中查找答案，并查看相关原文。</p><div class="suggestions"><button data-prompt="装配线开机前需要做哪些检查？">装配线开机前要检查什么？</button><button data-prompt="工序报工需要保留哪些追溯信息？">报工时需要保留哪些信息？</button></div></div>';rail();return;}
    $('messages').innerHTML=thread.messages.map((m,i)=>m.role==='user'?`<div class="message user-message"><div class="bubble">${esc(m.text)}</div><span class="avatar small">王</span></div>`:`<div class="message assistant-message"><span class="avatar">MES</span><div class="assistant-body"><div class="assistant-name">知识库助手 <span>${m.status==='GENERATING'?'正在生成':'依据知识库资料'}</span></div><div class="answer ${m.html?'':'streamed'}">${m.html||esc(m.text||(m.status==='GENERATING'?'正在检索相关资料…':''))}</div>${m.source?'<button class="source-link" data-source><span class="file-mark">▤</span> 装配线设备点检规程.md <span class="source-number">来源 1</span><span aria-hidden="true">›</span></button>':''}${m.status==='FAILED'?'<div class="message-status">本次回答未完成，问题已保存。</div>':m.status==='CANCELLED'?'<div class="message-status">已停止生成，已保留现有内容。</div>':''}<div class="message-actions">${m.status==='COMPLETED'?`<button data-copy="${i}">复制回答</button><span>标准检索</span>`:''}${['FAILED','CANCELLED'].includes(m.status)?`<button data-retry="${i}" class="retry">重新生成</button>`:''}</div></div></div>`).join('');
    rail();
  }
  function stop(){if(!generating)return;clearTimeout(timer);generating.message.status='CANCELLED';generating=null;render();}
  function newChat(){closeThreadMenu();stop();$('search').value='';const t={id:nextId++,title:'新对话',time:'今天',messages:[]};threads.unshift(t);selected=t.id;state='empty';$('scenario').value='empty';$('question').value='';$('counter').textContent='0 / 2000';setSource(false);render();$('question').focus();}
  function generate(question,retry=false){
    if(generating)return;const t=current();if(!retry)t.messages.push({role:'user',text:question});if(t.title==='新对话')t.title=question.slice(0,18);
    const m={role:'assistant',text:'',status:'GENERATING',source:false};t.messages.push(m);generating={message:m};render();
    const answer='根据设备点检规程，先检查电源、急停与防护装置，再确认传动机构、气路及作业区域正常。完成检查后进行空载试运行，观察有无异响或振动。\n\n如发现传动带松动、气压异常或防护装置缺失，应停止开机并报修。处理完成、确认安全后再恢复作业。 [1]';let cursor=0;
    function tick(){if(!generating)return;cursor+=6;m.text=answer.slice(0,cursor);if(cursor>=answer.length){m.status='COMPLETED';m.source=true;generating=null;}render();$('messages').scrollTop=$('messages').scrollHeight;if(generating)timer=setTimeout(tick,55);}
    timer=setTimeout(tick,300);
  }
  function scenario(value){stop();state=value;$('scenario').value=value;if(value==='empty'){newChat();return;}if(!threads.some(t=>t.id===1))threads.unshift({id:1,title:'装配线设备点检',time:'今天',messages:[]});selected=1;if(value==='noMatch'){current().messages=[{role:'user',text:'这款设备的冷却风机参数是多少？'},{role:'assistant',text:'当前知识库中没有找到相关资料。请补充设备型号、工序或关键字后再试。',status:'COMPLETED',source:false}];}else if(value==='failed'){current().messages=[{role:'user',text:'装配线开机前需要做哪些检查？'},{role:'assistant',text:'',status:'FAILED',source:false}];}else{threads[threads.findIndex(t=>t.id===1)].messages=[{role:'user',text:'装配线开机前需要做哪些检查？'},{role:'assistant',html:sampleAnswer,status:'COMPLETED',source:true}];}render();setSource(value==='answer'&&innerWidth>1550);}
  $('new-chat').onclick=newChat;$('search').oninput=()=>{closeThreadMenu();rail();};$('scenario').onchange=e=>{closeThreadMenu();scenario(e.target.value);};
  $('composer').onsubmit=e=>{e.preventDefault();const question=$('question').value.trim();if(!question||generating||$('question').disabled)return;$('question').value='';$('counter').textContent='0 / 2000';generate(question);};
  $('question').oninput=()=>{$('counter').textContent=`${$('question').value.length} / 2000`;};
  $('question').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing){e.preventDefault();$('composer').requestSubmit();}};
  $('stop').onclick=stop;$('source-toggle').onclick=e=>{sourceTrigger=e.currentTarget;setSource($('source-panel').hidden);};$('source-close').onclick=()=>setSource(false);$('source-backdrop').onclick=()=>setSource(false);
  $('collapse-nav').onclick=()=>{document.body.classList.toggle('nav-collapsed');$('collapse-nav').setAttribute('aria-label',document.body.classList.contains('nav-collapsed')?'展开主导航':'折叠主导航');};
  $('rename-chat').onclick=()=>openThreadDialog('rename',selected);
  $('delete-chat').onclick=()=>openThreadDialog('delete',selected);
  $('thread-dialog-cancel').onclick=()=>$('thread-dialog').close();
  $('thread-title-input').oninput=()=>$('thread-title-input').setCustomValidity('');
  $('thread-dialog').addEventListener('close',()=>{restoreThreadFocus(dialogFocusId);dialogState=null;});
  $('thread-dialog-form').onsubmit=e=>{
    e.preventDefault();if(!dialogState)return;
    const {kind,id}=dialogState;const thread=threads.find(t=>t.id===id);if(!thread){$('thread-dialog').close();return;}
    if(kind==='rename'){
      const title=$('thread-title-input').value.trim();
      if(!title){$('thread-title-input').setCustomValidity('请输入对话名称');$('thread-title-input').reportValidity();return;}
      thread.title=title.slice(0,120);$('thread-dialog').close();render();
    }else{
      const isCurrent=id===selected;if(isCurrent)stop();
      threads.splice(threads.findIndex(t=>t.id===id),1);$('thread-dialog').close();
      if(!threads.length)newChat();else{if(isCurrent){selected=threads[0].id;state='answer';$('scenario').value='answer';setSource(false);}render();}
    }
  };
  document.addEventListener('pointerdown',()=>{document.body.dataset.focusMode='pointer';},true);
  document.addEventListener('click',async e=>{
    const target=e.target.closest('button');
    if(target?.dataset.threadMenu){openThreadMenu(Number(target.dataset.threadMenu),target,e.detail===0);return;}
    if(target?.dataset.threadAction){openThreadDialog(target.dataset.threadAction,menuThreadId);return;}
    if(!e.target.closest('#thread-menu'))closeThreadMenu();
    if(!target)return;
    if(target.dataset.thread){stop();selected=Number(target.dataset.thread);state='answer';$('scenario').value='answer';setSource(false);render();}
    if(target.hasAttribute('data-source')){sourceTrigger=target;setSource(true);}
    if(target.dataset.prompt){$('question').value=target.dataset.prompt;$('question').oninput();$('question').focus();}
    if(target.dataset.retry){const i=Number(target.dataset.retry);const question=current().messages.slice(0,i).reverse().find(m=>m.role==='user')?.text||'如何点检';generate(question,true);}
    if(target.dataset.copy){const m=current().messages[Number(target.dataset.copy)];try{await navigator.clipboard.writeText(m.text||target.closest('.assistant-body').querySelector('.answer').innerText);toast('回答已复制');}catch{toast('请选中回答文字复制');}}
    if(target.id==='reconnect')toast('当前为设计预览，服务连接状态由后端提供');
  });
  document.addEventListener('keydown',e=>{
    if(e.key==='Tab')document.body.dataset.focusMode='keyboard';
    if($('thread-dialog').open)return;
    const trigger=e.target.closest('[data-thread-menu]');
    if(trigger&&e.key==='ArrowDown'){e.preventDefault();openThreadMenu(Number(trigger.dataset.threadMenu),trigger,true);return;}
    if(!$('thread-menu').hidden){
      if(e.key==='Escape'){e.preventDefault();closeThreadMenu(true);return;}
      if(e.key==='Tab'){closeThreadMenu(true);return;}
      const items=[...$('thread-menu').querySelectorAll('button')];const index=items.indexOf(document.activeElement);
      if(['ArrowDown','ArrowUp','Home','End'].includes(e.key)){e.preventDefault();const next=e.key==='Home'?0:e.key==='End'?items.length-1:(index+(e.key==='ArrowDown'?1:-1)+items.length)%items.length;items[next].focus();return;}
    }
    if(e.key==='Escape')setSource(false);
    if(e.key==='Tab'&&!$('source-panel').hidden&&innerWidth<=1550){const items=[...$('source-panel').querySelectorAll('button,[tabindex="0"]')];if(items.length&&((e.shiftKey&&document.activeElement===items[0])||(!e.shiftKey&&document.activeElement===items.at(-1)))){e.preventDefault();(e.shiftKey?items.at(-1):items[0]).focus();}}
  });
  $('conversations').addEventListener('scroll',()=>closeThreadMenu());
  document.addEventListener('visibilitychange',()=>{if(document.hidden)stop();});window.addEventListener('pagehide',()=>{clearTimeout(timer);clearTimeout(toastTimer);});window.addEventListener('resize',()=>{closeThreadMenu();if(!$('source-panel').hidden)setSource(true);});
  render();setSource(innerWidth>1550);
})();
