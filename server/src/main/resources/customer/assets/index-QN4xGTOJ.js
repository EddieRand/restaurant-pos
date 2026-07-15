(function(){const n=document.createElement("link").relList;if(n&&n.supports&&n.supports("modulepreload"))return;for(const t of document.querySelectorAll('link[rel="modulepreload"]'))i(t);new MutationObserver(t=>{for(const a of t)if(a.type==="childList")for(const d of a.addedNodes)d.tagName==="LINK"&&d.rel==="modulepreload"&&i(d)}).observe(document,{childList:!0,subtree:!0});function r(t){const a={};return t.integrity&&(a.integrity=t.integrity),t.referrerPolicy&&(a.referrerPolicy=t.referrerPolicy),t.crossOrigin==="use-credentials"?a.credentials="include":t.crossOrigin==="anonymous"?a.credentials="omit":a.credentials="same-origin",a}function i(t){if(t.ep)return;t.ep=!0;const a=r(t);fetch(t.href,a)}})();const O=document.querySelector("#app"),T=new URLSearchParams(location.search),S=T.get("code")||location.pathname.split("/").filter(Boolean).pop()||"demo-table-1";window.addEventListener("error",e=>{b("页面加载失败",e.message||"请刷新页面或联系餐厅工作人员。")});window.addEventListener("unhandledrejection",e=>{const n=e.reason;b("页面加载失败",n instanceof Error?n.message:"请刷新页面或联系餐厅工作人员。")});let s,o,c=null,l=new Map,y="all",m=!1,p="连接中";function u(e,n){return fetch(e,{...n,headers:{"Content-Type":"application/json",...(n==null?void 0:n.headers)??{}}}).then(async r=>{if(!r.ok){const i=await r.text();throw new Error(i||`HTTP ${r.status}`)}return r.json()})}function I(e){try{const n=JSON.parse(e.names);return n.zh||n.en||Object.values(n)[0]||e.id}catch{return e.names||e.id}}function h(e){const n=(o==null?void 0:o.currencyMinorDigits)??2;return`${(o==null?void 0:o.currencySymbol)??"$"}${(e/Math.pow(10,n)).toFixed(n)}`}function L(e){return{DINE_IN:"堂食",TAKEAWAY:"自取",DELIVERY:"外卖"}[e]??e}function $(){return s.scope==="TABLE"?"DINE_IN":s.scope==="PICKUP"?"TAKEAWAY":s.scope==="DELIVERY"?"DELIVERY":s.orderTypes[0]??"DINE_IN"}function A(){return Array.from(l.entries()).reduce((e,[n,r])=>{const i=o.items.find(t=>t.id===n);return e+((i==null?void 0:i.priceMinorUnit)??0)*r},0)}async function w(){c||s.menuOnly||(c=await u("/public/sessions",{method:"POST",body:JSON.stringify({code:S,orderType:$(),customerInfo:q()})}),l=new Map(c.cart.items.map(e=>[e.menuItemId,e.quantity])))}async function N(){c&&(c=await u(`/public/sessions/${c.id}/cart`,{method:"POST",body:JSON.stringify({items:Array.from(l.entries()).map(([e,n])=>({menuItemId:e,quantity:n}))})}))}function q(){var t,a,d,g;const e=((t=document.querySelector("#customer-name"))==null?void 0:t.value.trim())||null,n=((a=document.querySelector("#customer-phone"))==null?void 0:a.value.trim())||null,r=((d=document.querySelector("#customer-address"))==null?void 0:d.value.trim())||null,i=((g=document.querySelector("#customer-notes"))==null?void 0:g.value.trim())||null;return{name:e,phone:n,address:r,notes:i}}async function E(e,n){if(s.menuOnly)return;await w();const r=Math.max(0,(l.get(e)??0)+n);r===0?l.delete(e):l.set(e,r),await N(),f()}async function P(){if(!(m||s.menuOnly||l.size===0)){m=!0,p="提交中",f();try{if(await w(),!c)return;const e=await u(`/public/sessions/${c.id}/submit`,{method:"POST",body:JSON.stringify({})});if(e.requiresPayment){const n=await u("/public/payments/mock-intents",{method:"POST",body:JSON.stringify({sessionId:c.id,orderId:e.orderId})});await u(`/public/payments/mock-intents/${n.id}/confirm`,{method:"POST",body:JSON.stringify({})})}p=e.requiresStaffConfirmation?"已提交，等待餐厅确认":"已提交",l.clear(),c=null}catch(e){p=e instanceof Error?e.message:"提交失败"}finally{m=!1,f()}}}function f(){var i;const e=["all",...Array.from(new Set(o.items.map(t=>t.categoryId)))],n=y==="all"?o.items:o.items.filter(t=>t.categoryId===y),r=Array.from(l.entries()).map(([t,a])=>({item:o.items.find(d=>d.id===t),quantity:a})).filter(t=>t.item);O.innerHTML=`
    <main class="shell">
      <header class="topbar">
        <div class="brand">
          <h1>${s.scope==="TABLE"?`桌台 ${s.tableId??""}`:"QR 点餐"}</h1>
          <span>${s.menuOnly?"只读菜单":`${L($())} · ${R(s.paymentTiming)}`}</span>
        </div>
        <div class="pill"><span class="dot"></span>${p}</div>
      </header>
      <section class="panel menu-panel">
        <nav class="tabs">
          ${e.map(t=>`<button class="tab ${t===y?"active":""}" data-category="${t}">${t==="all"?"全部":v(t)}</button>`).join("")}
        </nav>
        <div class="menu">
          ${n.length===0?M():n.map(t=>C(t)).join("")}
        </div>
      </section>
      <aside class="panel cart">
        <div class="cart-head">
          <h2>${s.menuOnly?"菜单":"购物车"}</h2>
          <span class="muted">${r.reduce((t,a)=>t+a.quantity,0)} 件</span>
        </div>
        ${s.menuOnly?'<div class="empty">此二维码当前仅展示菜单。</div>':D(r)}
      </aside>
    </main>
  `,document.querySelectorAll("[data-category]").forEach(t=>{t.addEventListener("click",()=>{y=t.dataset.category||"all",f()})}),document.querySelectorAll("[data-inc]").forEach(t=>t.addEventListener("click",()=>E(t.dataset.inc,1))),document.querySelectorAll("[data-dec]").forEach(t=>t.addEventListener("click",()=>E(t.dataset.dec,-1))),(i=document.querySelector("#submit-order"))==null||i.addEventListener("click",P)}function M(){return`
    <div class="menu-empty">
      <div>
        <strong>暂无可点菜品</strong>
        <div class="muted">餐厅还没有发布菜单，或当前菜品已全部下架。</div>
      </div>
    </div>
  `}function C(e){const n=l.get(e.id)??0;return`
    <article class="item">
      <h3>${v(I(e))}</h3>
      <div class="muted">${e.allergens?`过敏原: ${v(e.allergens)}`:`Course ${e.course}`}</div>
      <div class="item-bottom">
        <span class="price">${h(e.priceMinorUnit)}</span>
        ${s.menuOnly?"":`
          <div class="qty">
            <button data-dec="${e.id}" ${n===0?"disabled":""}>-</button>
            <span>${n}</span>
            <button data-inc="${e.id}" ${e.isSoldOut?"disabled":""}>+</button>
          </div>
        `}
      </div>
    </article>
  `}function D(e){return`
    <div class="section">
      <div class="field"><label>姓名</label><input id="customer-name" autocomplete="name" /></div>
      <div class="field"><label>电话</label><input id="customer-phone" autocomplete="tel" /></div>
      ${$()==="DELIVERY"?'<div class="field"><label>地址</label><input id="customer-address" autocomplete="street-address" /></div>':""}
      <div class="field"><label>备注</label><textarea id="customer-notes"></textarea></div>
    </div>
    <div class="cart-list">
      ${e.length===0?'<div class="empty">请选择菜品</div>':e.map(({item:r,quantity:i})=>`
        <div class="cart-item">
          <strong>${v(I(r))}</strong>
          <span>${i} × ${h(r.priceMinorUnit)}</span>
        </div>
      `).join("")}
    </div>
    ${s.firePolicy==="STAFF_CONFIRM"?'<div class="section"><div class="notice">提交后需要餐厅确认。</div></div>':""}
    <div class="total"><span>合计</span><span>${h(A())}</span></div>
    <div class="actions">
      <button id="submit-order" class="primary" ${e.length===0||m?"disabled":""}>${m?"提交中":"提交订单"}</button>
    </div>
  `}function R(e){return e==="PAY_BEFORE_SUBMIT"?"先付后下单":e==="PAY_AFTER_SUBMIT"?"下单后支付":e==="MENU_ONLY"?"只读菜单":"用餐后支付"}function v(e){return e.replace(/[&<>"']/g,n=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[n])}function b(e,n){O.innerHTML=`<div class="state"><h1>${e}</h1><p class="muted">${n}</p></div>`}async function x(){try{if(s=await u(`/public/qr/${encodeURIComponent(S)}`),!s.orderTypes.length&&!s.menuOnly){b("暂不可点餐","此二维码未开启可用的订单类型。");return}o=await u("/public/menu"),p=s.menuOnly?"浏览菜单":"可点餐",f()}catch(e){b("二维码不可用",e instanceof Error?e.message:"请联系餐厅工作人员。")}}x();
