(() => {
    'use strict';

    const bar = document.querySelector('.navbar');
    if (!bar) return;

    const toggle = bar.querySelector('.nav-toggle');
    const dropdown = bar.querySelector('.nav-dropdown');
    const dropdownToggle = dropdown && dropdown.querySelector('.nav-dropdown-toggle');

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

    if (toggle) {
        toggle.addEventListener('click', () => setMenu(!bar.classList.contains('open')));
    }

    if (dropdownToggle) {
        dropdownToggle.addEventListener('click', () => setDropdown(!dropdown.classList.contains('open')));
    }

    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        const wasOpen = dropdown && dropdown.classList.contains('open');
        setDropdown(false);
        setMenu(false);
        if (wasOpen && dropdownToggle) dropdownToggle.focus();
    });

    document.addEventListener('click', event => {
        if (dropdown && !dropdown.contains(event.target)) setDropdown(false);
        if (!bar.contains(event.target)) setMenu(false);
    });
})();