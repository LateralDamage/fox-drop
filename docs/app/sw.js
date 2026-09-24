// Keeps the app shell on the PC so Fox Drop opens instantly and still shows its window offline.
// Feeds and Firebase always go to the network; only these files are cached. Bump VERSION on every release.
const VERSION = 'foxdrop-app-5';
const SHELL = ['./', 'index.html', 'style.css', 'app.js', 'fox-chat.svg', 'icon-192.png', 'icon-512.png', '../icon.svg', 'manifest.webmanifest'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Network first for the shell too, so a new version shows up on the next launch; the cache is the fallback.
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return;
  e.respondWith(
    fetch(e.request)
      .then((r) => {
        if (r.ok) { const copy = r.clone(); caches.open(VERSION).then((c) => c.put(e.request, copy)); }
        return r;
      })
      .catch(() => caches.match(e.request)),
  );
});

// Clicking a Fox Drop notification brings the app window forward on the right tab.
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const tab = e.notification.data?.tab || 'shop';
  e.waitUntil(self.clients.matchAll({ type: 'window' }).then((list) => {
    const w = list[0];
    if (w) { w.postMessage({ tab }); return w.focus(); }
    return self.clients.openWindow('./?tab=' + tab);
  }));
});
