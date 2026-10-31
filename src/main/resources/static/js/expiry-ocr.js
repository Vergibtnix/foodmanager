(() => {
	'use strict';

	// Ablaufdatum von der Verpackung abfotografieren. Die Texterkennung (Tesseract.js) läuft komplett im
	// Browser, das Foto verlässt das Handy nicht. Die Bibliothek (~ 3 MB) wird erst beim ersten Klick geladen.
	// Das Ergebnis ist nur ein Vorschlag: aufgedruckte Daten sind oft schwer lesbar, also immer kurz prüfen.

	const row = document.querySelector('.ocr-row');
	const expiry = document.getElementById('expiryDate');
	const photo = document.getElementById('expiry-photo');
	if (!row || !expiry || !photo) return;

	const status = row.querySelector('.ocr-status');
	const choices = row.querySelector('.ocr-choices');
	const LIB = 'https://cdn.jsdelivr.net/npm/tesseract.js@5.1.1/dist/tesseract.min.js';
	const MAX_SIDE = 1600;

	row.hidden = false;

	let libPromise = null;
	function loadLib() {
		if (window.Tesseract) return Promise.resolve();
		if (!libPromise) {
			libPromise = new Promise((resolve, reject) => {
				const s = document.createElement('script');
				s.src = LIB;
				s.onload = resolve;
				s.onerror = () => {
					libPromise = null;
					reject(new Error('load'));
				};
				document.head.appendChild(s);
			});
		}
		return libPromise;
	}

	function setStatus(text) {
		status.textContent = text;
	}

	const pad = n => String(n).padStart(2, '0');
	const iso = d => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
	const german = d => pad(d.getDate()) + '.' + pad(d.getMonth() + 1) + '.' + d.getFullYear();

	// Foto verkleinern und in Graustufen mit mehr Kontrast umwandeln, das hilft der Erkennung
	async function prepare(file) {
		const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
		const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
		const c = document.createElement('canvas');
		c.width = Math.round(bitmap.width * scale);
		c.height = Math.round(bitmap.height * scale);
		const ctx = c.getContext('2d');
		ctx.filter = 'grayscale(1) contrast(1.6)';
		ctx.drawImage(bitmap, 0, 0, c.width, c.height);
		if (bitmap.close) bitmap.close();
		return c;
	}

	function validDate(y, m, d) {
		const date = new Date(y, m - 1, d);
		return date.getFullYear() === y && date.getMonth() === m - 1 && date.getDate() === d ? date : null;
	}

	const fullYear = y => (y < 100 ? 2000 + y : y);

	// Findet Daten wie 12.11.26, 12.11.2026, 12/11/2026, 12-11-26 und Monat/Jahr wie 11.2026 oder 11/26
	function findDates(text) {
		const today = new Date();
		today.setHours(0, 0, 0, 0);
		const min = new Date(today.getFullYear() - 1, today.getMonth(), today.getDate());
		const max = new Date(today.getFullYear() + 10, 11, 31);
		const found = new Map();

		const add = date => {
			if (date && date >= min && date <= max) found.set(iso(date), date);
		};

		const cleaned = text.replace(/[oO]/g, '0').replace(/[lI|]/g, '1').replace(/\s*([./-])\s*/g, '$1');

		const dayPattern = /(\d{1,2})[./-](\d{1,2})[./-](\d{4}|\d{2})(?!\d)/g;
		let m;
		while ((m = dayPattern.exec(cleaned)) !== null) {
			add(validDate(fullYear(Number(m[3])), Number(m[2]), Number(m[1])));
		}

		// Nur Monat und Jahr (MHD bei länger haltbaren Produkten): letzter Tag des Monats
		const withoutDays = cleaned.replace(dayPattern, ' ');
		const monthPattern = /(?<![\d./-])(\d{1,2})[./-](\d{4}|\d{2})(?![\d./-])/g;
		while ((m = monthPattern.exec(withoutDays)) !== null) {
			const month = Number(m[1]);
			if (month >= 1 && month <= 12) {
				add(new Date(fullYear(Number(m[2])), month, 0));
			}
		}

		return [...found.values()].sort((a, b) => a - b);
	}

	function apply(date) {
		expiry.value = iso(date);
		expiry.dispatchEvent(new Event('input', { bubbles: true }));
		setStatus('Übernommen: ' + german(date) + '. Bitte kurz prüfen.');
		choices.textContent = '';
	}

	function offerChoices(dates) {
		choices.textContent = '';
		dates.slice(0, 4).forEach(date => {
			const chip = document.createElement('button');
			chip.type = 'button';
			chip.className = 'chip small';
			chip.textContent = german(date);
			chip.addEventListener('click', () => apply(date));
			choices.appendChild(chip);
		});
	}

	photo.addEventListener('change', async () => {
		const file = photo.files && photo.files[0];
		photo.value = '';
		if (!file) return;

		choices.textContent = '';
		setStatus('Texterkennung wird geladen …');
		let worker = null;
		try {
			await loadLib();
			const image = await prepare(file);
			setStatus('Datum wird gesucht …');

			worker = await window.Tesseract.createWorker('eng');
			await worker.setParameters({ tessedit_char_whitelist: '0123456789./-: ' });
			const result = await worker.recognize(image);
			const dates = findDates(result.data.text || '');

			if (dates.length === 0) {
				setStatus('Kein Datum erkannt. Foto näher und gerade aufnehmen oder das Datum eintippen.');
			} else if (dates.length === 1) {
				apply(dates[0]);
			} else {
				setStatus('Mehrere Daten gefunden, bitte das richtige antippen:');
				offerChoices(dates);
			}
		} catch (e) {
			setStatus('Texterkennung nicht verfügbar (Internet nötig). Bitte das Datum eintippen.');
		} finally {
			if (worker) worker.terminate();
		}
	});
})();
