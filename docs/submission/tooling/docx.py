"""DOCX tooling for the Roomify submission documents (Python standard library only).

  docx.py reference <pandoc-default.docx> <out.docx>
      Writes the reference document: Source Serif 4 typography, heading hierarchy, table, caption,
      block-quote, code and cover styles.
  docx.py finish <in.docx> <out.docx> <document title> <footer note> <fonts-dir>
      A4 page set-up, running header and footer (none on the cover), embedded fonts, document properties.
"""
import re
import sys
import uuid
import zipfile

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
SERIF, MONO = "Source Serif 4", "Source Code Pro"
TEXT_WIDTH = 9412  # twips: A4 (11906) minus 2.2 cm margins on both sides
FIXED_TIME = (2026, 10, 24, 0, 0, 0)  # reproducible archive timestamps
BUILT_IN = {"heading 1", "heading 2", "heading 3", "heading 4", "header", "footer", "body text", "block text"}


def fonts(face, size=None, extra=""):
    size_xml = f'<w:sz w:val="{size}"/><w:szCs w:val="{size}"/>' if size else ""
    return f'<w:rFonts w:ascii="{face}" w:hAnsi="{face}" w:cs="{face}" w:eastAsia="{face}"/>{extra}{size_xml}'


def para_style(style_id, name, ppr="", rpr="", based="Normal", next_style=None, kind="paragraph"):
    nxt = f'<w:next w:val="{next_style}"/>' if next_style else ""
    # Word's built-in styles (headings, header, footer, body text) must not be marked custom.
    custom = "" if name.lower() in BUILT_IN else ' w:customStyle="1"'
    return (f'<w:style w:type="{kind}"{custom} w:styleId="{style_id}"><w:name w:val="{name}"/>'
            f'<w:basedOn w:val="{based}"/>{nxt}<w:qFormat/><w:pPr>{ppr}</w:pPr><w:rPr>{rpr}</w:rPr></w:style>')


def heading(level, size, before, after, italic=False):
    return para_style(f"Heading{level}", f"heading {level}",
                      f'<w:keepNext/><w:keepLines/><w:spacing w:before="{before}" w:after="{after}"/><w:outlineLvl w:val="{level - 1}"/>',
                      fonts(SERIF, size, '<w:b/><w:bCs/>' + ('<w:i/>' if italic else '') + '<w:color w:val="111111"/>'),
                      based="Normal", next_style="BodyText")


