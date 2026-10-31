(() => {
    'use strict';

    // Offline-Unterstützung. Der Browser erlaubt das nur mit HTTPS oder auf localhost,
    // sonst bleibt alles wie bisher.
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('/sw.js').catch(() => { /* nicht möglich, dann läuft die App ohne */ });
        });
    }

    const bar = document.querySelector('.navbar');
    if (!bar) return;

    const toggle = bar.querySelector('.nav-toggle');
    const dropdown = bar.querySelector('.nav-dropdown');
    const dropdownToggle = dropdown && dropdown.querySelector('.nav-dropdown-toggle');
    const info = bar.querySelector('.brand-info');

    function setMenu(open) {
        if (!toggle) return;
        bar.classList.toggle('open', open);
        toggle.setAttribute('aria-expanded', String(open));
        toggle.setAttribute('aria-label', open ? 'Menü schließen' : 'Menü öffnen');
    }

    function setDropdown(open) {
        if (!dropdown) return;
        dropdown.classList.toggle('open', open);
        dropdownToggle.setAttribute('aria-expanded', String(open));
    }

    function closeInfo() {
        if (info && info.open) info.open = false;
    }

    if (toggle) {
        toggle.addEventListener('click', () => {
            const open = !bar.classList.contains('open');
            setMenu(open);
            if (open) closeInfo();
        });
    }

    if (dropdownToggle) {
        dropdownToggle.addEventListener('click', () => {
            const open = !dropdown.classList.contains('open');
            setDropdown(open);
            if (open) closeInfo();
        });
    }

    // Öffnet sich das Info-Fenster, schließen sich Menü und Account-Menü, damit nichts übereinanderliegt.
    if (info) {
        info.addEventListener('toggle', () => {
            if (info.open) {
                setMenu(false);
                setDropdown(false);
            }
        });
    }

    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;

        const infoWasOpen = info && info.open;
        const dropdownWasOpen = dropdown && dropdown.classList.contains('open');

        closeInfo();
        setDropdown(false);
        setMenu(false);

        // Fokus zurück auf den Knopf, der das Fenster geöffnet hat
        if (infoWasOpen) info.querySelector('summary').focus();
        else if (dropdownWasOpen && dropdownToggle) dropdownToggle.focus();
    });

    document.addEventListener('click', event => {
        if (info && info.open && !info.contains(event.target)) closeInfo();
        if (dropdown && !dropdown.contains(event.target)) setDropdown(false);
        if (!bar.contains(event.target)) setMenu(false);
    });
})();