// Lays out the pandoc HTML of each Roomify document with Paged.js and prints PDFs.
// usage: node render.mjs <submission-dir> <cache-dir> <out-dir> [--preview]
import { chromium } from "playwright";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const [submission, cache, out] = process.argv.slice(2).map(p => path.resolve(p));
const preview = process.argv.includes("--preview");
const here = path.dirname(new URL(import.meta.url).pathname);
const config = JSON.parse(readFileSync(path.join(here, "documents.json"), "utf8"));
const url = p => pathToFileURL(p).href;
const esc = s => s.replace(/&/g, "&amp;").replace(/</g, "&lt;");
const footer = `${config.release} · ${config.date}`;
const css = readFileSync(path.join(here, "print.css"), "utf8").replace("__FOOTER__", footer.replace(/"/g, '\\"'));

const font = (family, file, weight, style) =>
  `@font-face { font-family: "${family}"; src: url("${url(path.join(cache, "fonts", file))}"); font-weight: ${weight}; font-style: ${style}; }`;
const fonts = [
  font("Source Serif 4", "SourceSerif4-Regular.ttf", 400, "normal"), font("Source Serif 4", "SourceSerif4-It.ttf", 400, "italic"),
  font("Source Serif 4", "SourceSerif4-Bold.ttf", 700, "normal"), font("Source Serif 4", "SourceSerif4-BoldIt.ttf", 700, "italic"),
  font("Source Serif 4", "SourceSerif4-Semibold.ttf", 600, "normal"), font("Source Code Pro", "SourceCodePro-Regular.ttf", 400, "normal"),
].join("\n");

const cover = (title, subtitle) => `
<section class="cover">
  <div><div class="brand">Roomify</div><hr class="rule"><h1>${esc(title)}</h1><div class="subtitle">${esc(subtitle)}</div></div>
  <div class="meta"><strong>${esc(config.team)}</strong><br>${esc(config.release)}<br>${esc(config.date)}<br>
    github.com/Bootcamp2Roomify/roomify_frontend · roomify_backend · roomify_cv</div>
</section>`;

const page = (title, body) => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<title>Roomify — ${esc(title)}</title><base href="${url(submission)}/">
<style>${fonts}\n${css}</style>
<script>window.PagedConfig = { auto: false };</script>
<script src="${url(path.join(here, "node_modules/pagedjs/dist/paged.polyfill.js"))}"></script>
</head><body>${body}</body></html>`;

const chapter = (doc, html) => `<section class="chapter" id="doc-${doc.id}"><h1 class="chapter-title">${esc(doc.title)}</h1>${html}</section>`;
const fragment = (doc, kind) => readFileSync(path.join(cache, "html", `${doc.id}.${kind}.html`), "utf8");

function contents(docs) {
  const items = docs.map(doc => {
    const sections = [...fragment(doc, "pack").matchAll(/<h2\s+id="([^"]+)"[^>]*>(.*?)<\/h2>/gs)]
      .map(([, id, text]) => `<li class="toc-section"><a href="#${id}"><span>${text.replace(/<[^>]+>/g, "")}</span><span class="dots"></span></a></li>`);
    return `<li class="toc-chapter"><a href="#doc-${doc.id}"><span>${esc(doc.title)}</span><span class="dots"></span></a><ol>${sections.join("")}</ol></li>`;
  });
  return `<nav class="toc"><h1 class="chapter-title">Contents</h1><ol>${items.join("")}</ol></nav>`;
}

const browser = await chromium.launch();
async function print(name, title, body) {
  const file = path.join(cache, "html", `${name}.page.html`);
  writeFileSync(file, page(title, body));
  const tab = await browser.newPage();
  await tab.goto(url(file));
  await tab.evaluate(async () => { await document.fonts.ready; await window.PagedPolyfill.preview(); });
  const pages = await tab.locator(".pagedjs_page").count();
  await tab.pdf({ path: path.join(out, `${name}.pdf`), preferCSSPageSize: true, printBackground: true });
  if (preview) {
    mkdirSync(path.join(cache, "preview", name), { recursive: true });
    for (let i = 0; i < pages; i++) {
      await tab.locator(".pagedjs_page").nth(i).screenshot({ path: path.join(cache, "preview", name, `page-${String(i + 1).padStart(2, "0")}.png`) });
    }
  }
  await tab.close();
  console.log(`${name}.pdf  ${pages} pages`);
}

for (const doc of config.documents) {
  await print(doc.file, doc.title, cover(doc.title, doc.subtitle) + chapter(doc, fragment(doc, "single")));
}
await print(config.pack.file, config.pack.title,
  cover(config.pack.title, config.pack.subtitle) + contents(config.documents) +
  config.documents.map(doc => chapter(doc, fragment(doc, "pack"))).join(""));
await browser.close();