STYLES = {
    "Normal": '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>',
    "BodyText": para_style("BodyText", "Body Text", '<w:spacing w:before="0" w:after="120"/>'),
    "FirstParagraph": para_style("FirstParagraph", "First Paragraph", '', based="BodyText", next_style="BodyText"),
    "Compact": para_style("Compact", "Compact", '<w:spacing w:before="20" w:after="40"/>', based="BodyText"),
    "Heading1": heading(1, 28, 360, 120),
    "Heading2": heading(2, 23, 260, 80),
    "Heading3": heading(3, 20, 200, 60),
    "Heading4": heading(4, 20, 160, 40, italic=True),
    "BlockText": para_style("BlockText", "Block Text",
                            '<w:pBdr><w:left w:val="single" w:sz="12" w:space="8" w:color="8A8A8A"/></w:pBdr>'
                            '<w:shd w:val="clear" w:color="auto" w:fill="F3F3F1"/><w:spacing w:before="120" w:after="120"/><w:ind w:left="227" w:right="113"/>',
                            based="BodyText"),
    "SourceCode": para_style("SourceCode", "Source Code", '<w:shd w:val="clear" w:color="auto" w:fill="F3F3F1"/><w:spacing w:after="120" w:line="240" w:lineRule="auto"/>',
                             fonts(MONO, 16)),
    "VerbatimChar": (f'<w:style w:type="character" w:customStyle="1" w:styleId="VerbatimChar"><w:name w:val="Verbatim Char"/>'
                     f'<w:rPr>{fonts(MONO, 17)}<w:shd w:val="clear" w:color="auto" w:fill="F3F3F1"/></w:rPr></w:style>'),
    "Hyperlink": ('<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/>'
                  '<w:rPr><w:color w:val="1F3A6E"/></w:rPr></w:style>'),
    "Figure": para_style("Figure", "Figure", '<w:keepNext/><w:jc w:val="center"/><w:spacing w:before="120" w:after="60"/>'),
    "CaptionedFigure": para_style("CaptionedFigure", "Captioned Figure", '<w:keepNext/><w:jc w:val="center"/><w:spacing w:before="120" w:after="60"/>', based="Figure"),
    "ImageCaption": para_style("ImageCaption", "Image Caption", '<w:spacing w:before="0" w:after="240"/>',
                               fonts(SERIF, 17, '<w:i/><w:iCs/><w:color w:val="555555"/>')),
    "TableCaption": para_style("TableCaption", "Table Caption", '<w:keepNext/><w:spacing w:before="120" w:after="60"/>',
                               fonts(SERIF, 17, '<w:i/><w:color w:val="555555"/>')),
    "Table": ('<w:style w:type="table" w:default="1" w:styleId="Table"><w:name w:val="Table"/><w:uiPriority w:val="99"/>'
              '<w:pPr><w:spacing w:before="40" w:after="40" w:line="252" w:lineRule="auto"/></w:pPr>'
              f'<w:rPr>{fonts(SERIF, 17)}</w:rPr>'
              '<w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:tblBorders>'
              '<w:top w:val="single" w:sz="8" w:space="0" w:color="1A1A1A"/><w:bottom w:val="single" w:sz="8" w:space="0" w:color="1A1A1A"/>'
              '<w:insideH w:val="single" w:sz="4" w:space="0" w:color="C9C9C9"/></w:tblBorders>'
              '<w:tblCellMar><w:top w:w="57" w:type="dxa"/><w:left w:w="85" w:type="dxa"/><w:bottom w:w="57" w:type="dxa"/><w:right w:w="85" w:type="dxa"/></w:tblCellMar></w:tblPr>'
              '<w:tblStylePr w:type="firstRow"><w:rPr><w:b/><w:bCs/></w:rPr><w:tcPr>'
              '<w:tcBorders><w:bottom w:val="single" w:sz="6" w:space="0" w:color="1A1A1A"/></w:tcBorders>'
              '<w:shd w:val="clear" w:color="auto" w:fill="F3F3F1"/></w:tcPr></w:tblStylePr></w:style>'),
    # A 34 mm rule under the brand line, as in the PDF cover (border width follows the right indent).
    "CoverBrand": para_style("CoverBrand", "Cover Brand",
                             f'<w:pBdr><w:bottom w:val="single" w:sz="12" w:space="14" w:color="1A1A1A"/></w:pBdr>'
                             f'<w:ind w:right="{TEXT_WIDTH - 1928}"/><w:spacing w:before="2800" w:after="360"/>',
                             fonts(SERIF, 26, '<w:b/><w:bCs/>')),
    "CoverTitle": para_style("CoverTitle", "Cover Title",
                             '<w:spacing w:before="120" w:after="200" w:line="240" w:lineRule="auto"/>',
                             fonts(SERIF, 64, '<w:b/><w:bCs/>')),
    "CoverSubtitle": para_style("CoverSubtitle", "Cover Subtitle", '<w:spacing w:after="0"/><w:ind w:right="2000"/>',
                                fonts(SERIF, 25, '<w:color w:val="555555"/>')),
    "CoverMeta": para_style("CoverMeta", "Cover Meta",
                            '<w:pBdr><w:top w:val="single" w:sz="4" w:space="10" w:color="C9C9C9"/></w:pBdr><w:spacing w:before="5600" w:after="0" w:line="320" w:lineRule="auto"/>',
                            fonts(SERIF, 19, '<w:color w:val="555555"/>')),
    "Header": para_style("Header", "header", f'<w:tabs><w:tab w:val="right" w:pos="{TEXT_WIDTH}"/></w:tabs><w:spacing w:after="0"/>',
                         fonts(SERIF, 17, '<w:color w:val="555555"/>')),
    "Footer": para_style("Footer", "footer", f'<w:tabs><w:tab w:val="right" w:pos="{TEXT_WIDTH}"/></w:tabs><w:spacing w:after="0"/>',
                         fonts(SERIF, 16, '<w:color w:val="777777"/>')),
}

DOC_DEFAULTS = ('<w:docDefaults><w:rPrDefault><w:rPr>' + fonts(SERIF, 20, '<w:color w:val="1A1A1A"/>') +
                '<w:lang w:val="en-GB" w:eastAsia="en-GB" w:bidi="ar-SA"/></w:rPr></w:rPrDefault>'
                '<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="288" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>')


