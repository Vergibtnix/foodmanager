(() => {
    'use strict';

    const form = document.getElementById('item-form');
    if (!form) return;
    form.classList.add('js');

    const $ = id => document.getElementById(id);
    const fire = el => el.dispatchEvent(new Event('input', { bubbles: true }));

    // ---------- Kategorie bestimmt, welche Zusatzfelder erscheinen ----------
    const FOOD = ['expiry'];
    const DURABLE = ['purchase', 'condition'];
    const ALL = ['expiry', 'purchase', 'condition'];
    const GROUPS = {
        'lebensmittel': FOOD,
        'getränk': FOOD,
        'medizin': FOOD,
        'werkzeug': DURABLE,
        'elektronik': DURABLE,
        'kleidung': ['purchase', 'condition']
    };

    const category = $('category');
    const conds = [...form.querySelectorAll('.cond')];
    const condHint = $('cond-hint');

    function updateConditional() {
        const cat = category.value.trim().toLowerCase();
        const known = Object.prototype.hasOwnProperty.call(GROUPS, cat);
        const active = known ? GROUPS[cat] : (cat ? ALL : []);
        let visible = 0;

        conds.forEach(el => {
            // Felder mit Inhalt oder Serverfehler bleiben immer sichtbar, damit nichts versteckt wird.
            const hasValue = [...el.querySelectorAll('input, select')].some(i => i.value.trim() !== '');
            const hasError = !!el.querySelector('.field-error');
            const show = active.includes(el.dataset.group) || hasValue || hasError;
            el.hidden = !show;
            if (show) visible++;
        });

        condHint.hidden = visible > 0;
    }

    // ---------- Chips für Kategorie und Lagerort ----------
    function setupPicker(picker) {
        const input = $(picker.dataset.input);
        const chips = [...picker.querySelectorAll('.chip[data-value]')];
        const otherChip = picker.querySelector('.chip[data-other]');
        const custom = picker.querySelector('.picker-custom');
        const error = picker.parentElement.querySelector('.js-error');
        let customOpen = false;

        function sync() {
            const value = input.value.trim();
            const preset = chips.find(c => c.dataset.value.toLowerCase() === value.toLowerCase());

            chips.forEach(c => {
                const on = c === preset;
                c.classList.toggle('active', on);
                c.setAttribute('aria-pressed', String(on));
            });

            const isCustom = value !== '' && !preset;
            otherChip.classList.toggle('active', isCustom || customOpen);
            custom.hidden = !(isCustom || customOpen);
            if (error && value) error.hidden = true;
        }

        chips.forEach(chip => chip.addEventListener('click', () => {
            input.value = chip.dataset.value;
            customOpen = false;
            sync();
            fire(input);
        }));

        otherChip.addEventListener('click', () => {
            customOpen = true;
            if (chips.some(c => c.classList.contains('active'))) input.value = '';
            sync();
            input.focus();
            fire(input);
        });

        input.addEventListener('input', sync);
        sync();
    }

    form.querySelectorAll('.picker').forEach(setupPicker);

    // Fehlermeldung verschwindet, sobald ein Lagerort gewählt ist
    form.querySelectorAll('.fixed-choice').forEach(group => group.addEventListener('change', () => {
        const error = group.parentElement.querySelector('.js-error');
        if (error) error.hidden = true;
    }));

    category.addEventListener('input', updateConditional);
    conds.forEach(el => el.addEventListener('input', updateConditional));
    updateConditional();

    // ---------- Menge: Standardwert 1 und Plus/Minus ----------
    const quantity = $('quantity');
    if (quantity && quantity.value === '') quantity.value = '1';

    // Schrittweite je Einheit: 1 Stück, 100 g, 100 ml
    const unit = $('unit');
    const unitStep = () => Number(unit?.selectedOptions[0]?.dataset.step || 1);

    form.querySelectorAll('button[data-step]').forEach(btn => btn.addEventListener('click', () => {
        const target = $(btn.dataset.target);
        const min = Number(target.min || 0);
        const current = parseInt(target.value, 10) || 0;
        target.value = String(Math.max(min, current + Number(btn.dataset.step) * unitStep()));
    }));

    // ---------- Ablaufdatum: Schnellauswahl ----------
    const expiry = $('expiryDate');

    form.querySelectorAll('[data-add]').forEach(btn => btn.addEventListener('click', () => {
        const [amount, unit] = btn.dataset.add.split(':');
        const d = new Date();
        if (unit === 'd') d.setDate(d.getDate() + Number(amount));
        if (unit === 'm') d.setMonth(d.getMonth() + Number(amount));
        if (unit === 'y') d.setFullYear(d.getFullYear() + Number(amount));

        const pad = n => String(n).padStart(2, '0');
        expiry.value = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
        fire(expiry);
    }));

    // ---------- Bild: Vorschau und Verkleinern vor dem Hochladen ----------
    const MAX_SIDE = 1600;
    const imageInput = $('image');
    const previewBox = form.querySelector('.image-preview');
    let previewUrl = null;

    async function shrink(file) {
        try {
            const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
            const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
            if (scale === 1 && file.size < 1.5 * 1024 * 1024) {
                if (bitmap.close) bitmap.close();
                return file;
            }
            const c = document.createElement('canvas');
            c.width = Math.round(bitmap.width * scale);
            c.height = Math.round(bitmap.height * scale);
            const ctx = c.getContext('2d');
            ctx.fillStyle = '#fff'; // PNG-Transparenz wird sonst schwarz
            ctx.fillRect(0, 0, c.width, c.height);
            ctx.drawImage(bitmap, 0, 0, c.width, c.height);
            if (bitmap.close) bitmap.close();
            const blob = await new Promise(resolve => c.toBlob(resolve, 'image/jpeg', 0.88));
            if (!blob) return file;
            return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' });
        } catch (e) {
            return file;
        }
    }

    function showPreview(file) {
        if (!previewBox) return;
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = URL.createObjectURL(file);

        let img = previewBox.querySelector('img');
        if (!img) {
            previewBox.textContent = '';
            img = document.createElement('img');
            img.alt = 'Vorschau';
            previewBox.appendChild(img);
        }
        img.src = previewUrl;
    }

    if (imageInput) {
        imageInput.addEventListener('change', async () => {
            const original = imageInput.files && imageInput.files[0];
            if (!original) return;

            const file = await shrink(original);
            if (file !== original) {
                try {
                    const transfer = new DataTransfer();
                    transfer.items.add(file);
                    imageInput.files = transfer.files;
                } catch (e) {
                    // Browser erlaubt das nicht: Original bleibt ausgewählt
                }
            }
            showPreview(file);
        });
    }

    // ---------- Absenden: Pflichtangaben prüfen, Doppelklick verhindern ----------
    form.addEventListener('submit', event => {
        const nameInput = $('name');
        let firstInvalid = null;

        if (nameInput && nameInput.value.trim() === '') {
            firstInvalid = nameInput;
            nameInput.focus();
        }

        form.querySelectorAll('.picker').forEach(picker => {
            const empty = $(picker.dataset.input).value.trim() === '';
            const error = picker.parentElement.querySelector('.js-error');
            if (error) error.hidden = !empty;
            if (empty && !firstInvalid) firstInvalid = picker;
        });

        // Feste Auswahl (Lagerort): eine Option muss angeklickt sein
        form.querySelectorAll('.fixed-choice').forEach(group => {
            const empty = !group.querySelector('input:checked');
            const error = group.parentElement.querySelector('.js-error');
            if (error) error.hidden = !empty;
            if (empty && !firstInvalid) firstInvalid = group;
        });

        if (firstInvalid) {
            event.preventDefault();
            firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
            return;
        }

        const submit = form.querySelector('button[type="submit"]');
        if (submit) submit.disabled = true;
    });
})();