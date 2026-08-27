import { OrgChart } from 'd3-org-chart';
import { selection } from 'd3-selection';

function escapeHtml(value) {
    if (value === null || value === undefined) {
        return '';
    }
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function initials(name) {
    return String(name || '')
        .split(/\s+/)
        .filter((part) => part.length > 0)
        .slice(0, 2)
        .map((part) => part.charAt(0).toUpperCase())
        .join('');
}

function avatar(data) {
    if (data.image) {
        return `<img class="d3-chart-node-img" src="${escapeHtml(data.image)}" alt="">`;
    }
    return `<div class="d3-chart-node-img d3-chart-node-initials">${escapeHtml(initials(data.name))}</div>`;
}

class D3OrgChart extends HTMLElement {

    connectedCallback() {
        this.style.display = 'block';
        this.style.width = '100%';
        this.chartData = new Map();

        this.innerHTML = `
            <div class="d3-orgchart-main-container">
                <div class="d3-orgchart-container"></div>
                <div class="chart-controls">
                    <button id="chart-btn-top" class="chart-control-btn" title="Top">
                        <i class="material-icons">keyboard_arrow_up</i>
                    </button>
                    <button id="chart-btn-left" class="chart-control-btn" title="Left">
                        <i class="material-icons">keyboard_arrow_left</i>
                    </button>
                    <button id="chart-btn-compress" class="chart-control-btn" title="Compress">
                        <i class="material-icons">compress</i>
                    </button>
                    <button id="chart-btn-decompress" class="chart-control-btn" title="Decompress">
                        <i class="material-icons">format_line_spacing</i>
                    </button>
                    <button id="chart-btn-zoomin" class="chart-control-btn" title="Zoom In">
                        <i class="material-icons">zoom_in</i>
                    </button>
                    <button id="chart-btn-zoomout" class="chart-control-btn" title="Zoom Out">
                        <i class="material-icons">zoom_out</i>
                    </button>
                </div>
            </div>
        `;

        this.chart = this.createChart([]);

        this.bindUI();
    }

    /**
     * Builds a fresh chart over the container. A new instance is created for every
     * dataset: reusing one and swapping its data makes d3-org-chart animate the
     * removed nodes towards parents that no longer exist, which throws
     * "translate(NaN,NaN)" for every exiting node.
     */
    createChart(nodes) {
        return new OrgChart()
            .nodeHeight((d) => 105)
            .nodeWidth((d) => 240)
            .childrenMargin((d) => 50)
            .compactMarginBetween((d) => 35)
            .compactMarginPair((d) => 30)
            .neighbourMargin((a, b) => 20)
            .nodeContent(function (d, i, arr, state) {
                const data = d.data || {};
                const departmentOnly = data.departmentNode ? ' d3-chart-node-department' : '';
                return `
                    <div class="d3-chart-node" style="width:${d.with}px; height:${d.height}px;">
                        <div class="d3-chart-node-inner${departmentOnly}"
                            data-id="${escapeHtml(data.id)}"
                            style="width:${d.with}px; height:${d.height}px;">

                            <div class="d3-chart-node-title">
                                ${escapeHtml(data.orgLevelName)}
                            </div>
                            <div class="d3-chart-node-content">
                                <div class="d3-chart-node-img-container">
                                    ${avatar(data)}
                                </div>
                                <div class="d3-chart-node-content-text">
                                    <div class="d3-chart-node-name">
                                        ${escapeHtml(data.name)}
                                    </div>
                                    <div class="d3-chart-node-position">
                                        ${escapeHtml(data.position)}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
            })
            .container('.d3-orgchart-container')
            .data(nodes)
            .render();
    }

    bindUI() {
        const root = this;

        this.querySelector('#chart-btn-top')
            .addEventListener('click', () => {
                root.chart.layout('top').render().fit();
            });

        this.querySelector('#chart-btn-left')
            .addEventListener('click', () => {
                root.chart.layout('left').render().fit();
            });

        this.querySelector('#chart-btn-compress')
            .addEventListener('click', () => {
                root.chart.compact(true).render().fit();
            });

        this.querySelector('#chart-btn-decompress')
            .addEventListener('click', () => {
                root.chart.compact(false).render().fit();
            });

        this.querySelector('#chart-btn-zoomin')
            .addEventListener('click', () => {
                root.chart.zoomIn();
            });

        this.querySelector('#chart-btn-zoomout')
            .addEventListener('click', () => {
                root.chart.zoomOut();
            });

        // delegated on the container, which outlives every chart re-render
        this.querySelector('.d3-orgchart-container')
            .addEventListener('click', (event) => {
                const nodeElement = event.target.closest('.d3-chart-node-inner');
                if (!nodeElement) {
                    return;
                }
                const nodeId = nodeElement.getAttribute('data-id');
                if (!nodeId) {
                    return;
                }
                root.dispatchEvent(new CustomEvent('node-click', {
                    detail: { nodeId: nodeId }
                }));
            });
    }

    setData(json) {
        const container = this.querySelector('.d3-orgchart-container');
        if (!container) {
            return;
        }
        const nodes = json ? JSON.parse(json) : [];
        this.chartData.clear();
        nodes.forEach((item) => this.chartData.set(String(item.id), item));

        // wipe the rendered svg but keep the container element itself, so the
        // delegated node-click listener bound in bindUI() stays attached
        container.innerHTML = '';
        this.chart = this.createChart(nodes);
        if (nodes.length > 0) {
            this.chart.fit();
        }
    }

}

customElements.define('d3-org-chart', D3OrgChart);
