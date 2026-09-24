// Fox Drop for Windows: the phone app's seven tabs in an installable web app.
// Same feeds as Api.kt, same Firestore project and rules as Crew.kt, so chat and tips are shared with the phones.
// Epic's own content page (in-game notices, tournaments) blocks browsers, so the News tab has only the news posts.
import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import { getAuth, signInAnonymously, onAuthStateChanged } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';
import {
  getFirestore, collection, doc, onSnapshot, query, orderBy, limit, addDoc, setDoc, updateDoc, deleteDoc,
  serverTimestamp, writeBatch, Timestamp,
} from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-firestore.js';

// Public identifiers, not secrets: firestore.rules does the guarding.
const fb = initializeApp({
  apiKey: 'AIzaSyAe4FIR-TSxN5RKejUzpwogc-hr9NVrMHQ',
  authDomain: 'fox-drop.firebaseapp.com',
  projectId: 'fox-drop',
  storageBucket: 'fox-drop.firebasestorage.app',
  messagingSenderId: '737795172947',
  appId: '1:737795172947:web:47124679816c207fd834a6',
});
const auth = getAuth(fb);
const db = getFirestore(fb);

const EVENTS_URL = 'https://lateraldamage.github.io/fox-drop/events.json';
const CREW = 'crew-';
const LIVE_MS = 30 * 60 * 1000;

// ---------- small helpers ----------

