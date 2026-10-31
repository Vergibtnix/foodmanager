(() => {
	'use strict';

	// Live-Aktualisierung: Ändert jemand anderes etwas (z. B. Einkaufsliste abhaken), lädt die Seite
	// ihren Inhalt im Hintergrund neu. Welche Änderungen zählen, steht in <main data-live="shopping items">.

	const main = document.querySelector('main[data-live]');
	if (!main || !('EventSource' in window)) return;
	if (document.body.dataset.offline === 'true') return; // gemerkte Offline-Version, kein Server da

	const topics = main.dataset.live.split(/\s+/);
	let timer = null;
	let pending = false;
	let failures = 0;

	// Wer gerade tippt, soll nicht unterbrochen werden. Dann erst nach dem Verlassen des Feldes neu laden.
	function isTyping() {
		const el = document.activeElement;
		return el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') && el.value !== '';
	}

	async function refresh() {
		if (isTyping()) {
			pending = true;
			return;
		}
		pending = false;
		try {
			const response = await fetch(window.location.href, { credentials: 'same-origin', cache: 'no-store' });
			if (!response.ok || response.redirected) return;
			const doc = new DOMParser().parseFromString(await response.text(), 'text/html');
			const fresh = doc.querySelector('main[data-live]');
			if (!fresh) return;

			// Eingabefeld samt Fokus behalten (z. B. halb getippter Eintrag)
			const active = document.activeElement && document.activeElement.id;
			main.innerHTML = fresh.innerHTML;
			if (active) {
				const again = document.getElementById(active);
				if (again) again.focus();
			}
		} catch (e) {
			// Netz kurz weg: beim nächsten Ereignis erneut versuchen
		}
	}

	document.addEventListener('focusout', () => {
		if (pending) setTimeout(refresh, 100);
	});

	const source = new EventSource('/live');

	source.addEventListener('changed', event => {
		if (!topics.includes(event.data)) return;
		clearTimeout(timer);
		timer = setTimeout(refresh, 300); // mehrere Änderungen kurz hintereinander nur einmal laden
	});

	source.addEventListener('open', () => {
		failures = 0;
	});

	source.addEventListener('error', () => {
		// Nach mehreren Fehlschlägen (z. B. abgemeldet) aufgeben, statt endlos neu zu verbinden
		failures++;
		if (failures > 5) source.close();
	});

	window.addEventListener('pagehide', () => source.close());
})();
