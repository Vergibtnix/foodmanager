/*
 * Service Worker für Foodmanager.
 *
 * Er macht genau zwei Dinge:
 *  1. Die Einkaufsliste wird bei jedem Besuch gemerkt und ist im Laden auch ohne Netz lesbar.
 *  2. Fällt eine Seite aus, weil kein Netz da ist, kommt eine freundliche Hinweisseite statt der Browser-Fehlerseite.
 *
 * Alles andere läuft unverändert über das Netz. Schreibende Aktionen (Abhaken, Hinzufügen) gehen nur online.
 * Beim Abmelden wird die gemerkte Einkaufsliste sofort gelöscht.
 *
 * Voraussetzung ist HTTPS (oder localhost). Ohne das registriert der Browser den Service Worker gar nicht.
 */
const VERSION = 'v2';
const STATIC_CACHE = 'foodmanager-static-' + VERSION;
const PAGE_CACHE = 'foodmanager-pages-' + VERSION;

const OFFLINE_URL = '/offline.html';
const SHOPPING_PATH = '/shopping-list';
const PRECACHE = [OFFLINE_URL, '/css/app.css', '/js/nav.js'];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(STATIC_CACHE)
            .then(cache => cache.addAll(PRECACHE))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys
                .filter(key => key !== STATIC_CACHE && key !== PAGE_CACHE)
                .map(key => caches.delete(key))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    const request = event.request;
    const url = new URL(request.url);

    if (url.origin !== self.location.origin) return;

    // Abmelden: gemerkte Seiten löschen, die Anfrage selbst läuft normal weiter
    if (request.method === 'POST' && url.pathname === '/logout') {
        event.waitUntil(caches.delete(PAGE_CACHE));
        return;
    }

    if (request.method !== 'GET') return;

    if (request.mode === 'navigate') {
        event.respondWith(handleNavigation(request, url));
        return;
    }

    if (isStaticAsset(url)) {
        event.respondWith(networkFirst(request));
    }
});

function isStaticAsset(url) {
    return url.pathname.startsWith('/css/')
        || url.pathname.startsWith('/js/')
        || url.pathname.startsWith('/icons/');
}

// ---------- Seiten ----------

async function handleNavigation(request, url) {
    try {
        const response = await fetch(request);

        // Nur eine echte Antwort der Einkaufsliste merken, keine Weiterleitung zum Login
        if (url.pathname === SHOPPING_PATH && response.ok && response.type === 'basic') {
            await storeShoppingList(response.clone());
        }
        return response;
    } catch (error) {
        // Kein Netz
        if (url.pathname === SHOPPING_PATH) {
            const cached = await readShoppingListOffline();
            if (cached) return cached;
        }
        const offline = await caches.match(OFFLINE_URL);
        return offline || Response.error();
    }
}

async function storeShoppingList(response) {
    const body = await response.blob();
    const headers = new Headers(response.headers);
    headers.set('X-Cached-At', new Date().toISOString());

    const cache = await caches.open(PAGE_CACHE);
    await cache.put(SHOPPING_PATH, new Response(body, {
        status: response.status,
        statusText: response.statusText,
        headers: headers
    }));
}

async function readShoppingListOffline() {
    const cache = await caches.open(PAGE_CACHE);
    const cached = await cache.match(SHOPPING_PATH);
    if (!cached) return null;

    const cachedAt = cached.headers.get('X-Cached-At');
    let stand = 'unbekannt';
    if (cachedAt) {
        stand = new Date(cachedAt).toLocaleString('de-AT', { dateStyle: 'short', timeStyle: 'short' });
    }

    const html = await cached.text();
    const banner = '<div class="offline-banner" role="status">Offline. Stand von ' + stand
        + '. Änderungen sind erst wieder online möglich.</div>';

    // Hinweis ganz oben einsetzen und die Seite als "offline" markieren (das CSS sperrt dann die Eingaben)
    const patched = html.replace(/<body([^>]*)>/i, '<body$1 data-offline="true">' + banner);

    return new Response(patched, {
        status: 200,
        headers: { 'Content-Type': 'text/html; charset=utf-8' }
    });
}

// ---------- Stile, Skripte, Symbole ----------

async function networkFirst(request) {
    const cache = await caches.open(STATIC_CACHE);
    try {
        const response = await fetch(request);
        if (response.ok) cache.put(request, response.clone());
        return response;
    } catch (error) {
        const cached = await cache.match(request);
        return cached || Response.error();
    }
}