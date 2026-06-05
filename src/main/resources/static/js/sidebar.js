(function () {
    function initSidebar() {
        var body = document.body;
        var sidebar = document.getElementById('sidebar');
        var toggle = document.getElementById('sidebarToggle');

        if (!body || !sidebar || !toggle) {
            return;
        }

        var overlay = document.getElementById('sidebarOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'sidebarOverlay';
            overlay.className = 'sidebar-overlay';
            overlay.setAttribute('aria-hidden', 'true');
            body.appendChild(overlay);
        }

        function closeSidebar() {
            body.classList.remove('sidebar-open');
            toggle.setAttribute('aria-expanded', 'false');
        }

        function openSidebar() {
            body.classList.add('sidebar-open');
            toggle.setAttribute('aria-expanded', 'true');
        }

        function toggleSidebar() {
            if (body.classList.contains('sidebar-open')) {
                closeSidebar();
                return;
            }
            openSidebar();
        }

        toggle.addEventListener('click', function (event) {
            event.preventDefault();
            toggleSidebar();
        });

        overlay.addEventListener('click', closeSidebar);

        sidebar.querySelectorAll('[data-sidebar-close]').forEach(function (button) {
            button.addEventListener('click', function (event) {
                event.preventDefault();
                closeSidebar();
            });
        });

        sidebar.querySelectorAll('.nav a').forEach(function (link) {
            link.addEventListener('click', function (event) {
                var currentUrl = new URL(window.location.href, window.location.origin);
                var targetUrl = new URL(link.href, window.location.origin);
                var isCurrentPage = currentUrl.pathname === targetUrl.pathname
                        && currentUrl.search === targetUrl.search;

                if (isCurrentPage) {
                    event.preventDefault();
                }

                closeSidebar();
            });
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeSidebar();
            }
        });

        window.addEventListener('resize', function () {
            if (window.innerWidth > 1100) {
                closeSidebar();
            }
        });

        toggle.setAttribute('aria-expanded', body.classList.contains('sidebar-open') ? 'true' : 'false');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initSidebar, { once: true });
        return;
    }

    initSidebar();
})();
