const barcodeInput = document.getElementById('barcode-input');
const lookupForm = document.getElementById('lookup-form');
const statusText = document.getElementById('scan-status');
const startButton = document.getElementById('start-scan');
const stopButton = document.getElementById('stop-scan');
const analyzeFrameButton = document.getElementById('analyze-frame');
const video = document.getElementById('scanner-video');
const lookupResult = document.getElementById('lookup-result');
const lookupName = document.getElementById('lookup-name');
const lookupBrand = document.getElementById('lookup-brand');
const lookupBarcodeValue = document.getElementById('lookup-barcode');
const lookupMessage = document.getElementById('lookup-message');
const lookupImage = document.getElementById('lookup-image');
const imagePlaceholder = document.getElementById('lookup-image-placeholder');
const createLink = document.getElementById('create-link');

const state = {
	stream: null,
	scanning: false,
	detector: null,
	lastDetectedBarcode: '',
	scanStartedAt: 0,
	noDetectionHintShown: false,
	frameDecodeInProgress: false,
	lookupInProgress: false,
	lastFrameDecodeAt: 0,
	lastStatusMessage: ''
};

const frameCanvas = document.createElement('canvas');

function setControlsDisabled(isScanning) {
	startButton.disabled = isScanning;
	stopButton.disabled = !isScanning;
	analyzeFrameButton.disabled = !state.stream || state.frameDecodeInProgress || state.lookupInProgress;
}

async function openCameraStream() {
	const preferredConstraints = {
		video: {
			facingMode: { ideal: 'environment' },
			width: { ideal: 1280 },
			height: { ideal: 720 }
		},
		audio: false
	};

	try {
		return await navigator.mediaDevices.getUserMedia(preferredConstraints);
	} catch (error) {
		return navigator.mediaDevices.getUserMedia({
			video: true,
			audio: false
		});
	}
}

function setStatus(message) {
	if (state.lastStatusMessage === message) {
		return;
	}
	state.lastStatusMessage = message;
	statusText.textContent = message;
}

function resetLookupResult() {
	lookupResult.classList.add('hidden');
	lookupName.textContent = 'Noch kein Produkt geladen';
	lookupBrand.textContent = '-';
	lookupBarcodeValue.textContent = '-';
	lookupMessage.textContent = '';
	lookupImage.removeAttribute('src');
	lookupImage.classList.add('hidden');
	imagePlaceholder.classList.remove('hidden');
	createLink.href = '#';
}

function updateCreateLink(result) {
	const params = new URLSearchParams({ barcode: result.barcode || '' });
	if (result.name) {
		params.set('name', result.name);
	}
	if (result.brand) {
		params.set('brand', result.brand);
	}
	if (result.imageUrl) {
		params.set('imageUrl', result.imageUrl);
	}
	createLink.href = `/items/new?${params.toString()}`;
}

function renderLookupResult(result) {
	lookupResult.classList.remove('hidden');
	lookupName.textContent = result.name || 'Produkt manuell ergänzen';
	lookupBrand.textContent = result.brand || '-';
	lookupBarcodeValue.textContent = result.barcode || '-';
	lookupMessage.textContent = result.message || '';
	updateCreateLink(result);

	if (result.imageUrl) {
		lookupImage.src = result.imageUrl;
		lookupImage.classList.remove('hidden');
		imagePlaceholder.classList.add('hidden');
	} else {
		lookupImage.removeAttribute('src');
		lookupImage.classList.add('hidden');
		imagePlaceholder.classList.remove('hidden');
	}
}

async function performBarcodeLookup(barcode) {
	const cleanedBarcode = (barcode || '').trim();
	if (!cleanedBarcode) {
		setStatus('Bitte gib zuerst einen Barcode ein.');
		return;
	}

	state.lookupInProgress = true;
	setControlsDisabled(state.scanning);
	setStatus('Produktdaten werden geladen ...');

	try {
		const response = await fetch(`/api/barcodes/lookup?barcode=${encodeURIComponent(cleanedBarcode)}`);
		if (!response.ok) {
			throw new Error(`Lookup fehlgeschlagen (${response.status})`);
		}
		const result = await response.json();
		renderLookupResult(result);
		setStatus(result.found ? 'Produkt gefunden.' : 'Produkt nicht gefunden. Du kannst es manuell anlegen.');
	} catch (error) {
		renderLookupResult({
			found: false,
			barcode: cleanedBarcode,
			name: '',
			brand: '',
			imageUrl: '',
			message: 'Lookup fehlgeschlagen. Bitte Produkt manuell anlegen.'
		});
		setStatus('Lookup fehlgeschlagen.');
	} finally {
		state.lookupInProgress = false;
		setControlsDisabled(state.scanning);
	}
}

function handleDetectedBarcode(barcode, sourceLabel) {
	const detectedValue = (barcode || '').trim();
	if (!detectedValue || detectedValue === state.lastDetectedBarcode) {
		return;
	}

	state.lastDetectedBarcode = detectedValue;
	barcodeInput.value = detectedValue;
	setStatus(`Barcode erkannt (${sourceLabel}): ${detectedValue}`);
	stopScan();
	performBarcodeLookup(detectedValue);
}

