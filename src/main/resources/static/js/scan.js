(() => {
	'use strict';

	const $ = id => document.getElementById(id);

	const barcodeInput = $('barcode-input');
	const lookupForm = $('lookup-form');
	const statusText = $('scan-status');
	const photoInputs = [$('barcode-photo'), $('barcode-gallery')].filter(Boolean);
	const liveSection = $('live-section');
	const insecureHint = $('insecure-hint');
	const video = $('scanner-video');
	const canvas = $('scanner-canvas');
	const startButton = $('start-scan');
	const stopButton = $('stop-scan');

	const MAX_SIDE = 1600;

	let stream = null;
	let scanTimer = null;
	let detector = null;
	let isBusy = false;
	let finished = false;

	function setStatus(message, kind) {
		if (!statusText) return;
		statusText.textContent = message;
		statusText.classList.remove('ok', 'error');
		if (kind) statusText.classList.add(kind);
	}

	const sanitizeBarcode = value => (value || '').replace(/[^0-9]/g, '').trim();

	// QR-Etikett eines Lagerorts? Dann direkt den Inhalt zeigen, immer auf der aktuellen Adresse der App.
	function locationLink(raw) {
		try {
			const url = new URL(raw || '', window.location.origin);
			const name = url.searchParams.get('name');
			return url.pathname.endsWith('/locations') && name ? '/locations?name=' + encodeURIComponent(name) : null;
		} catch (e) {
			return null;
		}
	}

	function goToLocation(link) {
		finished = true;
		stopCamera();
		setStatus('Lagerort erkannt, Inhalt wird geöffnet …', 'ok');
		window.location.href = link;
	}

	// Roh-Inhalt weitergeben: GS1-Codes (DataMatrix/QR) enthalten neben der GTIN evtl. das Ablaufdatum,
	// der Server liest beides aus. Normale Barcodes werden dort wie bisher auf Ziffern reduziert.
	function goToResult(code) {
		finished = true;
		stopCamera();
		setStatus('Barcode erkannt: ' + sanitizeBarcode(code), 'ok');
		window.location.href = '/scan/result?barcode=' + encodeURIComponent(code.trim());
	}

	// ---------- Serien-Scan (Einräumen): weiterscannen statt zur Ergebnisseite ----------

	const seriesToggle = $('series-mode');
	const seriesLog = $('series-log');
	const inboxCount = $('inbox-count');
	const SERIES_KEY = 'foodmanager.seriesMode';
	const REPEAT_PAUSE_MS = 4000; // derselbe Code zählt erst nach dieser Pause wieder (Kamera sieht ihn mehrfach)

	let lastSeriesCode = '';
	let lastSeriesTime = 0;
	let seriesBusy = false;

	const seriesMode = () => !!(seriesToggle && seriesToggle.checked);

	if (seriesToggle) {
		try { seriesToggle.checked = localStorage.getItem(SERIES_KEY) === '1'; } catch (e) { /* egal */ }
		seriesToggle.addEventListener('change', () => {
			try { localStorage.setItem(SERIES_KEY, seriesToggle.checked ? '1' : '0'); } catch (e) { /* egal */ }
			setStatus(seriesToggle.checked
				? 'Serien-Scan an: einfach ein Produkt nach dem anderen scannen.'
				: 'Serien-Scan aus: nach dem Scan öffnet sich das Ergebnis.');
		});
	}

	function csrfHeaders() {
		const header = document.querySelector('meta[name="csrf-header"]');
		const token = document.querySelector('meta[name="csrf-token"]');
		return header && token ? { [header.content]: token.content } : {};
	}

	function logSeries(message, kind) {
		if (!seriesLog) return;
		seriesLog.hidden = false;
		const li = document.createElement('li');
		li.textContent = message;
		if (kind) li.className = kind;
		seriesLog.prepend(li);
		while (seriesLog.children.length > 8) seriesLog.lastElementChild.remove();
	}

	async function submitSeries(code) {
		const clean = code.trim();
		const now = Date.now();
		if (seriesBusy || (clean === lastSeriesCode && now - lastSeriesTime < REPEAT_PAUSE_MS)) return;
		lastSeriesCode = clean;
		lastSeriesTime = now;
		seriesBusy = true;
		setStatus('Wird gebucht …');

		try {
			const response = await fetch('/scan/series', {
				method: 'POST',
				headers: Object.assign({ 'Content-Type': 'application/x-www-form-urlencoded' }, csrfHeaders()),
				body: new URLSearchParams({ code: clean }),
				credentials: 'same-origin'
			});
			if (!response.ok) throw new Error('HTTP ' + response.status);
			const result = await response.json();
			const kind = result.status === 'error' ? 'error' : 'ok';
			setStatus(result.message, kind);
			logSeries(result.message, result.status);
			if (inboxCount && typeof result.pendingCount === 'number') inboxCount.textContent = result.pendingCount;
			if (navigator.vibrate && kind === 'ok') navigator.vibrate(60);
		} catch (e) {
			setStatus('Konnte nicht gebucht werden. Verbindung prüfen und erneut scannen.', 'error');
			lastSeriesCode = ''; // gleich nochmal erlauben
		} finally {
			seriesBusy = false;
		}
	}

	/** Erkannter Code: im Serien-Modus buchen und weiterscannen, sonst zur Ergebnisseite. Liefert true, wenn der Scan endet. */
	function handleCode(code) {
		if (seriesMode()) {
			submitSeries(code);
			return false;
		}
		goToResult(code);
		return true;
	}

	// ---------- Foto: funktioniert auch ohne HTTPS ----------

	// Handyfotos sind oft 5-12 MB. Verkleinern spart Upload-Zeit und bleibt unter dem 10-MB-Limit.
	async function downscale(blob) {
		try {
			const bitmap = await createImageBitmap(blob, { imageOrientation: 'from-image' });
			const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
			if (scale === 1 && blob.size < 1.5 * 1024 * 1024) {
				if (bitmap.close) bitmap.close();
				return blob;
			}
			const c = document.createElement('canvas');
			c.width = Math.round(bitmap.width * scale);
			c.height = Math.round(bitmap.height * scale);
			c.getContext('2d').drawImage(bitmap, 0, 0, c.width, c.height);
			if (bitmap.close) bitmap.close();
			return await new Promise(resolve => c.toBlob(b => resolve(b || blob), 'image/jpeg', 0.9));
		} catch (e) {
			return blob;
		}
	}

	async function decodeOnServer(blob, silent) {
		if (isBusy || finished) return false;
		isBusy = true;
		if (!silent) setStatus('Barcode wird erkannt …');

		try {
			const formData = new FormData();
			formData.append('image', blob, 'scan.jpg');

			const response = await fetch('/api/barcodes/decode', { method: 'POST', body: formData });
			if (!response.ok) throw new Error('HTTP ' + response.status);

			const result = await response.json();
			const link = result.detected && locationLink(result.barcode);
			if (link) {
				goToLocation(link);
				return true;
			}
			if (result.detected && sanitizeBarcode(result.barcode)) {
				return handleCode(result.barcode);
			}
			if (!silent) {
				setStatus(result.message || 'Kein Barcode erkannt. Geh näher ran und sorge für gutes Licht.', 'error');
			}
		} catch (e) {
			if (!silent) setStatus('Fehler bei der Barcode-Erkennung. Bitte erneut versuchen.', 'error');
		} finally {
			isBusy = false;
		}
		return false;
	}

	async function handlePhoto(input) {
		const file = input.files && input.files[0];
		if (!file) return;
		setStatus('Foto wird vorbereitet …');
		const blob = await downscale(file);
		await decodeOnServer(blob, false);
		input.value = ''; // dasselbe Foto kann erneut gewählt werden
	}

	photoInputs.forEach(input => input.addEventListener('change', () => handlePhoto(input)));

	// ---------- Live-Kamera: braucht HTTPS (oder localhost) ----------

	const liveSupported = window.isSecureContext
		&& navigator.mediaDevices
		&& typeof navigator.mediaDevices.getUserMedia === 'function';

	async function createDetector() {
		if (!('BarcodeDetector' in window)) return null;
		try {
			const supported = await window.BarcodeDetector.getSupportedFormats();
			const wanted = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'qr_code', 'data_matrix', 'code_128'].filter(f => supported.includes(f));
			return wanted.length ? new window.BarcodeDetector({ formats: wanted }) : null;
		} catch (e) {
			return null;
		}
	}

	function grabFrame() {
		const scale = Math.min(1, MAX_SIDE / Math.max(video.videoWidth, video.videoHeight));
		canvas.width = Math.round(video.videoWidth * scale);
		canvas.height = Math.round(video.videoHeight * scale);
		canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
		return new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85));
	}

	function scheduleScan() {
		clearTimeout(scanTimer);
		if (!stream || finished) return;
		scanTimer = setTimeout(scanTick, detector ? 250 : 1500);
	}

	async function scanTick() {
		if (!stream || finished) return;

		if (video.readyState >= 2 && video.videoWidth) {
			try {
				if (detector) {
					const codes = await detector.detect(video);
					const link = codes[0] && locationLink(codes[0].rawValue);
					if (link) {
						goToLocation(link);
						return;
					}
					const raw = codes[0] && codes[0].rawValue;
					if (sanitizeBarcode(raw) && handleCode(raw)) {
						return;
					}
				} else {
					const blob = await grabFrame();
					if (blob && await decodeOnServer(blob, true)) return;
				}
			} catch (e) {
				// einfach beim nächsten Durchlauf erneut versuchen
			}
		}
		scheduleScan();
	}

	async function startCamera() {
		stopCamera();
		try {
			stream = await navigator.mediaDevices.getUserMedia({
				video: {
					facingMode: { ideal: 'environment' },
					width: { ideal: 1280 },
					height: { ideal: 720 }
				},
				audio: false
			});
			video.srcObject = stream;
			liveSection.classList.add('is-live');
			await video.play();
			detector = await createDetector();
			setStatus('Kamera aktiv. Halte den Barcode in den Rahmen, er wird automatisch erkannt.');
			scheduleScan();
		} catch (error) {
			stopCamera();
			const denied = error && (error.name === 'NotAllowedError' || error.name === 'SecurityError');
			setStatus(denied
				? 'Kamerazugriff blockiert. Erlaube die Kamera in den Browser-Einstellungen oder nutze „Barcode fotografieren“.'
				: 'Die Kamera konnte nicht gestartet werden. Nutze „Barcode fotografieren“.', 'error');
		}
	}

	function stopCamera() {
		clearTimeout(scanTimer);
		if (stream) {
			stream.getTracks().forEach(track => track.stop());
			stream = null;
		}
		if (video) {
			video.pause();
			video.srcObject = null;
		}
		if (liveSection) liveSection.classList.remove('is-live');
	}

	if (liveSupported && liveSection) {
		liveSection.classList.remove('hidden');

		// Live-Kamera ist der schnellste Weg: nach oben und als Hauptknopf, Foto bleibt als Ausweichweg
		const panel = liveSection.closest('.scanner-panel');
		if (panel) panel.classList.add('live-first');
		if (startButton) startButton.classList.add('primary', 'large');
		document.querySelectorAll('.capture-row .button.primary').forEach(b => b.classList.remove('primary'));
		const hint = panel && panel.querySelector('.photo-hint');
		if (hint) hint.textContent = 'Klappt die Kamera nicht? Dann den Barcode fotografieren:';
	} else if (insecureHint) {
		insecureHint.classList.remove('hidden');
	}

	if (startButton) startButton.addEventListener('click', startCamera);

	if (stopButton) {
		stopButton.addEventListener('click', () => {
			stopCamera();
			setStatus('Kamera gestoppt.');
		});
	}

	// ---------- Scanner/Tastatur ----------

	if (lookupForm && barcodeInput) {
		lookupForm.addEventListener('submit', event => {
			event.preventDefault();
			if (!sanitizeBarcode(barcodeInput.value)) {
				setStatus('Bitte einen gültigen Barcode scannen oder eingeben.', 'error');
				return;
			}
			if (seriesMode()) {
				// USB-/Bluetooth-Scanner: Feld leeren und bereit für den nächsten Scan
				lastSeriesCode = '';
				submitSeries(barcodeInput.value);
				barcodeInput.value = '';
				barcodeInput.focus();
				return;
			}
			goToResult(barcodeInput.value);
		});

		// Auf dem Handy nicht automatisch fokussieren, sonst springt die Tastatur auf.
		if (window.matchMedia('(pointer: fine)').matches) {
			setTimeout(() => barcodeInput.focus(), 50);
		}
	}

	window.addEventListener('pagehide', stopCamera);
	setStatus('Bereit. Fotografiere einen Barcode oder gib die Nummer ein.');
})();