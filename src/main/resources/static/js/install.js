(() => {
	'use strict';

	// "Als App installieren": Android/Chrome/Edge bieten die Installation per Ereignis an, dann löst der Knopf
	// sie direkt aus. Safari auf dem iPhone kann das nicht, dort zeigt der Knopf eine kurze Anleitung.
	// Läuft Foodmanager schon als installierte App, bleiben Knopf und Hinweise versteckt.

	const standalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
	if (standalone) {
		document.documentElement.classList.add('is-app');
		return;
	}

	const buttons = Array.from(document.querySelectorAll('.install-btn'));
	const sheet = document.getElementById('install-sheet');
	if (!buttons.length) return;

	const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent)
		|| (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
	let deferredPrompt = null;

	const show = () => buttons.forEach(b => { b.hidden = false; });
	const hide = () => buttons.forEach(b => { b.hidden = true; });

	window.addEventListener('beforeinstallprompt', event => {
		event.preventDefault(); // eigener Knopf statt der kleinen Browser-Leiste
		deferredPrompt = event;
		show();
	});

	window.addEventListener('appinstalled', () => {
		deferredPrompt = null;
		hide();
	});

	// iPhone: Installieren geht nur in Safari über "Teilen", also Anleitung anbieten
	if (isIos) show();

	function openSheet() {
		if (!sheet) return;
		sheet.hidden = false;
		const close = sheet.querySelector('.install-close');
		if (close) close.focus();
	}

	if (sheet) {
		sheet.addEventListener('click', event => {
			if (event.target === sheet || event.target.closest('.install-close')) sheet.hidden = true;
		});
		document.addEventListener('keydown', event => {
			if (event.key === 'Escape') sheet.hidden = true;
		});
	}

	buttons.forEach(button => button.addEventListener('click', async () => {
		if (deferredPrompt) {
			deferredPrompt.prompt();
			const choice = await deferredPrompt.userChoice.catch(() => null);
			deferredPrompt = null;
			if (choice && choice.outcome === 'accepted') hide();
			return;
		}
		openSheet();
	}));
})();