def rewrite(src, dst, edits):
    """Copy a zip, transforming selected members and adding new ones, with fixed timestamps."""
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        names = zin.namelist()
        for name in names + [n for n in edits if n not in names]:
            data = zin.read(name) if name in names else b""
            change = edits.get(name)
            if change is not None:
                data = change(data)
            zout.writestr(zipfile.ZipInfo(name, FIXED_TIME), data, zipfile.ZIP_DEFLATED)


def reference(default_docx, out):
    def styles(data):
        xml = data.decode()
        xml = re.sub(r"<w:docDefaults>.*?</w:docDefaults>", DOC_DEFAULTS, xml, flags=re.S)
        for style_id, definition in STYLES.items():
            pattern = rf'<w:style [^>]*w:styleId="{style_id}"[^>]*>.*?</w:style>'
            if re.search(pattern, xml, flags=re.S):
                xml = re.sub(pattern, lambda _: definition, xml, count=1, flags=re.S)
            else:
                xml = xml.replace("</w:styles>", definition + "</w:styles>")
        return xml.encode()
    rewrite(default_docx, out, {"word/styles.xml": styles})


def obfuscate(font_bytes, key):
    """ECMA-376 font obfuscation: XOR the first 32 bytes with the reversed GUID bytes."""
    guid = uuid.UUID(key).bytes[::-1]
    head = bytes(b ^ guid[i % 16] for i, b in enumerate(font_bytes[:32]))
    return head + font_bytes[32:]


