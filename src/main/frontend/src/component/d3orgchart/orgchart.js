import { OrgChart } from 'd3-org-chart';
import { selection } from 'd3-selection';

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

        this.chart = new OrgChart()
            .nodeHeight((d) => 105)
            .nodeWidth((d) => 240)
            .childrenMargin((d) => 50)
            .compactMarginBetween((d) => 35)
            .compactMarginPair((d) => 30)
            .neighbourMargin((a, b) => 20)
            .nodeContent(function (d, i, arr, state) {
                return `
                    <div class="d3-chart-node" style="width:${d.with}px; height:${d.height}px;">
                        <div class="d3-chart-node-inner"
                            data-id="${d.data.id}"
                            style="width:${d.with}px; height:${d.height}px;">
                            
                            <div class="d3-chart-node-title">
                                ${d.data.orgLevelName}
                            </div>
                            <div class="d3-chart-node-content">
                                <div class="d3-chart-node-img-container">
                                    <img class="d3-chart-node-img" src="${d.data.image}" alt="img">
                                </div>
                                <div class="d3-chart-node-content-text">
                                    <div class="d3-chart-node-name">
                                        ${d.data.name}
                                    </div>
                                    <div class="d3-chart-node-position">
                                        ${d.data.position}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
            })
            .container('.d3-orgchart-container')
            .data([])
            .render();

        this.bindUI();
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
    }

    setData(json) {
        if (!this.chart) {
            return;
        }
        const nodes = json ? JSON.parse(json) : [];
        nodes.forEach((item) => this.chartData.set(String(item.id), item));

        this.chart.data(nodes).render().fit();
    }

}

customElements.define('d3-org-chart', D3OrgChart);