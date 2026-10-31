(() => {
    'use strict';

    const table = document.querySelector('.items-table');
    const controls = document.getElementById('list-controls');
    if (!table || !controls) return;

    const tbody = table.tBodies[0];
    const rows = Array.from(tbody.rows);
    const chips = Array.from(controls.querySelectorAll('[data-filter]'));
    const sortSelect = document.getElementById('list-sort');
    const viewButtons = Array.from(controls.querySelectorAll('[data-view]'));
    const noMatch = document.getElementById('no-match');
    const narrow = window.matchMedia('(max-width: 800px)');

    const FILTERS = ['all', 'expired', 'soon', 'low', 'defect'];
    const SORTS = ['default', 'name', 'expiry', 'qty'];

    // Speicher kann gesperrt sein (z. B. private Fenster), dann läuft alles ohne Merken weiter.
    function read(kind, key) {
        try { return window[kind].getItem(key); } catch (e) { return null; }
    }

    function write(kind, key, value) {
        try { window[kind].setItem(key, value); } catch (e) { /* egal */ }
    }

    // Mit JavaScript steuern wir die Ansicht selbst, die automatische Handy-Karten-Regel entfällt.
    table.classList.remove('cards-on-mobile');
    controls.hidden = false;

    // Filter und Sortierung merkt sich die Sitzung, damit sie nach einem Klick auf "−" oder "+" bleiben.
    let filter = read('sessionStorage', 'foodmanager.filter');
    // Link vom Dashboard (z. B. /items?filter=expired) hat Vorrang
    const fromUrl = new URLSearchParams(window.location.search).get('filter');
    if (FILTERS.includes(fromUrl)) {
        filter = fromUrl;
        write('sessionStorage', 'foodmanager.filter', filter);
    }
    if (!FILTERS.includes(filter)) filter = 'all';

    let sort = read('sessionStorage', 'foodmanager.sort');
    if (!SORTS.includes(sort)) sort = 'default';

    // Die Ansicht merkt sich das Gerät dauerhaft. Erstes Mal: Karten am Handy, Tabelle am großen Bildschirm.
    let view = read('localStorage', 'foodmanager.view');
    if (view !== 'table' && view !== 'cards') view = narrow.matches ? 'cards' : 'table';

    // ---------- Filter ----------
    function matches(row, name) {
        switch (name) {
            case 'expired': return row.dataset.status === 'expired';
            case 'soon': return row.dataset.status === 'soon';
            case 'low': return row.dataset.low === 'true';
            case 'defect': return row.dataset.defect === 'true';
            default: return true;
        }
    }

    chips.forEach(chip => {
        const name = chip.dataset.filter;
        const count = rows.filter(row => matches(row, name)).length;
        chip.querySelector('.count').textContent = '(' + count + ')';

        // Filter ohne Treffer ausblenden, "Alle" bleibt immer da
        if (name !== 'all' && count === 0) {
            chip.hidden = true;
            if (filter === name) filter = 'all';
        }

        chip.addEventListener('click', () => {
            filter = name;
            write('sessionStorage', 'foodmanager.filter', filter);
            apply();
        });
    });

    // ---------- Sortierung ----------
    const collator = new Intl.Collator('de', { sensitivity: 'base' });

    const comparators = {
        default: (a, b) => Number(a.dataset.index) - Number(b.dataset.index),
        name: (a, b) => collator.compare(a.dataset.name, b.dataset.name),
        expiry: (a, b) => {
            const x = a.dataset.expiry;
            const y = b.dataset.expiry;
            if (!x && !y) return 0;
            if (!x) return 1;   // ohne Datum ans Ende
            if (!y) return -1;
            return x < y ? -1 : x > y ? 1 : 0;
        },
        qty: (a, b) => Number(a.dataset.qty) - Number(b.dataset.qty)
    };

    if (sortSelect) {
        sortSelect.value = sort;
        sortSelect.addEventListener('change', () => {
            sort = sortSelect.value;
            write('sessionStorage', 'foodmanager.sort', sort);
            apply();
        });
    }

    // ---------- Ansicht ----------
    viewButtons.forEach(button => button.addEventListener('click', () => {
        view = button.dataset.view;
        write('localStorage', 'foodmanager.view', view);
        applyView();
    }));

    function applyView() {
        table.classList.toggle('as-cards', view === 'cards');
        viewButtons.forEach(button => {
            const on = button.dataset.view === view;
            button.classList.toggle('active', on);
            button.setAttribute('aria-pressed', String(on));
        });
    }

    // ---------- Anwenden ----------
    function apply() {
        chips.forEach(chip => {
            const on = chip.dataset.filter === filter;
            chip.classList.toggle('active', on);
            chip.setAttribute('aria-pressed', String(on));
        });

        let visible = 0;
        rows.slice().sort(comparators[sort]).forEach(row => {
            const show = matches(row, filter);
            row.hidden = !show;
            if (show) visible++;
            tbody.appendChild(row);
        });

        if (noMatch) noMatch.hidden = visible > 0;
    }

    applyView();
    apply();
})();
