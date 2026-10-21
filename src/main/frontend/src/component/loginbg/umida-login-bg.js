/* UMIDA login background — mouse parallax (optional; the scene works without it).
   Writes --umida-px / --umida-py (-1..1) on .umida-stage. Disabled for "reduce motion" and data-motion="off".
   The scene markup is rendered by the server (LoginBackground.java) as light DOM of this element. */
class UmidaLoginBg extends HTMLElement {

    connectedCallback() {
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || this.getAttribute('data-motion') === 'off') {
            return;
        }

        this._tx = 0;
        this._ty = 0;
        this._cx = 0;
        this._cy = 0;
        this._running = false;

        this._onPointerMove = (e) => {
            this._tx = (e.clientX / window.innerWidth - 0.5) * 2;
            this._ty = (e.clientY / window.innerHeight - 0.5) * 2;
            if (!this._running) {
                this._running = true;
                requestAnimationFrame(() => this._tick());
            }
        };
        window.addEventListener('pointermove', this._onPointerMove, {passive: true});
    }

    disconnectedCallback() {
        if (this._onPointerMove) {
            window.removeEventListener('pointermove', this._onPointerMove);
            this._onPointerMove = null;
        }
    }

    _tick() {
        // the stage is server-rendered content, so it is looked up on every frame instead of being cached
        const stage = this.querySelector('.umida-stage');
        if (!stage || !this.isConnected) {
            this._running = false;
            return;
        }

        this._cx += (this._tx - this._cx) * 0.06;
        this._cy += (this._ty - this._cy) * 0.06;
        stage.style.setProperty('--umida-px', this._cx.toFixed(3));
        stage.style.setProperty('--umida-py', this._cy.toFixed(3));

        if (Math.abs(this._tx - this._cx) > 0.002 || Math.abs(this._ty - this._cy) > 0.002) {
            requestAnimationFrame(() => this._tick());
        } else {
            this._running = false;
        }
    }
}

if (!customElements.get('umida-login-bg')) {
    customElements.define('umida-login-bg', UmidaLoginBg);
}
