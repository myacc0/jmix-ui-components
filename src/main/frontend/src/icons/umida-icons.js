/*
 * Registers the "umida-icons" iconset from the SVG files in
 * src/main/resources/META-INF/resources/icons, so they can be used as
 * icon="umida-icons:<name>" in menu.xml, view descriptors and new Icon("umida-icons", "<name>").
 *
 * Icon name = file name without ".svg", lower-cased, every run of non-alphanumeric characters
 * replaced with "-": "home.svg" -> "home", "arrow-down.svg" -> "arrow-down", "add-chart.svg" -> "add-chart".
 *
 * Single-color icons are recolored to currentColor, so they follow the text color like Vaadin icons.
 * Multi-color icons (e.g. flags) keep their own colors.
 */
import { Iconset } from '@vaadin/icon/vaadin-iconset.js';

const ICONSET_NAME = 'umida-icons';
const ICON_SIZE = 24;
const COLOR_ATTRIBUTES = ['fill', 'stroke'];

const svgFiles = import.meta.glob('../../../resources/META-INF/resources/icons/*.svg', {
  query: '?raw',
  import: 'default',
  eager: true
});

function toIconName(path) {
  return path
    .substring(path.lastIndexOf('/') + 1)
    .replace(/\.svg$/i, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function isColor(value) {
  return value && value !== 'none' && value !== 'currentColor';
}

// Mask contents define shape (alpha), not the visible color, so they are excluded from recoloring.
function colorElements(svg) {
  return [svg, ...svg.querySelectorAll('*')].filter((element) => !element.closest('mask'));
}

function recolorSingleColorIcon(svg) {
  const elements = colorElements(svg);
  const colors = new Set();
  elements.forEach((element) =>
    COLOR_ATTRIBUTES.forEach((attribute) => {
      const value = element.getAttribute(attribute);
      if (isColor(value)) {
        colors.add(value.toLowerCase());
      }
    })
  );

  if (colors.size !== 1) {
    return;
  }

  elements.forEach((element) =>
    COLOR_ATTRIBUTES.forEach((attribute) => {
      if (isColor(element.getAttribute(attribute))) {
        element.setAttribute(attribute, 'currentColor');
      }
    })
  );
}

// Inner ids (masks, clip paths) are made unique per icon: the iconset looks icons up by [id],
// and the same ids are repeated in different SVG exports.
function prefixInnerIds(svg, iconName) {
  svg.querySelectorAll('[id]').forEach((element) => {
    const id = element.id;
    const newId = `${ICONSET_NAME}-${iconName}-${id}`;
    element.id = newId;
    svg.querySelectorAll('*').forEach((child) =>
      [...child.attributes].forEach((attribute) => {
        if (attribute.value === `#${id}`) {
          attribute.value = `#${newId}`;
        } else if (attribute.value.includes(`url(#${id})`)) {
          attribute.value = attribute.value.replaceAll(`url(#${id})`, `url(#${newId})`);
        }
      })
    );
  });
}

function createIconElement(iconName, source) {
  const svg = new DOMParser().parseFromString(source, 'image/svg+xml').documentElement;
  if (svg.nodeName !== 'svg') {
    console.warn(`[${ICONSET_NAME}] Cannot parse icon "${iconName}"`);
    return null;
  }

  recolorSingleColorIcon(svg);
  prefixInnerIds(svg, iconName);

  svg.setAttribute('id', `${ICONSET_NAME}:${iconName}`);
  svg.setAttribute('viewBox', svg.getAttribute('viewBox') || `0 0 ${ICON_SIZE} ${ICON_SIZE}`);
  // The size is controlled by <vaadin-icon>
  svg.removeAttribute('width');
  svg.removeAttribute('height');
  return svg;
}

const template = document.createElement('template');
Object.entries(svgFiles).forEach(([path, source]) => {
  const icon = createIconElement(toIconName(path), source);
  if (icon) {
    template.content.appendChild(document.importNode(icon, true));
  }
});

Iconset.register(ICONSET_NAME, ICON_SIZE, template);