def finish(src, dst, title, footer, fonts_dir):
    esc = lambda s: s.replace("&", "&amp;").replace("<", "&lt;")
    header_xml = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:hdr xmlns:w="{W}" xmlns:r="{R}"><w:p><w:pPr><w:pStyle w:val="Header"/>'
                  '<w:pBdr><w:bottom w:val="single" w:sz="4" w:space="4" w:color="BDBDBD"/></w:pBdr></w:pPr>'
                  f'<w:r><w:rPr><w:b/></w:rPr><w:t>Roomify</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>{esc(title)}</w:t></w:r></w:p></w:hdr>')
    field = lambda instr: f'<w:fldSimple w:instr=" {instr} "><w:r><w:t>1</w:t></w:r></w:fldSimple>'
    footer_xml = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:ftr xmlns:w="{W}" xmlns:r="{R}"><w:p><w:pPr><w:pStyle w:val="Footer"/></w:pPr>'
                  f'<w:r><w:t xml:space="preserve">{esc(footer)}</w:t></w:r><w:r><w:tab/></w:r>'
                  f'<w:r><w:t xml:space="preserve">Page </w:t></w:r>{field("PAGE")}<w:r><w:t xml:space="preserve"> of </w:t></w:r>{field("NUMPAGES")}</w:p></w:ftr>')
    empty = lambda tag: f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:{tag} xmlns:w="{W}"><w:p/></w:{tag}>'
    sect = ('<w:sectPr><w:headerReference w:type="default" r:id="rIdRoomifyH1"/><w:headerReference w:type="first" r:id="rIdRoomifyH2"/>'
            '<w:footerReference w:type="default" r:id="rIdRoomifyF1"/><w:footerReference w:type="first" r:id="rIdRoomifyF2"/>'
            '<w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1418" w:right="1247" w:bottom="1304" w:left="1247" w:header="709" w:footer="624" w:gutter="0"/>'
            '<w:titlePg/></w:sectPr>')

    embedded = [  # (family, slot, file)
        (SERIF, "embedRegular", "SourceSerif4-Regular.ttf"), (SERIF, "embedBold", "SourceSerif4-Bold.ttf"),
        (SERIF, "embedItalic", "SourceSerif4-It.ttf"), (SERIF, "embedBoldItalic", "SourceSerif4-BoldIt.ttf"),
        (MONO, "embedRegular", "SourceCodePro-Regular.ttf"),
    ]
    font_parts, font_rels, entries = {}, [], {}
    for i, (family, slot, file) in enumerate(embedded, start=1):
        key = "{" + str(uuid.uuid5(uuid.NAMESPACE_URL, f"roomify:{file}")).upper() + "}"
        data = open(f"{fonts_dir}/{file}", "rb").read()
        font_parts[f"word/fonts/font{i}.odttf"] = (lambda d, k: lambda _: obfuscate(d, k))(data, key)
        font_rels.append(f'<Relationship Id="rIdFont{i}" Type="{R}/font" Target="fonts/font{i}.odttf"/>')
        entries.setdefault(family, []).append(f'<w:{slot} r:id="rIdFont{i}" w:fontKey="{key}"/>')
    family_kind = {SERIF: "roman", MONO: "modern"}

    def font_table(data):
        xml = data.decode()
        for family, embeds in entries.items():
            xml = re.sub(rf'<w:font w:name="{re.escape(family)}">.*?</w:font>', "", xml, flags=re.S)
            xml = xml.replace("</w:fonts>", f'<w:font w:name="{family}"><w:charset w:val="00"/><w:family w:val="{family_kind[family]}"/>'
                                            f'<w:pitch w:val="{"fixed" if family == MONO else "variable"}"/>{"".join(embeds)}</w:font></w:fonts>')
        if 'xmlns:r=' not in xml.split(">", 2)[1]:
            xml = xml.replace("<w:fonts ", f'<w:fonts xmlns:r="{R}" ', 1)
        return xml.encode()

    def document(data):
        xml = re.sub(r"<w:sectPr\b.*?</w:sectPr>", "", data.decode(), flags=re.S)
        # Tables span the full text width, as in the PDF.
        xml = re.sub(r"<w:tblW [^>]*/>", '<w:tblW w:type="pct" w:w="5000"/>', xml)
        return xml.replace("</w:body>", sect + "</w:body>").encode()

    def document_rels(data):
        rels = (f'<Relationship Id="rIdRoomifyH1" Type="{R}/header" Target="header1.xml"/><Relationship Id="rIdRoomifyH2" Type="{R}/header" Target="header2.xml"/>'
                f'<Relationship Id="rIdRoomifyF1" Type="{R}/footer" Target="footer1.xml"/><Relationship Id="rIdRoomifyF2" Type="{R}/footer" Target="footer2.xml"/>')
        return data.decode().replace("</Relationships>", rels + "</Relationships>").encode()

    def content_types(data):
        xml = data.decode()
        ct = "application/vnd.openxmlformats-officedocument.wordprocessingml"
        add = ('<Default Extension="odttf" ContentType="application/vnd.openxmlformats-officedocument.obfuscatedFont"/>'
               f'<Override PartName="/word/header1.xml" ContentType="{ct}.header+xml"/><Override PartName="/word/header2.xml" ContentType="{ct}.header+xml"/>'
               f'<Override PartName="/word/footer1.xml" ContentType="{ct}.footer+xml"/><Override PartName="/word/footer2.xml" ContentType="{ct}.footer+xml"/>')
        return xml.replace("</Types>", add + "</Types>").encode()

    def settings(data):
        xml = data.decode()
        if "<w:embedTrueTypeFonts" not in xml:
            xml = re.sub(r"(<w:embedSystemFonts\s*/>)", r"<w:embedTrueTypeFonts/>\1", xml, count=1) \
                if "<w:embedSystemFonts" in xml else re.sub(r"(<w:zoom[^>]*/>)", r"\1<w:embedTrueTypeFonts/>", xml, count=1)
        return xml.encode()

    def core(data):
        xml = data.decode()
        xml = re.sub(r"<dc:title>.*?</dc:title>|<dc:title\s*/>", "", xml, flags=re.S)
        xml = re.sub(r"<dc:creator>.*?</dc:creator>|<dc:creator\s*/>", "", xml, flags=re.S)
        meta = f"<dc:title>Roomify — {esc(title)}</dc:title><dc:creator>Team Bootcamp2Roomify</dc:creator>"
        return re.sub(r"(<cp:coreProperties[^>]*>)", lambda m: m.group(1) + meta, xml, count=1).encode()

    edits = {
        "word/document.xml": document, "word/_rels/document.xml.rels": document_rels, "[Content_Types].xml": content_types,
        "word/settings.xml": settings, "word/fontTable.xml": font_table, "docProps/core.xml": core,
        "word/header1.xml": lambda _: header_xml.encode(), "word/header2.xml": lambda _: empty("hdr").encode(),
        "word/footer1.xml": lambda _: footer_xml.encode(), "word/footer2.xml": lambda _: empty("ftr").encode(),
        "word/_rels/fontTable.xml.rels": lambda _: ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                                                    f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">{"".join(font_rels)}</Relationships>').encode(),
        **font_parts,
    }
    rewrite(src, dst, edits)


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    {"reference": reference, "finish": finish}[command](*args)
