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

	function goToResult(barcode) {
		finished = true;
		stopCamera();
		setStatus('Barcode erkannt: ' + barcode, 'ok');
		window.location.href = '/scan/result?barcode=' + encodeURIComponent(barcode);
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
			const barcode = sanitizeBarcode(result.barcode);

			if (result.detected && barcode) {
				goToResult(barcode);
				return true;
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
			const wanted = ['ean_13', 'ean_8', 'upc_a', 'upc_e'].filter(f => supported.includes(f));
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
					const barcode = sanitizeBarcode(codes[0] && codes[0].rawValue);
					if (barcode) {
						goToResult(barcode);
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
	}

	if (liveSupported && liveSection) {
		liveSection.classList.remove('hidden');
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
			const barcode = sanitizeBarcode(barcodeInput.value);

			if (!barcode) {
				setStatus('Bitte einen gültigen Barcode scannen oder eingeben.', 'error');
				return;
			}
			goToResult(barcode);
		});

		// Auf dem Handy nicht automatisch fokussieren, sonst springt die Tastatur auf.
		if (window.matchMedia('(pointer: fine)').matches) {
			setTimeout(() => barcodeInput.focus(), 50);
		}
	}

	window.addEventListener('pagehide', stopCamera);
	setStatus('Bereit. Fotografiere einen Barcode oder gib die Nummer ein.');
})();