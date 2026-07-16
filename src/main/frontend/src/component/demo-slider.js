/**
 * <demo-slider> — an integer slider web component used by the Jmix Slider field
 * (com.company.demo.component.Slider).
 *
 * Element properties written by the server side:
 *   value, min, max, step        — integer slider parameters
 *   label                        — field label (HasLabel)
 *   disabled, readonly, required — standard field states
 *   invalid, errorMessage        — validation state (HasValidationProperties)
 *
 * Client -> server: on change the component updates its `value` property and
 * dispatches a `value-changed` DOM event, which Vaadin Flow uses to synchronize
 * the `value` property of AbstractSinglePropertyField.
 */
class DemoSlider extends HTMLElement {

    // Vaadin Flow communicates some state (e.g. enabled) via DOM attributes
    // rather than element properties, so boolean states are observed both ways.
    static get observedAttributes() {
        return ['disabled', 'readonly', 'required', 'invalid'];
    }

    constructor() {
        super();

        this._value = null;
        this._min = 0;
        this._max = 100;
        this._step = 1;
        this._label = '';
        this._disabled = false;
        this._readonly = false;
        this._required = false;
        this._invalid = false;
        this._errorMessage = '';

        const shadow = this.attachShadow({mode: 'open'});
        shadow.innerHTML = `
            <style>
                :host {
                    display: inline-block;
                    width: 100%;
                    font-family: var(--lumo-font-family, sans-serif);
                }
                .label {
                    display: block;
                    color: var(--lumo-secondary-text-color, #555);
                    font-size: var(--lumo-font-size-s, 0.875rem);
                    font-weight: 500;
                    padding-bottom: 0.25em;
                }
                .label:empty {
                    display: none;
                }
                :host([required-shown]) .label::after {
                    content: '\\2022';
                    color: var(--lumo-required-field-indicator-color, var(--lumo-primary-text-color, #1676f3));
                    padding-left: 0.25em;
                }
                .row {
                    display: flex;
                    align-items: center;
                    gap: 0.75em;
                }
                input[type=range] {
                    flex: 1;
                    accent-color: var(--lumo-primary-color, #1676f3);
                    margin: 0;
                    min-width: 0;
                }
                .value-badge {
                    min-width: 3em;
                    text-align: center;
                    font-size: var(--lumo-font-size-s, 0.875rem);
                    color: var(--lumo-body-text-color, #222);
                    background-color: var(--lumo-contrast-10pct, rgba(0, 0, 0, 0.1));
                    border-radius: var(--lumo-border-radius-s, 4px);
                    padding: 0.125em 0.375em;
                }
                .error {
                    display: none;
                    color: var(--lumo-error-text-color, #d32f2f);
                    font-size: var(--lumo-font-size-xs, 0.8125rem);
                    padding-top: 0.25em;
                }
                :host([invalid-shown]) .error {
                    display: block;
                }
                :host([invalid-shown]) input[type=range] {
                    accent-color: var(--lumo-error-color, #d32f2f);
                }
                :host([disabled-shown]) {
                    opacity: 0.5;
                    pointer-events: none;
                }
            </style>
            <label class="label" part="label"></label>
            <div class="row">
                <input type="range" part="slider">
                <span class="value-badge" part="value"></span>
            </div>
            <div class="error" part="error-message"></div>
        `;

        this._labelElement = shadow.querySelector('.label');
        this._input = shadow.querySelector('input');
        this._badge = shadow.querySelector('.value-badge');
        this._errorElement = shadow.querySelector('.error');

        this._input.addEventListener('input', () => {
            if (this._readonly) {
                this._input.value = this._value === null ? this._min : this._value;
                return;
            }
            this._badge.textContent = this._input.value;
        });

        this._input.addEventListener('change', () => {
            if (this._readonly) {
                return;
            }
            this._value = parseInt(this._input.value, 10);
            this.dispatchEvent(new CustomEvent('value-changed', {detail: {value: this._value}}));
        });
    }

    attributeChangedCallback(name, oldValue, newValue) {
        this['_' + name] = newValue !== null;
        this._render();
    }

    connectedCallback() {
        ['value', 'min', 'max', 'step', 'label', 'disabled', 'readonly', 'required',
            'invalid', 'errorMessage'].forEach((prop) => this._upgradeProperty(prop));
        this._render();
    }

    /**
     * Re-applies a property that was set on the instance before the custom
     * element definition was upgraded (it would otherwise shadow the accessor).
     */
    _upgradeProperty(prop) {
        if (Object.prototype.hasOwnProperty.call(this, prop)) {
            const value = this[prop];
            delete this[prop];
            this[prop] = value;
        }
    }

    get value() {
        return this._value;
    }

    set value(value) {
        this._value = (value === null || value === undefined || value === '')
            ? null
            : parseInt(value, 10);
        this._render();
    }

    get min() {
        return this._min;
    }

    set min(min) {
        this._min = parseInt(min, 10) || 0;
        this._render();
    }

    get max() {
        return this._max;
    }

    set max(max) {
        this._max = parseInt(max, 10) || 0;
        this._render();
    }

    get step() {
        return this._step;
    }

    set step(step) {
        step = parseInt(step, 10);
        this._step = step > 0 ? step : 1;
        this._render();
    }

    get label() {
        return this._label;
    }

    set label(label) {
        this._label = label == null ? '' : String(label);
        this._render();
    }

    get disabled() {
        return this._disabled;
    }

    set disabled(disabled) {
        this._disabled = Boolean(disabled);
        this._render();
    }

    get readonly() {
        return this._readonly;
    }

    set readonly(readonly) {
        this._readonly = Boolean(readonly);
        this._render();
    }

    get required() {
        return this._required;
    }

    set required(required) {
        this._required = Boolean(required);
        this._render();
    }

    get invalid() {
        return this._invalid;
    }

    set invalid(invalid) {
        this._invalid = Boolean(invalid);
        this._render();
    }

    get errorMessage() {
        return this._errorMessage;
    }

    set errorMessage(errorMessage) {
        this._errorMessage = errorMessage == null ? '' : String(errorMessage);
        this._render();
    }

    _render() {
        if (!this._input) {
            return;
        }
        this._input.min = this._min;
        this._input.max = this._max;
        this._input.step = this._step;
        const shownValue = this._value === null ? this._min : this._value;
        this._input.value = shownValue;
        this._badge.textContent = String(shownValue);
        this._labelElement.textContent = this._label;
        this._errorElement.textContent = this._errorMessage;
        this._input.disabled = this._disabled;
        this.toggleAttribute('disabled-shown', this._disabled);
        this.toggleAttribute('required-shown', this._required);
        this.toggleAttribute('invalid-shown', this._invalid);
    }
}

customElements.define('demo-slider', DemoSlider);