const $ = (sel, root = document) => root.querySelector(sel);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtWhen = (d) => d.toLocaleString(undefined, { weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
const fmtDay = (d) => d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
const fmtClock = (d) => d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
const pretty = (s) => String(s || '').replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
const pad = (n) => String(n).padStart(2, '0');

function countdown(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const days = Math.floor(s / 86400);
  const hms = `${pad(Math.floor((s % 86400) / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
  return days > 0 ? `${days}d ${hms}` : hms;
}

function store(key, fallback) {
  try { const v = localStorage.getItem('foxdrop.' + key); return v == null ? fallback : JSON.parse(v); } catch { return fallback; }
}
function save(key, value) {
  try { localStorage.setItem('foxdrop.' + key, JSON.stringify(value)); } catch { /* private window: keep it in memory only */ }
}

function toast(text) {
  const t = $('#toast');
  t.textContent = text;
  t.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.remove('show'), 2600);
}

const RARITY = {
  common: '#8A8F98', uncommon: '#5FA82B', rare: '#2F8FE0', epic: '#9B3FD8', legendary: '#E38A26', mythic: '#E8C02A',
  marvel: '#C5312C', dc: '#2F5FB8', icon: '#2EB6BD', gaminglegends: '#4B3CC4', starwars: '#333333', dark: '#B02AA8',
  frozen: '#8FC6E8', lava: '#D14A1F', shadow: '#454545', slurp: '#16B5C9',
};
const rarityColor = (r) => RARITY[String(r || '').toLowerCase()] || '#6A5ACD';

// ---------- feeds (ports of Api.kt) ----------

async function getJson(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

async function loadShop() {
  const d = (await getJson('https://fortnite-api.com/v2/shop')).data;
  const entries = (d.entries || []).flatMap((e) => {
    const br = e.brItems;
    if (!br || !br.length) return [];   // skip jam tracks, cars, LEGO kits
    const items = br.map((b) => ({ id: b.id, name: b.name, type: b.type?.displayValue || '', rarity: b.rarity?.value || '' }));
    const icons = br[0].images || {};
    return [{
      offerId: e.offerId,
      title: e.bundle?.name || items[0].name,
      section: e.layout?.name || 'Shop',
      price: e.finalPrice || 0,
      regularPrice: e.regularPrice || 0,
      image: e.newDisplayAsset?.renderImages?.[0]?.image || icons.featured || icons.icon || null,
      outDate: e.outDate ? new Date(e.outDate) : null,
      items,
    }];
  });
  return { hash: d.hash, date: d.date ? new Date(d.date) : null, entries };
}

const cosmetic = (c) => ({
  id: c.id, name: c.name, type: c.type?.displayValue || '', rarity: c.rarity?.value || '',
  image: c.images?.icon || c.images?.smallIcon || null, added: c.added ? new Date(c.added) : null,
});

async function loadNew() {
  const d = (await getJson('https://fortnite-api.com/v2/cosmetics/new')).data;
  const items = (d.items?.br || []).map(cosmetic).sort((a, b) => (b.added || 0) - (a.added || 0));
  const build = String(d.build || '').replace('++Fortnite+Release-', '').split('-CL')[0];
  return { build, items };
}

async function searchCosmetics(name) {
  try {
    const o = await getJson(`https://fortnite-api.com/v2/cosmetics/br/search/all?name=${encodeURIComponent(name.trim())}&matchMethod=contains`);
    return (o.data || []).map(cosmetic).slice(0, 60);
  } catch { return []; }   // 404 means "no match"
}

async function loadNews() {
  const d = (await getJson('https://fortnite-api.com/v2/news/br')).data;
  return (d?.motds || []).filter((m) => !m.hidden).map((m) => ({ id: m.id, title: m.title, body: m.body, image: m.image || m.tileImage }));
}

async function loadStatus() {
  const s = await getJson('https://status.epicgames.com/api/v2/summary.json');
  const comps = s.components || [];
  const fortnite = comps.find((c) => c.name === 'Fortnite' && c.group);
  const fid = fortnite?.id;
  const services = comps.filter((c) => fid && c.group_id === fid).map((c) => ({ name: c.name, status: c.status }));
  const ids = new Set([...comps.filter((c) => c.group_id === fid).map((c) => c.id), fid]);
  const touches = (o) => (o.components || []).some((c) => ids.has(c.id)) || /fortnite/i.test(o.name || '');
  const incidents = (s.incidents || []).filter(touches).map((i) => ({
    id: i.id, name: i.name, status: i.status, latest: i.incident_updates?.[0]?.body || '', link: i.shortlink,
  }));
  const maint = (s.scheduled_maintenances || []).filter(touches).map((m) => ({
    id: m.id, name: m.name, status: m.status, link: m.shortlink,
    start: m.scheduled_for ? new Date(m.scheduled_for) : null, end: m.scheduled_until ? new Date(m.scheduled_until) : null,
  }));
  return { services, incidents, maint, allUp: services.every((x) => x.status === 'operational') };
}

async function loadEventsJson() {
  // GitHub Pages caches for 10 minutes; the query string sidesteps a stale copy.
  const o = await getJson(`${EVENTS_URL}?t=${Math.floor(Date.now() / 60000)}`);
  return Array.isArray(o) ? o : o.events || [];
}

// ---------- events (port of Events.kt) ----------

function parseEvent(o, custom) {
  if (!o || !o.id || !o.title) return null;
  let start; let approx = false;
  if (o.start) start = new Date(o.start);
  else if (o.date) { const [y, m, d] = o.date.split('-').map(Number); start = new Date(y, m - 1, d); approx = true; }
  if (!start || isNaN(start)) return null;
  return { id: o.id, title: o.title, start, approx, note: o.note || '', custom };
}
const isLive = (e, now = Date.now()) => !e.approx && now >= e.start && now < e.start.getTime() + LIVE_MS;
const isOver = (e, now = Date.now()) => now >= (e.approx ? e.start.getTime() + 86400000 : e.start.getTime() + LIVE_MS);
const whenText = (e) => (e.approx ? `${fmtDay(e.start)} · time not announced yet` : fmtWhen(e.start));
const eventJson = (e) => ({ id: e.id, title: e.title, note: e.note, ...(e.approx ? { date: dayString(e.start) } : { start: e.start.toISOString() }) });
const dayString = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

function allEvents() {
  const mine = store('my_events', []).map((o) => parseEvent(o, true));
  const seen = new Set();
  return [...state.hosted, ...state.cloudEvents, ...mine]
    .filter((e) => e && !isOver(e))
    .filter((e) => (seen.has(e.id) ? false : seen.add(e.id)))
    .sort((a, b) => a.start - b.start);
}

// ---------- app state ----------

const state = {
  tab: 'shop',
  shop: { value: null, loading: false, error: null },
  fresh: { value: null, loading: false, error: null },
  news: { value: null, loading: false, error: null },
  status: { value: null, loading: false, error: null },
  hosted: [],
  cloudEvents: [],
  eventsError: null,
  query: '',
  results: null,
  wishes: store('wishes', []),
};
const wishedIds = () => new Set(state.wishes.map((w) => w.id));

function toggleWish(w) {
  const i = state.wishes.findIndex((x) => x.id === w.id);
  if (i >= 0) state.wishes.splice(i, 1); else state.wishes.unshift(w);
  save('wishes', state.wishes);
  render();
}

async function fetchInto(key, loader) {
  state[key].loading = true;
  if (state.tab && key === tabFeed()) render();
  try {
    state[key] = { value: await loader(), loading: false, error: null };
  } catch {
    state[key] = { value: state[key].value, loading: false, error: "Couldn't reach the server. Tap refresh to try again." };
  }
  render();
}

function tabFeed() {
  return { shop: 'shop', wishlist: 'shop', new: 'fresh', news: 'news', servers: 'status' }[state.tab];
}

async function refreshEvents() {
  try { state.hosted = (await loadEventsJson()).map((o) => parseEvent(o, false)).filter(Boolean); state.eventsError = null; }
  catch { state.eventsError = "Couldn't load the event list. Tap refresh to try again."; }
  scheduleReminders();
  render();
}

function refresh(tab = state.tab) {
  if (tab === 'shop' || tab === 'wishlist') fetchInto('shop', loadShop);
  if (tab === 'new') fetchInto('fresh', loadNew);
  if (tab === 'news') fetchInto('news', loadNews);
  if (tab === 'servers') fetchInto('status', loadStatus);
  if (tab === 'events') refreshEvents();
}

function refreshAll() {
  fetchInto('shop', loadShop).then(checkWishlist);
  fetchInto('fresh', loadNew);
  fetchInto('news', loadNews);
  fetchInto('status', loadStatus);
  refreshEvents();
}

// ---------- the crew (port of Crew.kt) ----------

const crew = {
  uid: null, adminDoc: null, memberDoc: null, feedKey: null, feeds: [], commentsUnsub: null,
  me: null, locked: false, notice: null, signInError: null,
  chat: [], tips: [], comments: [], members: [], reports: [], inviteCode: null,
  get active() { const m = this.me; return !!m && (m.admin || (m.member && !m.banned && !this.locked)); },
};
const blocked = () => new Set(store('blocked', []));

function startCrew() {
  onAuthStateChanged(auth, (user) => {
    if (!user) { signInAnonymously(auth).catch(() => { crew.signInError = "Couldn't reach the chat server. Check your internet and try again."; render(); }); return; }
    if (crew.uid === user.uid) return;
    crew.uid = user.uid;
    // Only server-confirmed copies count; see the matching comment in Crew.kt.
    onSnapshot(doc(db, 'admins', user.uid), (s) => { if (!s.metadata.hasPendingWrites) { crew.adminDoc = s; updateMe(); } });
    onSnapshot(doc(db, 'members', user.uid), (s) => { if (!s.metadata.hasPendingWrites) { crew.memberDoc = s; updateMe(); } });
    onSnapshot(collection(db, 'events'), (s) => {
      state.cloudEvents = s.docs.map((d) => {
        const x = d.data();
        const o = { id: CREW + d.id, title: x.title, note: x.note || '' };
        if (x.start) o.start = x.start.toDate().toISOString(); else if (x.date) o.date = x.date;
        return parseEvent(o, false);
      }).filter(Boolean);
      scheduleReminders();
      if (state.tab === 'events' || state.tab === 'shop') render();
    });
  });
}

function updateMe() {
  const a = crew.adminDoc?.exists() ? crew.adminDoc.data() : null;
  const m = crew.memberDoc?.exists() ? crew.memberDoc.data() : null;
  crew.me = {
    uid: crew.uid, admin: !!a, adminName: a?.name, member: !!m, name: m?.name,
    muted: m?.muted === true, banned: m?.banned === true,
  };
  crew.me.displayName = crew.me.admin && !crew.me.member ? (crew.me.adminName || 'Admin') : (crew.me.name || '');
  crew.me.canPost = crew.me.admin || (crew.me.member && !crew.me.banned && !crew.me.muted);
  resubscribe();
  render();
}

function resubscribe() {
  const m = crew.me;
  if (!m) return;
  const key = `${m.admin}|${crew.memberDoc?.exists() ? crew.memberDoc.data().code : null}|${m.banned}`;
  if (key === crew.feedKey) return;
  crew.feedKey = key;
  crew.locked = false;
  crew.feeds.forEach((u) => u());
  crew.feeds = [];
  Object.assign(crew, { chat: [], tips: [], members: [], reports: [], inviteCode: null });
  if (!m.admin && !(m.member && !m.banned)) return;

  let first = true;
  crew.feeds.push(onSnapshot(query(collection(db, 'chat'), orderBy('at', 'desc'), limit(200)), (s) => {
    const before = new Set(crew.chat.map((p) => p.id));
    crew.chat = s.docs.map(postOf).filter(Boolean).reverse();
    if (!first) notifyChat(crew.chat.filter((p) => !before.has(p.id)));
    first = false;
    if (state.tab === 'chat') renderMessages();
  }, (e) => {
    if (e.code === 'permission-denied' && !m.admin) { crew.locked = true; render(); }
    else setTimeout(() => { crew.feedKey = null; resubscribe(); }, 3000);   // a dead listener never recovers on its own
  }));
  crew.feeds.push(onSnapshot(query(collection(db, 'tips'), orderBy('at', 'desc'), limit(50)), (s) => {
    crew.tips = s.docs.map(tipOf).filter((t) => t && t.status === 'pending');
    if (state.tab === 'events') render();
    refreshOpenDialog();
  }));
  if (m.admin) {
    crew.feeds.push(onSnapshot(collection(db, 'members'), (s) => {
      crew.members = s.docs.map((d) => ({ uid: d.id, name: d.data().name || '', muted: d.data().muted === true, banned: d.data().banned === true }))
        .sort((a, b) => a.name.localeCompare(b.name));
      refreshOpenDialog();
    }));
    crew.feeds.push(onSnapshot(collection(db, 'reports'), (s) => {
      crew.reports = s.docs.map((d) => ({ id: d.id, ...d.data() }));
      if (state.tab === 'chat') renderAdminBar();
      refreshOpenDialog();
    }));
    crew.feeds.push(onSnapshot(doc(db, 'config', 'invite'), (s) => { crew.inviteCode = s.data()?.code ?? null; refreshOpenDialog(); }));
  }
}

function postOf(d) {
  const x = d.data({ serverTimestamps: 'estimate' });
  if (!x.uid) return null;
  return { id: d.id, path: d.ref.path, uid: x.uid, name: x.name || '', text: x.text || '', at: x.at?.toDate() || null };
}
function tipOf(d) {
  const x = d.data({ serverTimestamps: 'estimate' });
  if (!x.uid || !x.title) return null;
  return {
    id: d.id, uid: x.uid, name: x.name || '', title: x.title, note: x.note || '',
    start: x.start?.toDate() || null, date: x.date || null, at: x.at?.toDate() || null, status: x.status || 'pending',
  };
}
const tipEvent = (t) => ({
  id: CREW + t.id, title: t.title, note: t.note, custom: false,
  start: t.start || (t.date ? parseEvent({ id: 'x', title: 'x', date: t.date }, false).start : new Date()), approx: !t.start,
});

/** Runs a write; a refusal from the rules becomes `denied`, anything else is blamed on the network. */
async function act(denied, fn) {
  try { await fn(); crew.notice = null; return true; }
  catch (e) {
    crew.notice = e.code === 'permission-denied' ? denied : "That didn't go through. Check your internet and try again.";
    render(); refreshOpenDialog();
    return false;
  }
}
const stamp = (fields) => ({ uid: crew.me.uid, name: crew.me.displayName, at: serverTimestamp(), ...fields });
const whenFields = (e) => (e.approx ? { date: dayString(e.start) } : { start: Timestamp.fromDate(e.start) });

const actions = {
  join: (name, code) => act('That invite code didn\'t work. Check it with the admin.', async () => {
    const ref = doc(db, 'members', crew.uid);
    if (crew.memberDoc?.exists()) await updateDoc(ref, { code: code.trim(), name: name.trim() });
    else await setDoc(ref, { code: code.trim(), name: name.trim(), muted: false, banned: false, joined: serverTimestamp() });
  }),
  claimAdmin: (name, code) => act("That admin code didn't work.", () => setDoc(doc(db, 'admins', crew.uid), { code: code.trim(), name: name.trim() })),
  send: (text) => act("You can't post right now.", () => addDoc(collection(db, 'chat'), stamp({ text: text.trim() }))),
  postTip: (e) => act("You can't post tips right now.", () => addDoc(collection(db, 'tips'), stamp({ title: e.title, note: e.note, status: 'pending', ...whenFields(e) }))),
  comment: (tipId, text) => act("You can't comment right now.", () => addDoc(collection(db, 'tips', tipId, 'comments'), stamp({ text: text.trim() }))),
  report: (p) => act("Couldn't send the report.", () => addDoc(collection(db, 'reports'), { by: crew.uid, path: p.path, uid: p.uid, name: p.name, text: p.text, at: serverTimestamp() })),
  remove: (path) => act('Only the admin can delete that.', () => deleteDoc(doc(db, path))),
  approve: (tip, e) => act('Only the admin can approve tips.', async () => {
    const b = writeBatch(db);
    b.set(doc(db, 'events', tip.id), { title: e.title, note: e.note, at: serverTimestamp(), ...whenFields(e) });
    b.update(doc(db, 'tips', tip.id), { status: 'approved' });
    await b.commit();
  }),
  publish: (e) => act('Only the admin can publish events.', () => addDoc(collection(db, 'events'), { title: e.title, note: e.note, at: serverTimestamp(), ...whenFields(e) })),
  removeOfficial: (id) => act('Only the admin can remove official events.', () => deleteDoc(doc(db, 'events', id.replace(CREW, '')))),
  setInvite: (code) => act('Only the admin can change the invite code.', () => setDoc(doc(db, 'config', 'invite'), { code: code.trim() })),
  setMuted: (uid, on) => act('Only the admin can mute.', () => updateDoc(doc(db, 'members', uid), { muted: on })),
  setBanned: (uid, on) => act('Only the admin can ban.', () => updateDoc(doc(db, 'members', uid), { banned: on })),
  dismissReport: (id) => act('Only the admin can clear reports.', () => deleteDoc(doc(db, 'reports', id))),
};

// ---------- notifications (only while the app is running) ----------

const canNotify = () => 'Notification' in window && Notification.permission === 'granted';

async function notify(title, body, tab, tag) {
  if (!canNotify() || !store('alerts', true)) return;
  const reg = await navigator.serviceWorker?.getRegistration();
  const opts = { body, icon: 'icon-192.png', tag, data: { tab } };
  if (reg) reg.showNotification(title, opts); else new Notification(title, opts);
}

function notifyChat(fresh) {
  const mine = crew.uid; const hidden = blocked();
  const others = fresh.filter((p) => p.uid !== mine && !hidden.has(p.uid));
  if (!others.length || (document.hasFocus() && state.tab === 'chat')) return;
  const title = others.length === 1 ? `💬 ${others[0].name}` : `💬 ${others.length} new messages`;
  notify(title, others.slice(-4).map((p) => (others.length === 1 ? p.text : `${p.name}: ${p.text}`)).join('\n'), 'chat', 'chat');
}

function checkWishlist() {
  const shop = state.shop.value;
  if (!shop) return;
  const day = shop.date ? shop.date.toISOString().slice(0, 10) : '';
  const told = new Set(store('wish_told', []).filter((k) => k.endsWith('@' + day)));
  const wanted = wishedIds();
  for (const e of shop.entries) for (const i of e.items) {
    if (wanted.has(i.id) && !told.has(`${i.id}@${day}`)) {
      notify(`🦊 ${i.name} is in the Item Shop!`, `${e.title} — ${e.price} V-Bucks`, 'shop', 'wish-' + i.id);
      told.add(`${i.id}@${day}`);
    }
  }
  save('wish_told', [...told]);
}

// Reminders a day, an hour and 10 minutes before, and at the start, for as long as the app is open.
let reminderTimers = [];
function scheduleReminders() {
  reminderTimers.forEach(clearTimeout);
  reminderTimers = [];
  const now = Date.now();
  for (const e of allEvents()) {
    const pings = e.approx ? [] : [
      [e.start - 86400000, `⏰ ${e.title} is tomorrow!`, `Starts ${fmtWhen(e.start)}.`],
      [e.start - 3600000, `⏰ ${e.title} starts in 1 hour`, `Starts at ${fmtClock(e.start)}. Update Fortnite now!`],
      [e.start - 600000, `🔥 ${e.title} in 10 minutes!`, 'Jump in now so you don\'t miss it!'],
      [e.start.getTime(), `🔴 ${e.title} is LIVE!`, "It's happening right now. Go go go!"],
    ];
    for (const [at, title, body] of pings) {
      const wait = at - now;
      if (wait > 0 && wait < 2 ** 31 - 1) reminderTimers.push(setTimeout(() => notify(title, body, 'events', e.id + at), wait));
    }
  }
}

// ---------- rendering ----------

const main = $('#main');
const spinner = '<div class="spinner"></div>';
const problem = (t) => (t ? `<div class="problem">${esc(t)}</div>` : '');
const header = (t, sub) => `<h2 class="header">${esc(t)}</h2>${sub ? `<p class="sub">${esc(sub)}</p>` : ''}`;

function tile({ id, title, subtitle, rarity, image, wished, badge, wish }) {
  const c = rarityColor(rarity);
  return `<div class="tile${wished ? ' wished' : ''}">
    <div class="art" style="background:linear-gradient(${c}f2, ${c}59)">
      ${image ? `<img src="${esc(image)}" alt="${esc(title)}" loading="lazy">` : ''}
      <button class="heart" data-wish='${esc(JSON.stringify(wish))}' title="${wished ? 'Remove from wishlist' : 'Add to wishlist'}">${wished ? '❤️' : '🤍'}</button>
      ${badge ? `<span class="badge">${esc(badge)}</span>` : ''}
    </div>
    <div class="name"><b>${esc(title)}</b><span>${esc(subtitle)}</span></div>
  </div>`;
}

function nextEventStrip() {
  const e = allEvents()[0];
  if (!e) return '';
  const label = isLive(e) ? '🔴 Live now' : 'Next live event';
  const right = isLive(e) ? 'GO!' : e.approx ? `~${Math.ceil((e.start - Date.now()) / 86400000)} days` : `<span data-until="${e.start.getTime()}"></span>`;
  return `<div class="card strip row" data-goto="events"><div class="grow"><div class="faint small">${label}</div><b>${esc(e.title)}</b></div><div class="big-num">${right}</div></div>`;
}

function renderShop() {
  const s = state.shop;
  const ids = wishedIds();
  const f = (state.shopFilter || '').toLowerCase();
  const entries = (s.value?.entries || []).filter((e) => !f || e.title.toLowerCase().includes(f) || e.items.some((i) => i.name.toLowerCase().includes(f)));
  const hits = entries.filter((e) => e.items.some((i) => ids.has(i.id)));
  const sections = {};
  for (const e of entries) (sections[e.section] ||= []).push(e);
  const reset = new Date(); reset.setUTCHours(24, 0, 0, 0);
  const shopTile = (e) => {
    const main = e.items[0];
    const price = e.price < e.regularPrice ? `${e.price} V-Bucks (was ${e.regularPrice})` : `${e.price} V-Bucks`;
    const leavesToday = e.outDate && e.outDate - Date.now() > 0 && e.outDate - Date.now() < 86400000;
    return tile({
      title: e.title, subtitle: e.items.length > 1 ? `${price} · ${e.items.length} items` : `${price} · ${main.type}`,
      rarity: main.rarity, image: e.image, wished: e.items.some((i) => ids.has(i.id)), badge: leavesToday ? 'Leaves today' : null,
      wish: { id: main.id, name: main.name, type: main.type, image: e.image },
    });
  };
  return `<div class="wrap stack">
    <div class="card row"><div class="grow"><div class="faint small">Next shop reset</div><div class="big-num" data-until="${reset.getTime()}"></div></div><div class="faint">${fmtClock(reset)}</div></div>
    ${nextEventStrip()}
    <input class="field" id="shop-filter" placeholder="🔍 Search today's shop" value="${esc(state.shopFilter || '')}">
    ${problem(s.error)}
    ${!s.value && s.loading ? spinner : ''}
    ${hits.length ? header("🦊 On your wishlist!") + `<div class="grid">${hits.map(shopTile).join('')}</div>` : ''}
    ${Object.entries(sections).map(([name, list]) => header(name) + `<div class="grid">${list.map(shopTile).join('')}</div>`).join('')}
  </div>`;
}

function renderNew() {
  const f = state.fresh; const ids = wishedIds();
  return `<div class="wrap">
    ${header('New in the game files', f.value ? `Update v${f.value.build} · ${f.value.items.length} new items. Some haven't hit the shop yet.` : null)}
    ${problem(f.error)}${!f.value && f.loading ? spinner : ''}
    <div class="grid">${(f.value?.items || []).map((c) => tile({
      title: c.name, subtitle: c.type, rarity: c.rarity, image: c.image, wished: ids.has(c.id),
      wish: { id: c.id, name: c.name, type: c.type, image: c.image },
    })).join('')}</div>
  </div>`;
}

function renderNews() {
  const n = state.news;
  return `<div class="wrap">
    ${problem(n.error)}${header('📰 News')}${!n.value && n.loading ? spinner : ''}
    <div class="news-grid">${(n.value || []).map((p) => `<div class="card news">
      ${p.image ? `<img src="${esc(p.image)}" alt="" loading="lazy">` : ''}<b style="font-size:18px">${esc(p.title)}</b><p class="faint">${esc(p.body)}</p></div>`).join('')}</div>
    <p class="faint small">In-game notices and the cup list are in the phone app; Epic doesn't let web pages read them.</p>
  </div>`;
}

function renderServers() {
  const s = state.status;
  if (!s.value) return `<div class="wrap">${problem(s.error)}${s.loading ? spinner : ''}</div>`;
  const st = s.value;
  const up = st.allUp && !st.maint.some((m) => m.status === 'in_progress');
  const link = (url, inner) => (url ? `<a class="card" style="display:block;text-decoration:none;color:inherit" href="${esc(url)}" target="_blank" rel="noopener">${inner}</a>` : `<div class="card">${inner}</div>`);
  return `<div class="wrap stack">
    ${problem(s.error)}
    <div class="status-hero ${up ? 'up' : 'down'}"><div style="font-size:40px">${up ? '✅' : '⚠️'}</div>${up ? 'Fortnite is up!' : 'Fortnite is having problems'}</div>
    ${st.maint.length ? header('🛠️ Maintenance') + st.maint.map((m) => link(m.link, `<b>${esc(m.name)}</b><div style="color:var(--sky)">${esc(pretty(m.status))}</div>
      ${m.start ? `<div>Starts ${fmtWhen(m.start)}</div>` : ''}${m.end ? `<div>Back about ${fmtWhen(m.end)}</div>` : ''}`)).join('') : ''}
    ${st.incidents.length ? header('⚠️ Problems right now') + st.incidents.map((i) => link(i.link, `<b>${esc(i.name)}</b><div style="color:var(--sky)">${esc(pretty(i.status))}</div>
      ${i.latest ? `<div class="faint small">${esc(i.latest)}</div>` : ''}`)).join('') : ''}
    ${header('Services')}
    <div class="card" style="padding:4px 0">${st.services.map((v) => {
      const ok = v.status === 'operational';
      return `<div class="svc row"><span class="dot" style="background:${ok ? 'var(--good)' : 'var(--bad)'}"></span><span class="grow">${esc(v.name)}</span>
        <span style="color:${ok ? 'var(--good)' : '#FF8A80'}">${ok ? 'Up' : esc(pretty(v.status))}</span></div>`;
    }).join('')}</div>
  </div>`;
}

function renderWishlist() {
  const ids = wishedIds();
  const inShop = new Set((state.shop.value?.entries || []).flatMap((e) => e.items.map((i) => i.id)));
  const searching = state.query.trim().length >= 2;
  const list = searching ? (state.results || []) : state.wishes;
  return `<div class="wrap">
    ${header('My wishlist', 'Heart any skin, and Fox Drop tells you the day it shows up in the shop.')}
    <input class="field" id="wish-search" placeholder="🔍 Find any skin, emote, pickaxe…" value="${esc(state.query)}">
    <div style="height:12px"></div>
    ${searching && state.results == null ? spinner : ''}
    ${searching && state.results?.length === 0 ? `<p>Nothing called "${esc(state.query)}"</p>` : ''}
    ${!searching && !list.length ? '<p class="faint">No favourites yet. Search above, or tap 🤍 on anything in the Shop or New tabs.</p>' : ''}
    <div class="grid">${list.map((c) => tile({
      title: c.name, subtitle: c.type, rarity: c.rarity || '', image: c.image, wished: ids.has(c.id),
      badge: inShop.has(c.id) ? 'In shop now!' : null, wish: { id: c.id, name: c.name, type: c.type, image: c.image },
    })).join('')}</div>
  </div>`;
}

function renderEvents() {
  const events = allEvents();
  const next = events[0];
  const admin = crew.me?.admin;
  const removable = (e) => e.custom || (admin && e.id.startsWith(CREW));
  let hero;
  if (!next) hero = '<div class="card center" style="padding:24px"><div class="emoji">🦊</div><b>No live event on the calendar yet</b><p class="faint">When Epic announces one, it shows up here with a countdown.</p></div>';
  else {
    const live = isLive(next);
    const clock = live ? 'Go go go!' : next.approx ? (() => { const d = Math.floor((next.start - Date.now()) / 86400000); return d <= 0 ? 'Today!' : `about ${d} day${d === 1 ? '' : 's'}`; })()
      : `<span data-until="${next.start.getTime()}"></span>`;
    hero = `<div class="hero${live ? ' live' : ''}"><div class="kicker">${live ? '🔴 LIVE NOW' : 'NEXT LIVE EVENT'}</div><div class="title">${esc(next.title)}</div>
      <div class="clock">${clock}</div><div>${esc(whenText(next))}</div>${next.note ? `<p class="faint">${esc(next.note)}</p>` : ''}
      ${next.custom ? '<div class="small" style="color:var(--sky)">Added by you</div>' : ''}</div>
      ${removable(next) ? `<button class="btn text" data-remove-event="${esc(next.id)}">Remove "${esc(next.title)}"${next.custom ? '' : ' for everyone'}</button>` : ''}`;
  }
  const rest = events.slice(1).map((e) => {
    const right = e.approx ? `~${Math.ceil((e.start - Date.now()) / 86400000)}d` : `<span data-until="${e.start.getTime()}" data-short="1"></span>`;
    return `<div class="card row"><div class="grow"><b>${esc(e.title)}</b><div class="faint small">${esc(whenText(e))}</div>
      ${e.custom ? '<div class="small" style="color:var(--sky)">Added by you</div>' : ''}</div><div class="big-num" style="font-size:20px">${right}</div>
      ${removable(e) ? `<button class="btn danger" data-remove-event="${esc(e.id)}" title="Remove">🗑</button>` : ''}</div>`;
  }).join('');
  const tips = crew.active ? header('Crew tips', "Events people heard about. Once the admin approves one, it goes on everyone's calendar.")
    + (crew.tips.length ? crew.tips.map((t) => `<div class="card tip" data-tip="${esc(t.id)}"><b>💡 ${esc(t.title)}</b>
      <div class="small">${esc(t.start ? fmtWhen(t.start) : t.date ? t.date + ' · time not announced yet' : '')}</div>
      <div class="small" style="color:var(--sky)">from ${esc(t.name)} · click to comment</div></div>`).join('')
      : '<p class="faint small">No tips waiting. Heard about an event? Add it below and share it with the crew.</p>') : '';
  return `<div class="wrap stack" style="max-width:760px">
    ${problem(state.eventsError)}${hero}
    ${rest ? header('Coming up') + rest : ''}
    ${tips}
    <button class="btn" id="add-event">＋ Add an event I heard about</button>
    <p class="faint small">Epic doesn't share event times with apps, so the Fox Drop list is updated by hand when Epic announces one.
      While this window is open, Fox Drop reminds you a day before, an hour before, 10 minutes before, and when it goes live.</p>
  </div>`;
}

// ----- chat tab -----

function renderChat() {
  const me = crew.me;
  if (crew.signInError) return `<div class="center">${problem(crew.signInError)}<button class="btn ghost" id="retry-signin">Try again</button></div>`;
  if (!me) return spinner;
  if (me.banned && !me.admin) return '<div class="center"><div class="emoji">🚫</div><h2>Chat is off for this computer</h2><p class="faint">The admin turned off chat here.</p></div>';
  if (!crew.active) {
    const rejoin = me.member && crew.locked;
    return `<div class="join stack">
      <div class="row"><img src="fox-chat.svg" alt="" style="width:44px"><h2 style="margin:0;font-size:28px;font-weight:900">Fox Chat</h2></div>
      <p>${rejoin ? 'The invite code changed. Type the new one to get back in.' : 'Chat with your Fox Drop crew and share live-event tips. Ask the admin for the invite code.'}</p>
      <div><label class="lab" for="join-name">Chat name</label><input class="field" id="join-name" maxlength="20" value="${esc(me.name || '')}"></div>
      <p class="faint small" style="margin:0">Use a nickname, not your real name.</p>
      <div><label class="lab" for="join-code">Invite code</label><input class="field" id="join-code"></div>
      ${problem(crew.notice)}
      <button class="btn" id="join-btn">${rejoin ? 'Get back in' : 'Join the crew'}</button>
      <button class="btn text" id="claim-admin" style="align-self:flex-start">I'm the admin</button>
    </div>`;
  }
  return `<div id="adminbar"></div><div class="messages" id="messages"></div>${problem(crew.notice)}
    ${me.canPost ? `<div class="composer"><textarea class="field" id="composer" rows="1" maxlength="500" placeholder="Message the crew (Enter to send, Shift+Enter for a new line)"></textarea>
      <button class="btn" id="send">Send</button></div>`
      : '<div class="composer faint">The admin muted you for now. You can still read the chat.</div>'}`;
}

function renderAdminBar() {
  const bar = $('#adminbar');
  if (!bar) return;
  bar.innerHTML = crew.me?.admin ? `<div class="adminbar"><span class="grow">🛡️ You're the admin</span>
    ${crew.reports.length ? `<b style="color:var(--error)">🚩 ${crew.reports.length}</b>` : ''}<button class="btn ghost" id="open-admin">Admin</button></div>` : '';
}

function bubble(p, meUid) {
  const mine = p.uid === meUid;
  return `<div class="msg${mine ? ' mine' : ''}">${mine ? '' : `<span class="who">${esc(p.name)}</span>`}
    <button class="bubble" data-post="${esc(p.path)}">${esc(p.text)}</button>${p.at ? `<span class="when">${fmtClock(p.at)}</span>` : ''}</div>`;
}

function renderMessages() {
  const box = $('#messages');
  if (!box) return;
  const hidden = blocked();
  const posts = crew.chat.filter((p) => !hidden.has(p.uid));
  const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
  box.innerHTML = posts.length ? posts.map((p) => bubble(p, crew.uid)).join('') : '<p class="faint">No messages yet. Say hi! 👋</p>';
  if (atBottom || !renderMessages.done) box.scrollTop = box.scrollHeight;
  renderMessages.done = true;
}

// ----- tab switching -----

const renderers = { shop: renderShop, new: renderNew, news: renderNews, events: renderEvents, chat: renderChat, servers: renderServers, wishlist: renderWishlist };

function render() {
  // Don't rebuild the page under someone's cursor while they type in a search box or the chat.
  const active = document.activeElement;
  const typing = active && main.contains(active) && (active.id === 'shop-filter' || active.id === 'wish-search' || active.id === 'composer' || active.id?.startsWith('join-'));
  if (typing) { if (state.tab === 'chat') { renderMessages(); renderAdminBar(); } return; }
  main.classList.toggle('chat-mode', state.tab === 'chat' && crew.active);
  main.innerHTML = renderers[state.tab]();
  if (state.tab === 'chat') { renderMessages.done = false; renderMessages(); renderAdminBar(); }
  tick();
}

function setTab(tab) {
  if (!renderers[tab]) tab = 'shop';
  state.tab = tab;
  document.querySelectorAll('#tabs button').forEach((b) => (b.dataset.tab === tab ? b.setAttribute('aria-current', 'page') : b.removeAttribute('aria-current')));
  history.replaceState(null, '', '?tab=' + tab);
  main.scrollTop = 0;
  render();
  main.focus({ preventScroll: true });
}

// Countdowns tick in place so the rest of the page isn't rebuilt every second.
function tick() {
  const now = Date.now();
  document.querySelectorAll('[data-until]').forEach((el) => {
    const ms = Number(el.dataset.until) - now;
    el.textContent = el.dataset.short && ms > 86400000 ? `${Math.floor(ms / 86400000)}d` : countdown(ms);
  });
}

// ---------- dialogs ----------

const dialog = $('#dialog');
let dialogRenderer = null;
function openDialog(renderFn) { dialogRenderer = renderFn; crew.notice = null; dialog.innerHTML = renderFn(); if (!dialog.open) dialog.showModal(); }
function closeDialog() { dialogRenderer = null; crew.openTip = null; crew.commentsUnsub?.(); crew.commentsUnsub = null; if (dialog.open) dialog.close(); }
function refreshOpenDialog() {
  if (!dialogRenderer || !dialog.open) return;
  const a = document.activeElement;
  if (a && dialog.contains(a) && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA')) {
    const scroll = $('.scroll', dialog);
    if (scroll && dialogRenderer.partial) { scroll.innerHTML = dialogRenderer.partial(); }
    return;
  }
  dialog.innerHTML = dialogRenderer();
}
dialog.addEventListener('close', () => { dialogRenderer = null; crew.commentsUnsub?.(); crew.commentsUnsub = null; });

function eventDialog({ heading, confirm, initial, share }) {
  const zone = initial?.start || new Date();
  const date = dayString(initial && initial.start > new Date() ? initial.start : new Date());
  const time = initial && !initial.approx ? `${pad(zone.getHours())}:${pad(zone.getMinutes())}` : '';
  openDialog(() => `<h3>${esc(heading)}</h3>
    <form method="dialog" id="event-form" class="stack">
      <input class="field" name="title" maxlength="80" placeholder="What's the event?" value="${esc(initial?.title || '')}" required>
      <div class="row"><div class="grow"><label class="lab">Day</label><input class="field" type="date" name="date" value="${date}" min="${dayString(new Date())}" required></div>
        <div class="grow"><label class="lab">Time (if you know it)</label><input class="field" type="time" name="time" value="${time}"></div></div>
      <input class="field" name="note" maxlength="300" placeholder="Where did you hear it? (optional)" value="${esc(initial?.note || '')}">
      <p class="faint small" style="margin:0">Times are this computer's time zone. Tip: Epic usually says Eastern time (ET).</p>
      ${share ? `<label class="switch"><input type="checkbox" name="share" checked><span><b>${esc(share.label)}</b><br><span class="faint small">${esc(share.blurb)}</span></span></label>` : ''}
      ${problem(crew.notice)}
      <div class="actions"><button class="btn ghost" value="cancel" formnovalidate>Cancel</button><button class="btn" value="ok">${esc(confirm)}</button></div>
    </form>`);
  return new Promise((resolve) => {
    $('#event-form').addEventListener('submit', (ev) => {
      if (ev.submitter?.value !== 'ok') { resolve(null); return; }
      const f = new FormData(ev.target);
      const [y, m, d] = f.get('date').split('-').map(Number);
      const t = f.get('time');
      const start = t ? new Date(y, m - 1, d, ...t.split(':').map(Number)) : new Date(y, m - 1, d);
      if (t && start < new Date()) { ev.preventDefault(); toast('That time already passed.'); return; }
      resolve({
        id: initial?.id || 'my-' + Date.now(), title: String(f.get('title')).trim(), start, approx: !t,
        note: String(f.get('note') || '').trim(), custom: !initial, share: f.get('share') === 'on',
      });
    }, { once: true });
  });
}

async function addEvent() {
  const me = crew.me; const admin = !!me?.admin;
  const canShare = crew.active && me?.canPost;
  const e = await eventDialog({
    heading: 'Add a live event', confirm: canShare ? (admin ? 'Publish' : 'Send tip') : 'Add',
    share: canShare ? (admin ? { label: 'Publish for everyone', blurb: 'Every Fox Drop phone and PC gets the countdown and reminders.' }
      : { label: 'Share with the crew', blurb: "The admin checks tips before they go on everyone's calendar. Untick to keep it on this computer only." }) : null,
  });
  if (!e) return;
  if (!e.share) { save('my_events', [...store('my_events', []).filter((o) => o.id !== e.id), eventJson(e)]); scheduleReminders(); render(); }
  else if (admin) { if (await actions.publish(e)) toast('Published for everyone'); }
  else if (await actions.postTip(e)) toast('Tip sent to the crew');
}

function removeEvent(id) {
  if (id.startsWith(CREW)) { if (confirm('Remove this event for everyone?')) actions.removeOfficial(id); return; }
  save('my_events', store('my_events', []).filter((o) => o.id !== id));
  scheduleReminders();
  render();
}

function postActions(path) {
  const p = [...crew.chat, ...crew.comments].find((x) => x.path === path);
  if (!p) return;
  const me = crew.me; const mine = p.uid === me.uid;
  const m = crew.members.find((x) => x.uid === p.uid);
  openDialog(() => `<h3>${esc(p.name || 'Message')}</h3><p class="faint">"${esc(p.text)}"</p><div class="stack" style="align-items:flex-start">
    ${!mine ? `<button class="btn text" data-act="report">🚩 Report to the admin</button><button class="btn text" data-act="hide">🙈 Hide everything from ${esc(p.name)}</button>` : ''}
    ${me.admin ? `<button class="btn text" data-act="delete">🗑️ Delete this message</button>
      ${!mine && m ? `<button class="btn text" data-act="mute">${m.muted ? '🔊 Unmute' : '🔇 Mute'} ${esc(p.name)}</button>
        <button class="btn text" data-act="ban">${m.banned ? '✅ Unban' : '🚫 Ban'} ${esc(p.name)}</button>` : ''}` : ''}
    </div><div class="actions"><button class="btn ghost" data-act="close">Close</button></div>`);
  dialog.onclick = async (ev) => {
    const a = ev.target.closest('[data-act]')?.dataset.act;
    if (!a) return;
    dialog.onclick = null;
    closeDialog();
    if (a === 'report' && await actions.report(p)) toast('Reported to the admin');
    if (a === 'hide') { save('blocked', [...blocked(), p.uid]); renderMessages(); toast(`Hidden: ${p.name}`); }
    if (a === 'delete') actions.remove(p.path);
    if (a === 'mute') actions.setMuted(p.uid, !m.muted);
    if (a === 'ban') actions.setBanned(p.uid, !m.banned);
  };
}

function adminDialog() {
  const r = () => `<h3>🛡️ Admin</h3>
    <b>Invite code</b><div class="faint">Now: ${esc(crew.inviteCode ?? 'not set yet')}</div>
    <div class="row" style="margin-top:6px"><input class="field grow" id="new-code" maxlength="40" placeholder="New code"><button class="btn" data-act="code">Change</button></div>
    <p class="faint small">Changing it locks everyone out of the chat until they type the new code. You stay in.</p>${problem(crew.notice)}
    <b>Reports (${crew.reports.length})</b>
    <div class="scroll">${crew.reports.length ? crew.reports.map((x) => `<div class="card" style="background:var(--night)">${esc(x.name)}: ${esc(x.text)}
      <div><button class="btn text" data-act="del-report" data-id="${esc(x.id)}" data-path="${esc(x.path)}">Delete message</button>
      <button class="btn text" data-act="dismiss" data-id="${esc(x.id)}">Dismiss</button></div></div>`).join('') : '<span class="faint">Nothing reported.</span>'}</div>
    <b style="display:block;margin-top:12px">Members (${crew.members.length})</b>
    <div class="scroll">${crew.members.map((m) => `<div class="row"><div class="grow">${esc(m.name)}${m.uid === crew.uid ? ' (you)' : ''}
      <div class="small" style="color:${m.muted || m.banned ? 'var(--error)' : 'var(--faint)'}">${[m.muted && 'muted', m.banned && 'banned'].filter(Boolean).join(' · ') || 'ok'}</div></div>
      ${m.uid !== crew.uid ? `<button class="btn text" data-act="mute" data-uid="${esc(m.uid)}" data-on="${!m.muted}">${m.muted ? 'Unmute' : 'Mute'}</button>
        <button class="btn text" data-act="ban" data-uid="${esc(m.uid)}" data-on="${!m.banned}">${m.banned ? 'Unban' : 'Ban'}</button>` : ''}</div>`).join('')}</div>
    <div class="actions"><button class="btn ghost" data-act="close">Done</button></div>`;
  openDialog(r);
  dialog.onclick = async (ev) => {
    const b = ev.target.closest('[data-act]');
    if (!b) return;
    const a = b.dataset.act;
    if (a === 'close') { dialog.onclick = null; closeDialog(); return; }
    if (a === 'code') { const v = $('#new-code').value; if (v.trim() && await actions.setInvite(v)) toast('Invite code changed'); }
    if (a === 'del-report') { await actions.remove(b.dataset.path); actions.dismissReport(b.dataset.id); }
    if (a === 'dismiss') actions.dismissReport(b.dataset.id);
    if (a === 'mute') actions.setMuted(b.dataset.uid, b.dataset.on === 'true');
    if (a === 'ban') actions.setBanned(b.dataset.uid, b.dataset.on === 'true');
  };
}

function tipDialog(tipId) {
  crew.commentsUnsub?.();
  crew.comments = [];
  crew.commentsUnsub = onSnapshot(query(collection(db, 'tips', tipId, 'comments'), orderBy('at'), limit(200)), (s) => {
    crew.comments = s.docs.map(postOf).filter(Boolean);
    refreshOpenDialog();
  });
  const list = () => {
    const hidden = blocked();
    const c = crew.comments.filter((p) => !hidden.has(p.uid));
    return c.length ? c.map((p) => bubble(p, crew.uid)).join('') : '<span class="faint small">No comments yet.</span>';
  };
  const r = () => {
    const t = crew.tips.find((x) => x.id === tipId);
    if (!t) { setTimeout(closeDialog); return ''; }   // approved or deleted meanwhile
    const me = crew.me;
    return `<h3>💡 ${esc(t.title)}</h3><div>${esc(t.start ? fmtWhen(t.start) : t.date ? t.date + ' · time not announced yet' : '')}</div>
      ${t.note ? `<p class="faint small">${esc(t.note)}</p>` : ''}<div class="small" style="color:var(--sky)">from ${esc(t.name)}</div>
      <div class="row" style="margin:8px 0">${me.admin ? '<button class="btn text" data-act="approve">✅ Approve</button>' : ''}
        ${me.admin || t.uid === me.uid ? '<button class="btn danger" data-act="delete-tip">🗑️ Delete tip</button>' : ''}</div>
      <div class="scroll">${list()}</div>${problem(crew.notice)}
      ${me.canPost ? '<div class="row" style="margin-top:10px"><input class="field grow" id="comment" maxlength="300" placeholder="Add a comment"><button class="btn" data-act="comment">Send</button></div>' : ''}
      <div class="actions"><button class="btn ghost" data-act="close">Close</button></div>`;
  };
  r.partial = list;
  openDialog(r);
  dialog.onclick = async (ev) => {
    const b = ev.target.closest('[data-act], [data-post]');
    if (!b) return;
    if (b.dataset.post) { dialog.onclick = null; postActions(b.dataset.post); return; }
    const a = b.dataset.act;
    const t = crew.tips.find((x) => x.id === tipId);
    if (a === 'close') { dialog.onclick = null; closeDialog(); }
    if (a === 'comment') { const input = $('#comment'); if (input.value.trim() && await actions.comment(tipId, input.value)) input.value = ''; }
    if (a === 'delete-tip' && confirm('Delete this tip?')) { dialog.onclick = null; closeDialog(); actions.remove('tips/' + tipId); }
    if (a === 'approve' && t) {
      dialog.onclick = null;
      closeDialog();
      const e = await eventDialog({ heading: 'Approve for everyone', confirm: 'Publish', initial: tipEvent(t) });
      if (e && await actions.approve(t, e)) toast('Approved: it is on everyone\'s calendar');
    }
  };
  dialog.onkeydown = (ev) => { if (ev.key === 'Enter' && ev.target.id === 'comment') { ev.preventDefault(); $('[data-act="comment"]', dialog)?.click(); } };
}

function claimAdminDialog() {
  openDialog(() => `<h3>Admin sign-in</h3><p class="faint small">Type the admin code from the Firebase console. This computer becomes an admin for good.</p>
    <label class="lab">Your chat name</label><input class="field" id="admin-name" maxlength="20">
    <label class="lab">Admin code</label><input class="field" id="admin-code" type="password">${problem(crew.notice)}
    <div class="actions"><button class="btn ghost" data-act="close">Cancel</button><button class="btn" data-act="ok">Sign in</button></div>`);
  dialog.onclick = async (ev) => {
    const a = ev.target.closest('[data-act]')?.dataset.act;
    if (a === 'close') { dialog.onclick = null; closeDialog(); }
    if (a === 'ok') {
      const name = $('#admin-name').value; const code = $('#admin-code').value;
      if (name.trim() && code.trim() && await actions.claimAdmin(name, code)) { dialog.onclick = null; closeDialog(); toast('You are the admin'); }
    }
  };
}

function settingsDialog() {
  const perm = 'Notification' in window ? Notification.permission : 'unsupported';
  openDialog(() => `<h3>Settings</h3>
    <label class="switch"><input type="checkbox" id="alerts-on" ${store('alerts', true) ? 'checked' : ''}><span><b>Windows notifications</b><br>
      <span class="faint small">Wishlist items in the shop, Fox Chat messages, and live-event reminders, while Fox Drop is open.</span></span></label>
    ${perm === 'default' ? '<button class="btn ghost" data-act="perm" style="margin-top:10px">Allow notifications</button>' : ''}
    ${perm === 'denied' ? '<p class="problem small">Notifications are blocked for this site. Turn them on in the browser\'s site settings.</p>' : ''}
    <p class="faint small" style="margin-top:14px">To keep Fox Drop in the taskbar: in Edge or Chrome, open the ⋯ menu and choose <b>Install Fox Drop</b> (or Apps → Install).</p>
    <p class="faint small">Fox Drop is an unofficial fan app, not made or endorsed by Epic Games. Fortnite is a trademark of Epic Games, Inc.
      <a href="../privacy.html" target="_blank" rel="noopener">Privacy policy</a></p>
    <div class="actions"><button class="btn" data-act="close">Done</button></div>`);
  dialog.onclick = async (ev) => {
    const a = ev.target.closest('[data-act]')?.dataset.act;
    if (a === 'perm') { await Notification.requestPermission(); settingsDialog(); }
    if (a === 'close') { dialog.onclick = null; closeDialog(); }
  };
  dialog.onchange = (ev) => { if (ev.target.id === 'alerts-on') save('alerts', ev.target.checked); };
}

// ---------- wiring ----------

$('#tabs').addEventListener('click', (e) => { const b = e.target.closest('button[data-tab]'); if (b) setTab(b.dataset.tab); });
$('#refresh').addEventListener('click', () => refresh());
$('#settings').addEventListener('click', settingsDialog);

let searchTimer;
main.addEventListener('input', (e) => {
  if (e.target.id === 'shop-filter') {
    state.shopFilter = e.target.value;
    const pos = e.target.selectionStart;
    main.innerHTML = renderShop();
    const f = $('#shop-filter'); f.focus(); f.setSelectionRange(pos, pos);
    tick();
  }
  if (e.target.id === 'wish-search') {
    state.query = e.target.value;
    clearTimeout(searchTimer);
    if (state.query.trim().length < 2) { state.results = null; return; }
    searchTimer = setTimeout(async () => {
      state.results = null;
      const q = state.query;
      const found = await searchCosmetics(q);
      if (q !== state.query) return;
      state.results = found;
      const pos = $('#wish-search')?.selectionStart;
      main.innerHTML = renderWishlist();
      const f = $('#wish-search'); f.focus(); if (pos != null) f.setSelectionRange(pos, pos);
    }, 350);
  }
  if (e.target.id === 'composer') { e.target.style.height = 'auto'; e.target.style.height = e.target.scrollHeight + 'px'; }
});

main.addEventListener('click', async (e) => {
  const t = e.target;
  const wish = t.closest('[data-wish]');
  if (wish) { toggleWish(JSON.parse(wish.dataset.wish)); return; }
  const go = t.closest('[data-goto]');
  if (go) { setTab(go.dataset.goto); return; }
  const rm = t.closest('[data-remove-event]');
  if (rm) { removeEvent(rm.dataset.removeEvent); return; }
  const tip = t.closest('[data-tip]');
  if (tip) { tipDialog(tip.dataset.tip); return; }
  const post = t.closest('[data-post]');
  if (post) { postActions(post.dataset.post); return; }
  if (t.id === 'add-event') addEvent();
  if (t.id === 'retry-signin') { crew.signInError = null; signInAnonymously(auth).catch(() => {}); render(); }
  if (t.id === 'claim-admin') claimAdminDialog();
  if (t.id === 'open-admin') adminDialog();
  if (t.id === 'join-btn') {
    const name = $('#join-name').value; const code = $('#join-code').value;
    if (!name.trim() || !code.trim()) { toast('Type a chat name and the invite code'); return; }
    t.disabled = true;
    const ok = await actions.join(name, code);
    t.disabled = false;
    if (ok) toast('Welcome to the crew!'); else { t.blur(); render(); }
  }
  if (t.id === 'send') sendMessage();
});

main.addEventListener('keydown', (e) => {
  if (e.target.id === 'composer' && e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
  if (e.target.id?.startsWith('join-') && e.key === 'Enter') $('#join-btn')?.click();
});

async function sendMessage() {
  const box = $('#composer');
  const text = box?.value || '';
  if (!text.trim()) return;
  box.value = '';
  box.style.height = 'auto';
  if (!(await actions.send(text))) box.value = text;
}

navigator.serviceWorker?.addEventListener('message', (e) => { if (e.data?.tab) setTab(e.data.tab); });
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});

setInterval(tick, 1000);
setInterval(() => refreshAll(), 15 * 60 * 1000);   // same cadence as the phone app's watcher
setTab(new URLSearchParams(location.search).get('tab') || 'shop');
refreshAll();
startCrew();