async function captureVideoFrame() {
	if (!state.stream || !video.videoWidth || !video.videoHeight) {
		return null;
	}

	frameCanvas.width = video.videoWidth;
	frameCanvas.height = video.videoHeight;
	const context = frameCanvas.getContext('2d', { willReadFrequently: true });
	context.drawImage(video, 0, 0, frameCanvas.width, frameCanvas.height);

	return new Promise(resolve => {
		frameCanvas.toBlob(resolve, 'image/jpeg', 0.92);
	});
}

async function decodeCurrentFrame(manualTrigger = false) {
	if (!state.stream || state.frameDecodeInProgress || state.lookupInProgress) {
		return;
	}

	state.frameDecodeInProgress = true;
	state.lastFrameDecodeAt = Date.now();
	setControlsDisabled(state.scanning);
	if (manualTrigger) {
		setStatus('Aktuelles Kamerabild wird geprüft ...');
	}

	try {
		const imageBlob = await captureVideoFrame();
		if (!imageBlob) {
			if (manualTrigger) {
				setStatus('Kamerabild ist noch nicht bereit. Bitte kurz warten und erneut versuchen.');
			}
			return;
		}

		const formData = new FormData();
		formData.append('image', imageBlob, 'camera-frame.jpg');

		const response = await fetch('/api/barcodes/decode', {
			method: 'POST',
			body: formData
		});
		if (!response.ok) {
			throw new Error(`Decode fehlgeschlagen (${response.status})`);
		}

		const result = await response.json();
		if (result.detected && result.barcode) {
			handleDetectedBarcode(result.barcode, 'Kamerabild');
			return;
		}

		if (manualTrigger) {
			setStatus(result.message || 'Im aktuellen Kamerabild wurde kein Barcode erkannt.');
		}
	} catch (error) {
		if (manualTrigger) {
			setStatus('Kamerabild konnte nicht ausgewertet werden. Bitte erneut versuchen oder Barcode manuell eingeben.');
		}
	} finally {
		state.frameDecodeInProgress = false;
		setControlsDisabled(state.scanning);
	}
}

async function detectLoop() {
	if (!state.scanning) {
		return;
	}

	try {
		if (state.detector) {
			const barcodes = await state.detector.detect(video);
			if (barcodes.length > 0) {
				handleDetectedBarcode(barcodes[0].rawValue, 'Live-Scanner');
				return;
			}
		}

		const elapsedMs = Date.now() - state.scanStartedAt;
		if (elapsedMs > 2200 && Date.now() - state.lastFrameDecodeAt > 1600) {
			decodeCurrentFrame(false);
		}

		if (!state.noDetectionHintShown && Date.now() - state.scanStartedAt > 4000) {
			state.noDetectionHintShown = true;
			setStatus('Noch nichts erkannt. Ich prüfe zusätzlich das Kamerabild. Halte den Barcode näher und möglichst gerade vor die Kamera, vermeide Spiegelungen und sorge für gutes Licht.');
		}
	} catch (error) {
		if (!state.noDetectionHintShown) {
			setStatus('Scanner aktiv. Suche nach Barcode ...');
		}
	}

	requestAnimationFrame(detectLoop);
}

async function startScan() {
	if (!navigator.mediaDevices?.getUserMedia) {
		setStatus('Dein Browser unterstützt keinen Kamerazugriff. Bitte Barcode manuell eingeben.');
		return;
	}

	try {
		const supportsBarcodeDetector = 'BarcodeDetector' in window;
		state.stream = await openCameraStream();
		video.srcObject = state.stream;
		await video.play();
		state.detector = supportsBarcodeDetector
			? new BarcodeDetector({ formats: ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39', 'qr_code'] })
			: null;
		state.scanning = true;
		state.lastDetectedBarcode = '';
		state.scanStartedAt = Date.now();
		state.noDetectionHintShown = false;
		state.frameDecodeInProgress = false;
		state.lookupInProgress = false;
		state.lastFrameDecodeAt = 0;
		resetLookupResult();
		setControlsDisabled(true);

		if (supportsBarcodeDetector) {
			setStatus('Scanner läuft. Live-Erkennung und zusätzliche Kamerabild-Prüfung sind aktiv. Halte den Barcode gut sichtbar vor die Kamera.');
			requestAnimationFrame(detectLoop);
			return;
		}

		setStatus('Kamera läuft. Dein Browser unterstützt keinen nativen Scanner, daher nutze ich die Bildanalyse im Hintergrund. Du kannst auch auf „Aktuelles Bild prüfen“ klicken.');
		requestAnimationFrame(detectLoop);
	} catch (error) {
		setControlsDisabled(false);
		setStatus('Kamera konnte nicht gestartet werden. Bitte Berechtigung prüfen oder Barcode manuell eingeben.');
	}
}

function stopScan() {
	state.scanning = false;
	state.detector = null;
	state.scanStartedAt = 0;
	state.noDetectionHintShown = false;
	state.frameDecodeInProgress = false;
	state.lastFrameDecodeAt = 0;
	if (state.stream) {
		state.stream.getTracks().forEach(track => track.stop());
		state.stream = null;
	}
	video.srcObject = null;
	setControlsDisabled(false);
}

lookupForm?.addEventListener('submit', event => {
	event.preventDefault();
	performBarcodeLookup(barcodeInput.value);
});

startButton?.addEventListener('click', startScan);
stopButton?.addEventListener('click', () => {
	stopScan();
	setStatus('Scanner gestoppt.');
});
analyzeFrameButton?.addEventListener('click', () => {
	decodeCurrentFrame(true);
});

window.addEventListener('beforeunload', stopScan);

setControlsDisabled(false);

