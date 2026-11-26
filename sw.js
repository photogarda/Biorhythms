// Ritmi service worker: offline cache + best-effort daily notification.
const CACHE = 'ritmi-v1.0.0';
const FILES = ['./', './index.html', './manifest.webmanifest', './icon.svg', './icon-192.png', './icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE && k !== 'ritmi-state').map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
// Network first for the page (so updates arrive), cache fallback offline.
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith(
    fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req, { ignoreSearch: true }).then(r => r || caches.match('./index.html')))
  );
});

const KEYS = ['p', 'e', 'i', 'u'];
const PER = { std: [23, 28, 33, 38], precise: [23.688437, 28.426125, 33.163812, 37.9015] };
function dn(s) { const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s || ''); return m ? Math.round(Date.UTC(+m[1], m[2] - 1, +m[3]) / 864e5) : null; }
function todayN() { const d = new Date(); return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 864e5); }

async function dailyNotice() {
  const c = await caches.open('ritmi-state');
  const r = await c.match('./__state.json'); if (!r) return;
  const st = await r.json(); const s = st.settings || {};
  if (!s.notify) return;
  const today = String(todayN());
  const last = await c.match('./__notified'); if (last && (await last.text()) === today) return;
  const p = (st.people || []).find(x => x.id === st.activeId) || (st.people || [])[0]; if (!p) return;
  const P = s.algo === 'precise' ? PER.precise : PER.std, off = s.algo === 'alt' ? 0.5 : 0;
  const tt = todayN() - dn(p.birth) + off, tx = st.texts || {};
  const vals = P.map(per => { const v = Math.sin(2 * Math.PI * tt / per), half = per / 2, m = Math.ceil((tt - 0.5) / half - 1e-9); return { pct: (v + 1) * 50, crit: m * half < tt + 0.5 - 1e-9 }; });
  const overall = vals.reduce((a, x) => a + x.pct, 0) / 4;
  const names = tx.r || ['Physical', 'Emotional', 'Intellectual', 'Intuitive'];
  const crit = vals.map((x, j) => x.crit ? names[j] : null).filter(Boolean);
  const body = vals.map((x, j) => `${names[j].slice(0, 4)} ${x.pct.toFixed(0)}%`).join(' · ') + (crit.length ? `\n${tx.crit || 'Critical day'}: ${crit.join(', ')}` : '');
  await self.registration.showNotification(`${p.name}: ${overall.toFixed(0)}%`, { body, icon: 'icon-192.png', badge: 'icon-192.png', tag: 'ritmi-daily' });
  await c.put('./__notified', new Response(today));
}
self.addEventListener('periodicsync', e => { if (e.tag === 'ritmi-daily') e.waitUntil(dailyNotice()); });
self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: 'window' }).then(list => list.length ? list[0].focus() : self.clients.openWindow('./#day')));
});
