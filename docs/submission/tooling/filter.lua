-- Shared by the PDF (HTML) and DOCX builds of the Roomify submission documents.
-- ROOMIFY_TARGET: "html" (one document), "pack" (combined PDF) or "docx". ROOMIFY_DOC: document id.
local target = os.getenv("ROOMIFY_TARGET") or "html"
local config = pandoc.json.decode(io.open(os.getenv("ROOMIFY_CONFIG")):read("a"))
local this_id = os.getenv("ROOMIFY_DOC")
local repo = "https://github.com/Bootcamp2Roomify/roomify_backend/blob/develop/docs/submission/"
local by_source, this = {}, nil
for _, d in ipairs(config.documents) do
  by_source[d.source] = d
  if d.id == this_id then this = d end
end

local function urlencode(s) return (s:gsub(" ", "%%20")) end
local function exists(path) local f = io.open(path, "r"); if f then f:close() return true end return false end

-- Mentions of another document's source file read as that document's title.
local function retitle(text)
  return (text:gsub("(%u[%u_]*%.md)", function(f) return by_source[f] and by_source[f].title or f end))
end

function Str(el)
  local t = retitle(el.text)
  if t ~= el.text then return pandoc.Str(t) end
end

function Link(el)
  local t = el.target
  if t:match("^%a+:") or t:match("^#") then return el end
  local file, frag = t:match("^([^#]*)(.*)$")
  local d = by_source[file]
  if d then
    el.target = target == "pack" and ("#doc-" .. d.id) or (repo .. "final/" .. urlencode(d.file) .. ".pdf")
  else
    el.target = repo .. urlencode(file) .. frag
  end
  return el
end

local figures = 0
function Figure(fig)
  figures = figures + 1
  local first = fig.caption.long[1]
  if first then
    first.content:insert(1, pandoc.Str("Figure " .. figures .. ". "))
  end
  return fig
end

function Image(img)
  if target ~= "docx" then
    -- Diagrams are sharper as vector graphics in the PDF.
    local svg = img.src:gsub("%.png$", ".svg")
    if svg ~= img.src and exists(svg) then img.src = svg end
  else
    img.attributes.width = img.src:match("^architecture/") and "16cm" or "12cm"
  end
  return img
end

local function cover_docx()
  local function para(style, inlines) return pandoc.Div({pandoc.Para(inlines)}, {["custom-style"] = style}) end
  return {
    para("Cover Brand", {pandoc.Str("Roomify")}),
    para("Cover Title", {pandoc.Str(this.title)}),
    para("Cover Subtitle", {pandoc.Str(this.subtitle)}),
    para("Cover Meta", {pandoc.Str(config.team), pandoc.LineBreak(), pandoc.Str(config.release),
                        pandoc.LineBreak(), pandoc.Str(config.date)}),
    pandoc.RawBlock("openxml", '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'),
  }
end

function Pandoc(doc)
  -- The source's first heading is the document title; the build places it on the cover.
  if doc.blocks[1] and doc.blocks[1].t == "Header" and doc.blocks[1].level == 1 then
    doc.blocks:remove(1)
  end
  if target == "docx" then
    doc.blocks = doc.blocks:walk({ Header = function(h) h.level = math.max(1, h.level - 1); return h end })
    local blocks = pandoc.List(cover_docx())
    blocks:extend(doc.blocks)
    doc.blocks = blocks
  end
  return doc
end
